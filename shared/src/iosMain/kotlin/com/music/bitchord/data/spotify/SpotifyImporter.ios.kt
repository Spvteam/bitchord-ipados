package com.music.bitchord.data.spotify

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.platform.PlatformLock
import com.music.bitchord.platform.PlatformUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SpotifyImportTrack(
    val title: String,
    val artist: String,
)

data class SpotifyImportResult(
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val totalTracks: Int,
    val resolvedVideoIds: List<String>,
    val unmatchedTracks: List<SpotifyImportTrack>,
)

/**
 * iOS implementation of [SpotifyImporter] using Ktor `Darwin` and `PlatformUrl`.
 */
object SpotifyImporter {

    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy { HttpClient(Darwin) }

    private val PLAYLIST_ID = Regex("""[A-Za-z0-9]{22}""")

    private fun isSpotifyHost(host: String) =
        host == "spotify.com" || host.endsWith(".spotify.com")

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("spotify:playlist:")) {
            val id = trimmed.removePrefix("spotify:playlist:").substringBefore("?").substringBefore("/")
            return id.takeIf { PLAYLIST_ID.matches(it) }
        }

        val scheme = PlatformUrl.extractScheme(trimmed)?.lowercase() ?: return null
        if (scheme !in setOf("http", "https")) return null
        val host = PlatformUrl.extractHost(trimmed)?.lowercase().orEmpty()
        if (!isSpotifyHost(host)) return null
        val path = PlatformUrl.extractPath(trimmed).orEmpty()
        val segments = path.split('/').filter { it.isNotEmpty() }
        val playlistIdx = segments.indexOf("playlist")
        if (playlistIdx == -1 || playlistIdx + 1 >= segments.size) return null
        return segments[playlistIdx + 1].takeIf { PLAYLIST_ID.matches(it) }
    }

    suspend fun fetchPlaylistTracks(playlistId: String): Pair<String, List<SpotifyImportTrack>> =
        withContext(Dispatchers.Default) {
            val url = "https://open.spotify.com/embed/playlist/$playlistId"
            val response = client.get(url) {
                header("User-Agent", USER_AGENT)
            }
            val html = response.bodyAsText()
            if (response.status.value !in 200..299 || html.isBlank()) {
                error("Failed to load Spotify playlist (HTTP ${response.status.value})")
            }

            val scriptRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val match = scriptRegex.find(html)
                ?: error("Could not parse Spotify playlist metadata. Make sure the playlist is public.")

            val jsonString = match.groupValues[1]
            val root = json.parseToJsonElement(jsonString).jsonObject

            val props = root["props"]?.jsonObject?.get("pageProps")?.jsonObject
            val stateData = props?.get("state")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("entity")?.jsonObject
                ?: props?.get("entity")?.jsonObject
                ?: error("Spotify playlist entity missing or private")

            val playlistTitle = stateData["name"]?.jsonPrimitive?.content
                ?: stateData["title"]?.jsonPrimitive?.content
                ?: "Imported Spotify Playlist"

            val trackListJson = stateData["trackList"]?.jsonArray
                ?: stateData["tracks"]?.jsonArray
                ?: JsonArray(emptyList())

            val tracks = mutableListOf<SpotifyImportTrack>()
            for (element in trackListJson) {
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content
                    ?: obj["name"]?.jsonPrimitive?.content
                    ?: continue
                val subtitle = obj["subtitle"]?.jsonPrimitive?.content
                    ?: obj["artists"]?.jsonArray?.joinToString(", ") {
                        it.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
                    }
                    ?: ""
                if (title.isNotBlank()) {
                    tracks.add(SpotifyImportTrack(title = title.trim(), artist = subtitle.trim()))
                }
            }

            if (tracks.isEmpty()) {
                error("No tracks found in public Spotify playlist.")
            }

            Pair(playlistTitle, tracks)
        }

    suspend fun matchTrack(track: SpotifyTrack): Song? {
        val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        val candidates = YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
            ?.filterIsInstance<SearchResult.Track>()
            ?.map { it.song }
            .orEmpty()
        return TrackMatcher.best(
            candidates,
            TrackMatcher.Target(
                title = track.title,
                artist = track.artist,
                durationSec = track.durationMs.takeIf { it > 0 }?.div(1000),
                album = track.album,
            ),
        ) ?: candidates.firstOrNull()
    }

    suspend fun resolveToSongs(
        tracks: List<SpotifyImportTrack>,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): Pair<List<Song>, List<SpotifyImportTrack>> = coroutineScope {
        val total = tracks.size
        val lock = PlatformLock()
        var completedCount = 0
        val semaphore = Semaphore(4)

        val deferredResults = tracks.map { track ->
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    val query = "${track.title} ${track.artist}".trim()
                    val searchResult = YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
                    val matchedSong = searchResult?.filterIsInstance<SearchResult.Track>()
                        ?.firstOrNull()?.song

                    val done = lock.withLock {
                        completedCount += 1
                        completedCount
                    }
                    onProgress(done, total)

                    if (matchedSong != null) {
                        Pair(matchedSong, null)
                    } else {
                        Pair(null, track)
                    }
                }
            }
        }

        val results = deferredResults.awaitAll()
        val songs = results.mapNotNull { it.first }
        val unmatched = results.mapNotNull { it.second }

        Pair(songs, unmatched)
    }
}
