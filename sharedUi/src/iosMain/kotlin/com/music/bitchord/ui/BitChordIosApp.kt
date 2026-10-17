@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.music.bitchord.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.IOSAudioEngine
import com.music.bitchord.IOSKeychainAuthStore
import com.music.bitchord.data.IOSSandboxStorage
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.innertube.IOSPoTokenProvider
import com.music.bitchord.data.innertube.InnerTubeXResolver
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsTranslation
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.playback.AudioEngineController
import com.music.bitchord.playback.DeckId
import com.music.bitchord.playback.IOSNowPlayingManager
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.player.AudioOutputDevice
import com.music.bitchord.ui.player.AudioOutputKind
import com.music.bitchord.ui.player.CanvasVideoSpec
import com.music.bitchord.ui.player.LyricsRomanizationResult
import com.music.bitchord.ui.player.LyricsShareRequest
import com.music.bitchord.ui.player.LyricsTranslationResult
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.OutputFormatUi
import com.music.bitchord.ui.player.PartyUi
import com.music.bitchord.ui.player.PlayerHost
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.player.PlayerSettingsSource
import com.music.bitchord.ui.player.RepeatModes
import com.music.bitchord.ui.player.SystemVolume
import com.music.bitchord.ui.screens.ExploreScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.SearchScreen
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.volume
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore

private const val YOUTUBE_SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fmusic.youtube.com%2F"

private const val SAFARI_IPAD_USER_AGENT =
    "Mozilla/5.0 (iPad; CPU OS 18_3 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1"

internal object IOSAppUiHost : AppUiHost {
    override val swipeToPlayNext: StateFlow<Boolean> = MutableStateFlow(true)
    private val _homeRecentsViewType = MutableStateFlow(LibraryViewType.LIST)
    override val homeRecentsViewType: StateFlow<LibraryViewType> = _homeRecentsViewType

    override fun setHomeRecentsViewType(value: LibraryViewType) {
        _homeRecentsViewType.value = value
    }

    override val pinnedPlaylists: StateFlow<List<String>> = MutableStateFlow(emptyList())
    private val _librarySort = MutableStateFlow(LibrarySort.DEFAULT)
    override val librarySort: StateFlow<LibrarySort> = _librarySort

    @Composable
    override fun downloadedIds(): Set<String> = emptySet()
}

internal object IOSPlayerSettings : PlayerSettingsSource {
    override val animatedCanvas: StateFlow<Boolean> = MutableStateFlow(true)
    override val audioQualityCellular: StateFlow<AudioQuality> = MutableStateFlow(AudioQuality.HIGH)
    override val audioQualityWifi: StateFlow<AudioQuality> = MutableStateFlow(AudioQuality.HIGH)
    override val canvasOverCellular: StateFlow<Boolean> = MutableStateFlow(true)
    override val fullBleedArtwork: StateFlow<Boolean> = MutableStateFlow(true)
    override val hideSongStatus: StateFlow<Boolean> = MutableStateFlow(false)
    override val hideVolumeBar: StateFlow<Boolean> = MutableStateFlow(false)
    private val _lastPlayerScreen = MutableStateFlow(LastPlayerScreen.MAIN)
    override val lastPlayerScreen: StateFlow<LastPlayerScreen> = _lastPlayerScreen
    override val legacyMeshGradient: StateFlow<Boolean> = MutableStateFlow(false)
    override val lyricsBlur: StateFlow<Boolean> = MutableStateFlow(true)
    private val _lyricsOffsetMs = MutableStateFlow(0)
    override val lyricsOffsetMs: StateFlow<Int> = _lyricsOffsetMs
    override val lyricsSourceOrder: StateFlow<List<LyricsSource>> =
        MutableStateFlow(LyricsSource.entries)
    override val meteredConnection: StateFlow<Boolean?> = MutableStateFlow(false)
    override val preferUsbDac: StateFlow<Boolean> = MutableStateFlow(false)
    override val prioritizeSpotifyCanvas: StateFlow<Boolean> = MutableStateFlow(true)
    override val reduceAnimation: StateFlow<Boolean> = MutableStateFlow(false)
    override val reduceDynamicBlur: StateFlow<Boolean> = MutableStateFlow(false)
    override val showNerdStats: StateFlow<Boolean> = MutableStateFlow(false)
    override val smartAnalysis: StateFlow<SmartAnalysis> = MutableStateFlow(SmartAnalysis())
    override val smartFadeEnabled: StateFlow<Boolean> = MutableStateFlow(true)
    override val smartMixInProgress: StateFlow<Boolean> = MutableStateFlow(false)
    override val smartMixBlend: StateFlow<MixBlend?> = MutableStateFlow(null)
    override val smartTransitionWindow: StateFlow<TransitionWindow?> = MutableStateFlow(null)
    override val spotifyCanvasAutoHide: StateFlow<Boolean> = MutableStateFlow(false)
    override val syncedLyrics: StateFlow<Boolean> = MutableStateFlow(true)
    override val translationLanguage: StateFlow<String> = MutableStateFlow("en")
    override val versionAlignmentInProgress: StateFlow<Boolean> = MutableStateFlow(false)

    override fun setLastPlayerScreen(value: LastPlayerScreen) {
        _lastPlayerScreen.value = value
    }

    override fun setLyricsOffsetMs(value: Int) {
        _lyricsOffsetMs.value = value
    }
}

internal object IOSSystemVolume : SystemVolume {
    private val _level = MutableStateFlow(0.85f)
    override val level: StateFlow<Float> = _level

    override fun set(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        _level.value = clamped
        IOSPlaybackController.setVolume(clamped)
    }

    override fun refresh() = Unit
}

internal object IOSPlayerHost : PlayerHost {
    override val settings: PlayerSettingsSource = IOSPlayerSettings

    override fun cachedCanvas(song: Song): CanvasArtwork? = null

    override suspend fun canvasFor(song: Song): CanvasArtwork? = null

    @Composable
    override fun CanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) = Unit

    @Composable
    override fun rememberRemoteArtworkUrl(song: Song?): String? = song?.thumbnailUrl

    override val volume: SystemVolume = IOSSystemVolume

    @Composable
    override fun rememberAudioOutputs(): List<AudioOutputDevice> = listOf(
        AudioOutputDevice(
            id = 1,
            name = "iPad Speakers / AirPods (CoreAudio)",
            kind = AudioOutputKind.PHONE,
            isActive = true,
        )
    )

    @Composable
    override fun rememberOutputPicker(onOpen: () -> Unit): () -> Unit = onOpen

    override fun selectAudioOutput(id: Int?) = Unit

    override val outputFormat: StateFlow<OutputFormatUi> =
        MutableStateFlow(OutputFormatUi(summary = "AAC 256 kbps · 44.1 kHz (AVAudioEngine)", carriesHiRes = false))

    override val party: StateFlow<PartyUi> = MutableStateFlow(PartyUi())

    override suspend fun translateLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsTranslationResult {
        val res = LyricsTranslation.translate(trackId, lines, targetLanguageTag)
        return when (res) {
            is LyricsTranslation.Result.Translated -> LyricsTranslationResult.Translated(res.lines)
            is LyricsTranslation.Result.SameLanguage -> LyricsTranslationResult.SameLanguage(res.language)
            else -> LyricsTranslationResult.Unavailable
        }
    }

    override suspend fun romanizeLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsRomanizationResult {
        val res = LyricsTranslation.romanize(trackId, lines, targetLanguageTag)
        return when (res) {
            is LyricsTranslation.RomanizationResult.Romanized -> LyricsRomanizationResult.Romanized(res.lines)
            is LyricsTranslation.RomanizationResult.AlreadyRomanized -> LyricsRomanizationResult.AlreadyRomanized
            else -> LyricsRomanizationResult.Unavailable
        }
    }

    override fun showMessage(message: String) = Unit

    override val lyricsShareAvailable: Boolean
        get() = true

    @Composable
    override fun LyricsShareSheet(
        hazeState: HazeState,
        request: LyricsShareRequest,
        onDismiss: () -> Unit,
    ) {
        val pickedLines = request.lines.filter { !it.isGap && it.text.isNotBlank() }.map { it.text }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF16192B))
                    .clickable(enabled = false) {}
                    .padding(24.dp),
            ) {
                Text(
                    text = "Share Lyrics Card (9:16)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${request.song.title} · ${request.song.artist}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA6AABF),
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1F243D))
                        .padding(16.dp),
                ) {
                    pickedLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                    Button(
                        onClick = {
                            val card = ShareSheet.renderLyricsStoryCardPng(
                                trackTitle = request.song.title,
                                artistName = request.song.artist,
                                selectedLines = pickedLines,
                            )
                            val shareText = buildString {
                                appendLine("\"${pickedLines.joinToString("\n")}\"")
                                append("— ${request.song.title} by ${request.song.artist}")
                            }
                            ShareSheet.presentShareSheet(listOf(shareText, card.pngBytes))
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B5C)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.IosShare, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Share", color = Color.White)
                    }
                }
            }
        }
    }

    @Composable
    override fun AudioPipelineDialog(
        hazeState: HazeState,
        isPlaying: Boolean,
        onDismiss: () -> Unit,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF151828))
                    .clickable(enabled = false) {}
                    .padding(24.dp),
            ) {
                Text(
                    text = "Audio Signal Pipeline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(14.dp))
                PipelineStageRow("Source Stream", "YouTube Music AAC-LC 256 kbps (itag 141/140)")
                PipelineStageRow("Hardware Decoder", "Apple AudioToolbox / AVFoundation 32-bit Float")
                PipelineStageRow("DSP Engine", "C++17 libbitchord_analysis + 10-Band Biquad EQ")
                PipelineStageRow("Crossfade Deck", "Dual-Deck Equal-Power (${IOSPlaybackController.crossfadeSeconds.toInt()}s)")
                PipelineStageRow("Output Route", "iPadOS CoreAudio · 44.1 kHz Stereo (AirPlay 2 / A2DP)")
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PipelineStageRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFA6AABF))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

internal object IOSPlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val avPlayer = AVPlayer()
    val audioEngine = IOSAudioEngine()
    val keychain = IOSKeychainAuthStore()
    val nowPlayingManager = IOSNowPlayingManager(
        onPlay = { resume() },
        onPause = { pause() },
        onTogglePlayPause = { togglePlayPause() },
        onNextTrack = { next() },
        onPreviousTrack = { previous() },
        onSeekToSeconds = { sec: Double -> seekTo((sec * 1000.0).toLong()) },
    )
    val position = PlaybackPosition()

    var currentSong by mutableStateOf<Song?>(null)
        private set
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    var queueIndex by mutableIntStateOf(0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var repeatMode by mutableIntStateOf(RepeatModes.OFF)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set
    var autoplayEnabled by mutableStateOf(true)
        private set
    var lyrics by mutableStateOf<List<LyricLine>?>(null)
        private set
    var lyricsSource by mutableStateOf<LyricsSource?>(null)
        private set
    var lyricsProviderStates by mutableStateOf<Map<LyricsSource, LyricsProviderState>>(emptyMap())
        private set
    var lyricsUnavailable by mutableStateOf(false)
        private set

    // Account state
    var signedIn by mutableStateOf(false)
        private set
    var account by mutableStateOf<Account?>(null)
        private set

    // Audio DSP settings state
    var activeEqPreset by mutableStateOf("Flat")
        private set
    var eqBands by mutableStateOf(audioEngine.getEqualizerBands().toList())
        private set
    var crossfadeSeconds by mutableFloatStateOf(4.0f)
        private set
    var playbackSpeed by mutableFloatStateOf(1.0f)
        private set

    private var playJob: Job? = null
    private var lyricsJob: Job? = null
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        AppUi.install(IOSAppUiHost)
        PlayerPlatform.install(IOSPlayerHost)

        try {
            val savedCookie = keychain.getSecret("INNERTUBE_COOKIE")
            if (!savedCookie.isNullOrBlank()) {
                Innertube.cookie = savedCookie
                signedIn = true
                refreshAccountProfile()
            }
            IOSSandboxStorage.resolveApplicationSupportDirectory()
            InnerTubeXResolver.init(poTokenProvider = IOSPoTokenProvider.shared)
            nowPlayingManager.configureAudioSession()
            nowPlayingManager.registerRemoteCommands()
        } catch (_: Throwable) {
        }

        scope.launch {
            while (true) {
                delay(250L)
                if (isPlaying) {
                    val item = avPlayer.currentItem
                    if (item != null) {
                        val curSec = CMTimeGetSeconds(avPlayer.currentTime())
                        if (!curSec.isNaN() && curSec >= 0.0) {
                            val posMs = (curSec * 1000.0).toLong()
                            position.report(posMs)
                        }
                        val durSec = CMTimeGetSeconds(item.duration)
                        if (!durSec.isNaN() && durSec > 0.0 && durSec < 36000.0) {
                            durationMs = (durSec * 1000.0).toLong()
                        }
                        if (durationMs > 1500L && position.positionMs >= durationMs - 400L) {
                            onTrackEnded()
                        }
                    }
                }
            }
        }
    }

    fun applyYouTubeCookie(cookieHeader: String, onCompleted: () -> Unit = {}) {
        val cleaned = cookieHeader.trim()
        if (cleaned.isEmpty()) return
        try {
            keychain.putSecret("INNERTUBE_COOKIE", cleaned)
        } catch (_: Throwable) {
        }
        Innertube.cookie = cleaned
        signedIn = true
        InnerTubeXResolver.onSessionChanged()
        refreshAccountProfile()
        onCompleted()
    }

    fun signOut(onCompleted: () -> Unit = {}) {
        try {
            keychain.deleteSecret("INNERTUBE_COOKIE")
        } catch (_: Throwable) {
        }
        Innertube.cookie = null
        signedIn = false
        account = null
        LikeState.clear()
        InnerTubeXResolver.onSessionChanged()
        onCompleted()
    }

    fun refreshAccountProfile() {
        scope.launch {
            val fetched = withContext(Dispatchers.Default) {
                YtMusicRepository.account().getOrNull()
            }
            if (fetched != null) {
                account = fetched
            }
        }
    }

    fun selectEqualizerPreset(presetName: String) {
        if (audioEngine.applyEqualizerPreset(presetName)) {
            activeEqPreset = presetName
            eqBands = audioEngine.getEqualizerBands().toList()
        }
    }

    fun updateEqBand(index: Int, gainDb: Float) {
        val current = audioEngine.getEqualizerBands()
        if (index !in current.indices) return
        current[index] = gainDb.coerceIn(-12f, 12f)
        audioEngine.setEqualizer(current)
        activeEqPreset = "Custom"
        eqBands = current.toList()
    }

    fun updateCrossfade(seconds: Float) {
        crossfadeSeconds = seconds.coerceIn(0f, 12f)
    }

    fun updatePlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        playbackSpeed = clamped
        audioEngine.setDeckPitchAndRate(DeckId.A, clamped, 0f)
        if (isPlaying) {
            avPlayer.rate = clamped
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, songs.lastIndex)
        queue = songs
        queueIndex = safeIdx
        loadAndPlay(songs[safeIdx])
    }

    fun playSong(song: Song) {
        playQueue(listOf(song), 0)
        scope.launch(Dispatchers.Default) {
            val radioSongs = YtMusicRepository.radio(song.videoId).getOrNull().orEmpty()
                .filterNot { it.videoId == song.videoId }
            if (radioSongs.isNotEmpty() && currentSong?.videoId == song.videoId && queue.size <= 1) {
                scope.launch(Dispatchers.Main) {
                    queue = listOf(song) + radioSongs
                }
            }
        }
    }

    private fun loadAndPlay(song: Song) {
        currentSong = song
        isLoading = true
        isPlaying = false
        position.report(0L)
        position.seeks += 1
        durationMs = 180_000L

        fetchLyricsFor(song)

        playJob?.cancel()
        playJob = scope.launch {
            val streamUrl: String? = withContext(Dispatchers.Default) {
                try {
                    val resolved = InnerTubeXResolver.resolveStream(song.videoId, requireM4a = true)
                        ?: InnerTubeXResolver.resolveStream(song.videoId, requireM4a = false)
                    resolved?.streamUrl
                } catch (_: Throwable) {
                    null
                }
            }
            if (!streamUrl.isNullOrBlank()) {
                val nsUrl = NSURL.URLWithString(streamUrl)
                if (nsUrl != null) {
                    val item = AVPlayerItem.playerItemWithURL(nsUrl)
                    avPlayer.replaceCurrentItemWithPlayerItem(item)
                    avPlayer.play()
                    if (playbackSpeed != 1.0f) {
                        avPlayer.rate = playbackSpeed
                    }
                    isPlaying = true
                }
            }
            isLoading = false
            nowPlayingManager.updateNowPlayingMetadata(
                title = song.title,
                artist = song.artist,
                albumTitle = song.albumName ?: "",
                durationSeconds = (durationMs / 1000L).toDouble().coerceAtLeast(1.0),
                elapsedSeconds = 0.0,
                playbackRate = if (isPlaying) playbackSpeed.toDouble() else 0.0,
            )
        }
    }

    private fun fetchLyricsFor(song: Song, preferredSource: LyricsSource? = null) {
        lyricsJob?.cancel()
        lyrics = null
        lyricsSource = null
        lyricsUnavailable = false
        lyricsProviderStates = LyricsSource.entries.associateWith {
            if (it == preferredSource) LyricsProviderState.FETCHING else LyricsProviderState.NOT_FETCHED
        }

        lyricsJob = scope.launch {
            val dur = if (durationMs > 0L) durationMs else 180_000L
            val sourcesToTry = if (preferredSource != null) setOf(preferredSource) else LyricsSource.entries.toSet()
            val orderToTry = if (preferredSource != null) listOf(preferredSource) else LyricsSource.entries
            val res = withContext(Dispatchers.Default) {
                try {
                    LyricsRepository.lyrics(
                        videoId = song.videoId,
                        title = song.title,
                        artist = song.artist,
                        durationMs = dur,
                        album = song.albumName,
                        sources = sourcesToTry,
                        order = orderToTry,
                    )
                } catch (_: Throwable) {
                    null
                }
            }
            if (res != null && res.lines.isNotEmpty()) {
                lyrics = res.lines
                lyricsSource = res.source
                lyricsUnavailable = false
                lyricsProviderStates = lyricsProviderStates + (res.source to LyricsProviderState.FOUND)
            } else {
                lyricsUnavailable = true
                if (preferredSource != null) {
                    lyricsProviderStates = lyricsProviderStates + (preferredSource to LyricsProviderState.NOT_FOUND)
                }
            }
        }
    }

    fun selectLyricsProvider(source: LyricsSource) {
        val s = currentSong ?: return
        fetchLyricsFor(s, preferredSource = source)
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else resume()
    }

    fun pause() {
        avPlayer.pause()
        isPlaying = false
        val s = currentSong ?: return
        nowPlayingManager.updateNowPlayingMetadata(
            title = s.title,
            artist = s.artist,
            albumTitle = s.albumName ?: "",
            durationSeconds = (durationMs / 1000L).toDouble(),
            elapsedSeconds = (position.positionMs / 1000L).toDouble(),
            playbackRate = 0.0,
        )
    }

    fun resume() {
        avPlayer.play()
        if (playbackSpeed != 1.0f) {
            avPlayer.rate = playbackSpeed
        }
        isPlaying = true
        val s = currentSong ?: return
        nowPlayingManager.updateNowPlayingMetadata(
            title = s.title,
            artist = s.artist,
            albumTitle = s.albumName ?: "",
            durationSeconds = (durationMs / 1000L).toDouble(),
            elapsedSeconds = (position.positionMs / 1000L).toDouble(),
            playbackRate = playbackSpeed.toDouble(),
        )
    }

    fun seekTo(ms: Long) {
        val targetMs = ms.coerceAtLeast(0L)
        position.report(targetMs)
        position.seeks += 1
        avPlayer.seekToTime(CMTimeMakeWithSeconds(targetMs.toDouble() / 1000.0, 1000))
    }

    fun seekFraction(fraction: Float) {
        if (durationMs <= 0L) return
        seekTo((durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun setVolume(vol: Float) {
        avPlayer.volume = vol.coerceIn(0f, 1f)
    }

    fun next() {
        if (queue.isEmpty()) return
        if (queueIndex + 1 < queue.size) {
            queueIndex += 1
            loadAndPlay(queue[queueIndex])
        } else if (repeatMode == RepeatModes.ALL) {
            queueIndex = 0
            loadAndPlay(queue[0])
        }
    }

    fun previous() {
        if (position.positionMs > 4_000L) {
            seekTo(0L)
            return
        }
        if (queueIndex > 0) {
            queueIndex -= 1
            loadAndPlay(queue[queueIndex])
        } else {
            seekTo(0L)
        }
    }

    fun jumpTo(index: Int) {
        if (index in queue.indices) {
            queueIndex = index
            loadAndPlay(queue[index])
        }
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices || queue.size <= 1) return
        val mutable = queue.toMutableList()
        mutable.removeAt(index)
        queue = mutable
        if (index < queueIndex) {
            queueIndex = (queueIndex - 1).coerceAtLeast(0)
        } else if (index == queueIndex) {
            queueIndex = queueIndex.coerceIn(0, queue.lastIndex)
            loadAndPlay(queue[queueIndex])
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val mutable = queue.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        queue = mutable
        queueIndex = when (queueIndex) {
            from -> to
            in (minOf(from, to)..maxOf(from, to)) ->
                if (from < to) queueIndex - 1 else queueIndex + 1
            else -> queueIndex
        }
    }

    fun clearQueue() {
        val current = currentSong ?: return
        queue = listOf(current)
        queueIndex = 0
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        if (shuffleEnabled && queue.size > 2) {
            val current = currentSong ?: return
            val rest = queue.filterNot { it.videoId == current.videoId }.shuffled()
            queue = listOf(current) + rest
            queueIndex = 0
        }
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatModes.OFF -> RepeatModes.ALL
            RepeatModes.ALL -> RepeatModes.ONE
            else -> RepeatModes.OFF
        }
    }

    fun toggleAutoplay() {
        autoplayEnabled = !autoplayEnabled
    }

    private fun onTrackEnded() {
        if (repeatMode == RepeatModes.ONE) {
            seekTo(0L)
            resume()
        } else {
            next()
        }
    }
}

private enum class IosTab(val label: String) {
    HOME("Listen Now"),
    EXPLORE("Explore"),
    LIBRARY("Library"),
    SEARCH("Search"),
    SETTINGS("Settings & EQ"),
}

private val BitChordDarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0A0B10),
    secondary = Color(0xFFFF3B5C),
    background = Color(0xFF0A0B10),
    onBackground = Color(0xFFF5F5F8),
    surface = Color(0xFF141622),
    onSurface = Color(0xFFF5F5F8),
    surfaceVariant = Color(0xFF1E2132),
    onSurfaceVariant = Color(0xFFA6AABF),
)

@Composable
fun BitChordIosApp() {
    LaunchedEffect(Unit) {
        IOSPlaybackController.ensureInitialized()
    }

    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(IosTab.HOME) }
    var playerExpanded by remember { mutableStateOf(false) }
    var showSignInModal by remember { mutableStateOf(false) }

    // Home state
    var homeState by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    var homeContinuation by remember { mutableStateOf<String?>(null) }
    var homeRefreshing by remember { mutableStateOf(false) }
    var homeLoadingMore by remember { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    val homePullState = rememberPullToRefreshState()

    // Explore state
    var exploreState by remember { mutableStateOf<UiState<List<MoodGenreSection>>>(UiState.Loading) }
    var exploreRefreshing by remember { mutableStateOf(false) }
    val exploreListState = rememberLazyListState()
    val explorePullState = rememberPullToRefreshState()

    // Library state
    var libraryState by remember { mutableStateOf<UiState<LibraryPage>>(UiState.Loading) }
    var libraryRefreshing by remember { mutableStateOf(false) }
    val libraryListState = rememberLazyListState()
    val libraryPullState = rememberPullToRefreshState()

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf(SearchFilter.SONGS) }
    var searchResults by remember { mutableStateOf<UiState<List<SearchResult>>?>(null) }
    var searchContinuation by remember { mutableStateOf<String?>(null) }
    var searchLoadingMore by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchTypeahead by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchHistory by remember { mutableStateOf<List<SearchHistoryEntity>>(emptyList()) }
    var searchScrollReset by remember { mutableIntStateOf(0) }
    var searchFocusRequested by remember { mutableStateOf(false) }
    val searchListState = rememberLazyListState()

    // Collection / Browse detail overlay state
    var activeBrowseTitle by remember { mutableStateOf<String?>(null) }
    var activeBrowseSongs by remember { mutableStateOf<UiState<List<Song>>?>(null) }

    fun loadHome(isRefresh: Boolean = false) {
        scope.launch {
            if (isRefresh) homeRefreshing = true else homeState = UiState.Loading
            val res = withContext(Dispatchers.Default) {
                YtMusicRepository.home()
            }
            res.onSuccess { feed ->
                homeContinuation = feed.continuation
                if (feed.shelves.isNotEmpty()) {
                    homeState = UiState.Success(feed.shelves)
                } else {
                    val fallback = withContext(Dispatchers.Default) {
                        YtMusicRepository.homeSupplement("FEmusic_new_releases")
                    }.getOrDefault(emptyList())
                    homeState = UiState.Success(fallback)
                }
            }.onFailure { err ->
                homeState = UiState.Error(err.message ?: "Unable to load YouTube Music home feed")
            }
            homeRefreshing = false
        }
    }

    fun loadExplore(isRefresh: Boolean = false) {
        scope.launch {
            if (isRefresh) exploreRefreshing = true else exploreState = UiState.Loading
            val res = withContext(Dispatchers.Default) {
                YtMusicRepository.moodAndGenres()
            }
            res.onSuccess { sections ->
                exploreState = UiState.Success(sections)
            }.onFailure { err ->
                exploreState = UiState.Error(err.message ?: "Unable to load Explore categories")
            }
            exploreRefreshing = false
        }
    }

    fun loadLibrary(isRefresh: Boolean = false) {
        scope.launch {
            if (isRefresh) libraryRefreshing = true else libraryState = UiState.Loading
            val res = withContext(Dispatchers.Default) {
                YtMusicRepository.library()
            }
            res.onSuccess { page ->
                libraryState = UiState.Success(page)
            }.onFailure { err ->
                libraryState = UiState.Error(err.message ?: "Sign in to load your YouTube Music library")
            }
            libraryRefreshing = false
        }
    }

    fun runSearch(q: String = searchQuery, f: SearchFilter = searchFilter) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            searchResults = null
            return
        }
        scope.launch {
            searchResults = UiState.Loading
            val res = withContext(Dispatchers.Default) {
                YtMusicRepository.searchPage(trimmed, f)
            }
            res.onSuccess { page ->
                searchContinuation = page.continuation
                searchResults = UiState.Success(page.rows)
                searchScrollReset += 1
            }.onFailure { err ->
                searchResults = UiState.Error(err.message ?: "Search failed")
            }
        }
    }

    fun openBrowseCollection(browseId: String, title: String) {
        activeBrowseTitle = title
        activeBrowseSongs = UiState.Loading
        scope.launch {
            val res = withContext(Dispatchers.Default) {
                YtMusicRepository.browseSongs(browseId)
            }
            res.onSuccess { page ->
                activeBrowseSongs = UiState.Success(page.songs)
            }.onFailure { err ->
                activeBrowseSongs = UiState.Error(err.message ?: "Failed to load collection")
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHome()
        loadExplore()
        if (IOSPlaybackController.signedIn) {
            loadLibrary()
        }
    }

    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) {
            searchSuggestions = emptyList()
            searchTypeahead = emptyList()
            return@LaunchedEffect
        }
        delay(220L)
        val sugg = withContext(Dispatchers.Default) {
            YtMusicRepository.searchSuggestions(q).getOrDefault(emptyList())
        }
        val ahead = withContext(Dispatchers.Default) {
            YtMusicRepository.searchTypeahead(q).getOrNull()?.rows.orEmpty()
        }
        searchSuggestions = sugg
        searchTypeahead = ahead
    }

    MaterialTheme(colorScheme = BitChordDarkColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val winWidth = maxWidth
                val winHeight = maxHeight
                val layoutMode = BitChordComposeHost.classifyWindowWidthDp(winWidth.value.toInt())
                val isWideIpad = layoutMode != StageManagerLayoutMode.COMPACT
                val currentSong = IOSPlaybackController.currentSong
                val isPlaying = IOSPlaybackController.isPlaying
                val signedIn = IOSPlaybackController.signedIn
                val account = IOSPlaybackController.account

                Row(modifier = Modifier.fillMaxSize()) {
                    if (isWideIpad) {
                        IpadSidebar(
                            activeTab = activeTab,
                            signedIn = signedIn,
                            account = account,
                            onSelectTab = { tab ->
                                activeTab = tab
                                activeBrowseTitle = null
                                if (tab == IosTab.LIBRARY && signedIn && libraryState is UiState.Loading) {
                                    loadLibrary()
                                }
                            },
                            onSignInClick = { showSignInModal = true },
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val bottomInset = if (currentSong != null) 104.dp else 28.dp
                        val pagePadding = PaddingValues(top = 24.dp, bottom = bottomInset)

                        when (activeTab) {
                            IosTab.HOME -> {
                                HomeScreen(
                                    state = homeState,
                                    listState = homeListState,
                                    onItemClick = { item, _ ->
                                        val vid = item.videoId
                                        val bid = item.browseId
                                        if (!vid.isNullOrBlank()) {
                                            IOSPlaybackController.playSong(
                                                Song(
                                                    videoId = vid,
                                                    title = item.title,
                                                    artist = item.subtitle,
                                                    thumbnailUrl = item.thumbnailUrl,
                                                )
                                            )
                                        } else if (!bid.isNullOrBlank()) {
                                            openBrowseCollection(bid, item.title)
                                        }
                                    },
                                    onRetry = { loadHome() },
                                    refreshing = homeRefreshing,
                                    onRefresh = { loadHome(isRefresh = true) },
                                    pullState = homePullState,
                                    contentPadding = pagePadding,
                                    title = "Listen Now",
                                    signedIn = signedIn,
                                    onSignIn = { showSignInModal = true },
                                    onLoadMore = homeContinuation?.let { token ->
                                        {
                                            if (!homeLoadingMore) {
                                                homeLoadingMore = true
                                                scope.launch {
                                                    val more = withContext(Dispatchers.Default) {
                                                        YtMusicRepository.moreHome(token)
                                                    }
                                                    more.onSuccess { nextFeed ->
                                                        homeContinuation = nextFeed.continuation
                                                        val cur = (homeState as? UiState.Success)?.data.orEmpty()
                                                        homeState = UiState.Success(cur + nextFeed.shelves)
                                                    }
                                                    homeLoadingMore = false
                                                }
                                            }
                                        }
                                    },
                                    loadingMore = homeLoadingMore,
                                    leadHero = !isWideIpad,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                )
                            }

                            IosTab.EXPLORE -> {
                                ExploreScreen(
                                    state = exploreState,
                                    listState = exploreListState,
                                    onCategoryClick = { mood ->
                                        activeBrowseTitle = mood.title
                                        activeBrowseSongs = UiState.Loading
                                        scope.launch {
                                            val shelves = withContext(Dispatchers.Default) {
                                                YtMusicRepository.moodGenreShelves(mood.browseId, mood.params)
                                            }.getOrDefault(emptyList())
                                            val firstPlaylist = shelves.asSequence()
                                                .flatMap { it.items.asSequence() }
                                                .firstOrNull { !it.browseId.isNullOrBlank() }
                                            if (firstPlaylist?.browseId != null) {
                                                openBrowseCollection(firstPlaylist.browseId!!, mood.title)
                                            } else {
                                                activeBrowseSongs = UiState.Error("No tracks found in ${mood.title}")
                                            }
                                        }
                                    },
                                    onRetry = { loadExplore() },
                                    refreshing = exploreRefreshing,
                                    onRefresh = { loadExplore(isRefresh = true) },
                                    pullState = explorePullState,
                                    contentPadding = pagePadding,
                                    showTitle = true,
                                )
                            }

                            IosTab.LIBRARY -> {
                                LibraryScreen(
                                    signedIn = signedIn,
                                    state = libraryState,
                                    listState = libraryListState,
                                    onShelfItemClick = { item ->
                                        val vid = item.videoId
                                        val bid = item.browseId
                                        if (!vid.isNullOrBlank()) {
                                            IOSPlaybackController.playSong(
                                                Song(
                                                    videoId = vid,
                                                    title = item.title,
                                                    artist = item.subtitle,
                                                    thumbnailUrl = item.thumbnailUrl,
                                                )
                                            )
                                        } else if (!bid.isNullOrBlank()) {
                                            openBrowseCollection(bid, item.title)
                                        }
                                    },
                                    onShelfItemLongPress = {},
                                    onNewPlaylist = {},
                                    onShowAll = { shelf ->
                                        val songs = shelf.items.mapNotNull { item ->
                                            item.videoId?.let { vid ->
                                                Song(
                                                    videoId = vid,
                                                    title = item.title,
                                                    artist = item.subtitle,
                                                    thumbnailUrl = item.thumbnailUrl,
                                                )
                                            }
                                        }
                                        if (songs.isNotEmpty()) {
                                            activeBrowseTitle = shelf.title
                                            activeBrowseSongs = UiState.Success(songs)
                                        }
                                    },
                                    replay = {},
                                    onSignIn = { showSignInModal = true },
                                    onRetry = { loadLibrary() },
                                    refreshing = libraryRefreshing,
                                    onRefresh = { loadLibrary(isRefresh = true) },
                                    pullState = libraryPullState,
                                    contentPadding = pagePadding,
                                    links = emptyList(),
                                    deviceItems = emptyList(),
                                    showTitle = true,
                                )
                            }

                            IosTab.SEARCH -> {
                                SearchScreen(
                                    query = searchQuery,
                                    onQueryChange = { q ->
                                        searchQuery = q
                                        if (q.isBlank()) searchResults = null
                                    },
                                    filter = searchFilter,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    onFilterChange = { f ->
                                        searchFilter = f
                                        if (searchQuery.isNotBlank()) runSearch(searchQuery, f)
                                    },
                                    results = searchResults,
                                    loadingMore = searchLoadingMore,
                                    onLoadMore = {
                                        val token = searchContinuation
                                        if (token != null && !searchLoadingMore) {
                                            searchLoadingMore = true
                                            scope.launch {
                                                val more = withContext(Dispatchers.Default) {
                                                    YtMusicRepository.searchContinuation(token, searchFilter)
                                                }
                                                more.onSuccess { page ->
                                                    searchContinuation = page.continuation
                                                    val cur = (searchResults as? UiState.Success)?.data.orEmpty()
                                                    searchResults = UiState.Success(cur + page.rows)
                                                }
                                                searchLoadingMore = false
                                            }
                                        }
                                    },
                                    listState = searchListState,
                                    scrollResetTrigger = searchScrollReset,
                                    focusRequested = searchFocusRequested,
                                    onFocusHandled = { searchFocusRequested = false },
                                    onSongClick = { songs, idx ->
                                        IOSPlaybackController.playQueue(songs, idx)
                                    },
                                    onSongLongPress = {},
                                    onSongSwipe = { s -> IOSPlaybackController.playSong(s) },
                                    onTopResultPlay = { s -> IOSPlaybackController.playSong(s) },
                                    onTopResultPlaylist = { s -> IOSPlaybackController.playSong(s) },
                                    onBrowseClick = { b: BrowseItem ->
                                        openBrowseCollection(b.browseId, b.title)
                                    },
                                    history = searchHistory,
                                    suggestions = searchSuggestions,
                                    typeaheadResults = searchTypeahead,
                                    onSubmit = { runSearch() },
                                    onSuggestionClick = { s ->
                                        searchQuery = s
                                        runSearch(s, searchFilter)
                                    },
                                    onHistoryClick = { h ->
                                        searchQuery = h.title
                                        runSearch(h.title, searchFilter)
                                    },
                                    onHistoryRemove = { id ->
                                        searchHistory = searchHistory.filterNot { it.id == id }
                                    },
                                    onHistoryClear = { searchHistory = emptyList() },
                                    contentPadding = pagePadding,
                                    topPadding = 24.dp,
                                    showField = true,
                                )
                            }

                            IosTab.SETTINGS -> {
                                SettingsAndEqualizerScreen(
                                    signedIn = signedIn,
                                    account = account,
                                    onOpenSignIn = { showSignInModal = true },
                                    onSignOut = {
                                        IOSPlaybackController.signOut {
                                            loadHome(isRefresh = true)
                                            libraryState = UiState.Loading
                                        }
                                    },
                                    bottomPadding = bottomInset,
                                )
                            }
                        }

                        // Browse collection detail overlay
                        if (activeBrowseTitle != null && activeBrowseSongs != null) {
                            BrowseCollectionOverlay(
                                title = activeBrowseTitle ?: "",
                                state = activeBrowseSongs!!,
                                onClose = {
                                    activeBrowseTitle = null
                                    activeBrowseSongs = null
                                },
                                onPlaySong = { songs, idx ->
                                    IOSPlaybackController.playQueue(songs, idx)
                                },
                            )
                        }

                        // Bottom Mini-Player & Compact Navigation Bar
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            if (currentSong != null) {
                                IpadMiniPlayerBar(
                                    song = currentSong,
                                    isPlaying = isPlaying,
                                    isLoading = IOSPlaybackController.isLoading,
                                    onTogglePlay = { IOSPlaybackController.togglePlayPause() },
                                    onNext = { IOSPlaybackController.next() },
                                    onExpand = { playerExpanded = true },
                                )
                            }

                            if (!isWideIpad) {
                                Spacer(Modifier.height(8.dp))
                                CompactBottomTabBar(
                                    activeTab = activeTab,
                                    onSelectTab = { tab ->
                                        activeTab = tab
                                        activeBrowseTitle = null
                                        if (tab == IosTab.LIBRARY && signedIn && libraryState is UiState.Loading) {
                                            loadLibrary()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Expanded full-screen NowPlayingScreen
                AnimatedVisibility(
                    visible = playerExpanded && currentSong != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                ) {
                    val song = currentSong
                    if (song != null) {
                        val likeOverrides by LikeState.overrides.collectAsState()
                        val likeStatus = likeOverrides[song.videoId] ?: LikeStatus.INDIFFERENT
                        var lyricsOffsetOpen by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B10))) {
                            NowPlayingScreen(
                                song = song,
                                isPlaying = isPlaying,
                                isLoading = IOSPlaybackController.isLoading,
                                position = IOSPlaybackController.position,
                                durationMs = IOSPlaybackController.durationMs,
                                audioVersionSwitching = false,
                                qualityUpgraded = false,
                                queue = IOSPlaybackController.queue,
                                queueIndex = IOSPlaybackController.queueIndex,
                                hasPrevious = IOSPlaybackController.queueIndex > 0,
                                hasNext = IOSPlaybackController.queueIndex + 1 < IOSPlaybackController.queue.size,
                                repeatMode = IOSPlaybackController.repeatMode,
                                shuffleEnabled = IOSPlaybackController.shuffleEnabled,
                                autoplayEnabled = IOSPlaybackController.autoplayEnabled,
                                signedIn = signedIn,
                                accountName = account?.name,
                                likeStatus = likeStatus,
                                onToggleLike = {
                                    val nextStatus = if (likeStatus == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE
                                    LikeState.set(song.videoId, nextStatus)
                                },
                                onPlayPause = { IOSPlaybackController.togglePlayPause() },
                                onNext = { IOSPlaybackController.next() },
                                onPrevious = { IOSPlaybackController.previous() },
                                onBlockedControl = {},
                                onSeek = { ms -> IOSPlaybackController.seekTo(ms) },
                                onSeekFraction = { frac -> IOSPlaybackController.seekFraction(frac) },
                                onToggleShuffle = { IOSPlaybackController.toggleShuffle() },
                                onCycleRepeat = { IOSPlaybackController.cycleRepeat() },
                                onToggleAutoplay = { IOSPlaybackController.toggleAutoplay() },
                                onJumpTo = { idx -> IOSPlaybackController.jumpTo(idx) },
                                onRemoveFromQueue = { idx -> IOSPlaybackController.removeFromQueue(idx) },
                                onMoveInQueue = { from, to -> IOSPlaybackController.moveInQueue(from, to) },
                                onClearQueue = { IOSPlaybackController.clearQueue() },
                                onOpenMenu = {},
                                onOpenAlbum = { albumId ->
                                    playerExpanded = false
                                    openBrowseCollection(albumId, song.albumName ?: "Album")
                                },
                                onOpenArtist = { browseId, name ->
                                    if (!browseId.isNullOrBlank()) {
                                        playerExpanded = false
                                        openBrowseCollection(browseId, name)
                                    }
                                },
                                onOpenPlaybackSource = { playerExpanded = false },
                                onListenTogether = {},
                                lyrics = IOSPlaybackController.lyrics,
                                lyricsSource = IOSPlaybackController.lyricsSource,
                                lyricsProviderStates = IOSPlaybackController.lyricsProviderStates,
                                onSelectLyricsProvider = { src ->
                                    IOSPlaybackController.selectLyricsProvider(src)
                                },
                                lyricsUnavailable = IOSPlaybackController.lyricsUnavailable,
                                lyricsOffsetOpen = lyricsOffsetOpen,
                                onDismissLyricsOffset = { lyricsOffsetOpen = false },
                                windowWidth = winWidth,
                                windowHeight = winHeight,
                            )

                            IconButton(
                                onClick = { playerExpanded = false },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .statusBarsPadding()
                                    .padding(16.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Minimize Player",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }

                // Full-screen YouTube Music WKWebView Sign-In Modal
                if (showSignInModal) {
                    YouTubeSignInModal(
                        onDismiss = { showSignInModal = false },
                        onSignedIn = { cookieHeader ->
                            IOSPlaybackController.applyYouTubeCookie(cookieHeader) {
                                showSignInModal = false
                                loadHome(isRefresh = true)
                                loadLibrary(isRefresh = true)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun YouTubeSignInModal(
    onDismiss: () -> Unit,
    onSignedIn: (String) -> Unit,
) {
    var manualCookieMode by remember { mutableStateOf(false) }
    var manualCookieText by remember { mutableStateOf("") }
    var detectedCookie by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Sign in with your Google Account below") }

    LaunchedEffect(manualCookieMode) {
        if (manualCookieMode) return@LaunchedEffect
        while (true) {
            delay(1200L)
            WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { rawList ->
                val cookies = rawList?.filterIsInstance<NSHTTPCookie>().orEmpty()
                val ytCookies = cookies.filter { c ->
                    val dom = c.domain.lowercase()
                    dom.contains("youtube.com") || dom.contains("google.com")
                }
                val hasSapisid = ytCookies.any { c ->
                    c.name == "SAPISID" || c.name == "__Secure-3PAPISID"
                }
                if (hasSapisid) {
                    val header = ytCookies.joinToString("; ") { c -> "${c.name}=${c.value}" }
                    detectedCookie = header
                    statusText = "Account session ready! Tap 'Use this profile' to finish."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B10))
            .statusBarsPadding(),
    ) {
        // Top Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141622))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = "Sign in to YouTube Music",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (detectedCookie != null) Color(0xFF4CD964) else Color(0xFFA6AABF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { manualCookieMode = !manualCookieMode },
                ) {
                    Text(if (manualCookieMode) "Web Login" else "Paste Cookie", color = Color.White)
                }

                Button(
                    onClick = {
                        if (manualCookieMode) {
                            if (manualCookieText.isNotBlank()) {
                                onSignedIn(manualCookieText)
                            }
                        } else {
                            val ready = detectedCookie
                            if (!ready.isNullOrBlank()) {
                                onSignedIn(ready)
                            } else {
                                WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { rawList ->
                                    val cookies = rawList?.filterIsInstance<NSHTTPCookie>().orEmpty()
                                    val ytCookies = cookies.filter { c ->
                                        val dom = c.domain.lowercase()
                                        dom.contains("youtube.com") || dom.contains("google.com")
                                    }
                                    if (ytCookies.isNotEmpty()) {
                                        val header = ytCookies.joinToString("; ") { c -> "${c.name}=${c.value}" }
                                        onSignedIn(header)
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (detectedCookie != null || manualCookieText.isNotBlank()) Color(0xFF4CD964) else Color(0xFFFF3B5C),
                    ),
                ) {
                    Text("Use this profile", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (manualCookieMode) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text(
                    text = "Paste YouTube Music Cookie Header",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Paste your Cookie string from music.youtube.com (containing SAPISID / __Secure-3PAPISID) to sign in directly:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA6AABF),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = manualCookieText,
                    onValueChange = { manualCookieText = it },
                    placeholder = { Text("SAPISID=...; __Secure-3PAPISID=...; SID=...", color = Color(0xFF6E738A)) },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
        } else {
            UIKitView(
                factory = {
                    val config = WKWebViewConfiguration()
                    config.websiteDataStore = WKWebsiteDataStore.defaultDataStore()
                    val webView = WKWebView(
                        frame = CGRectMake(0.0, 0.0, 1024.0, 768.0),
                        configuration = config,
                    )
                    webView.customUserAgent = SAFARI_IPAD_USER_AGENT
                    val nsUrl = NSURL.URLWithString(YOUTUBE_SIGN_IN_URL)
                    if (nsUrl != null) {
                        webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
                    }
                    webView
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsAndEqualizerScreen(
    signedIn: Boolean,
    account: Account?,
    onOpenSignIn: () -> Unit,
    onSignOut: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val scrollState = rememberScrollState()
    val eqBands = IOSPlaybackController.eqBands
    val activePreset = IOSPlaybackController.activeEqPreset
    val crossfadeSec = IOSPlaybackController.crossfadeSeconds
    val playbackSpeed = IOSPlaybackController.playbackSpeed
    val freqLabels = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    val presets = listOf("Flat", "Bass Boost", "Vocal", "Electronic", "Acoustic")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 28.dp, start = 28.dp, end = 28.dp, bottom = bottomPadding + 24.dp),
    ) {
        Text(
            text = "Settings & Audio DSP",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(20.dp))

        // Account Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF161928))
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (account?.thumbnailUrl != null) {
                    AsyncImage(
                        model = account.thumbnailUrl,
                        contentDescription = account.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(54.dp).clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(54.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (signedIn) (account?.name ?: "YouTube Music Account Connected") else "Guest Session (Not Signed In)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = if (signedIn) (account?.email ?: "Synced with iOS Keychain (SAPISIDHASH)") else "Sign in to sync your Liked Music, Playlists, and History",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA6AABF),
                    )
                }
                if (signedIn) {
                    OutlinedButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out", color = Color.White)
                    }
                } else {
                    Button(
                        onClick = onOpenSignIn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B5C)),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in to YouTube Music", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Crossfade & Tempo / Speed Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF161928))
                .padding(20.dp),
        ) {
            Text(
                text = "Dual-Deck Crossfade & Tempo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Equal-Power Crossfade Duration: ${crossfadeSec.toInt()}s",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA6AABF),
            )
            Slider(
                value = crossfadeSec,
                onValueChange = { IOSPlaybackController.updateCrossfade(it) },
                valueRange = 0f..12f,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Playback Speed (AVAudioUnitTimePitch): ${((playbackSpeed * 100).toInt() / 100.0)}x",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA6AABF),
            )
            Slider(
                value = playbackSpeed,
                onValueChange = { IOSPlaybackController.updatePlaybackSpeed(it) },
                valueRange = 0.5f..2.0f,
            )
        }

        Spacer(Modifier.height(20.dp))

        // 10-Band Parametric Equalizer Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF161928))
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "10-Band Biquad Equalizer ($activePreset)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(12.dp))

            // Preset chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                presets.forEach { preset ->
                    val isSelected = activePreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFFFF3B5C) else Color(0xFF22263A))
                            .clickable { IOSPlaybackController.selectEqualizerPreset(preset) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = preset,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))

            freqLabels.forEachIndexed { idx, freq ->
                val gain = eqBands.getOrElse(idx) { 0f }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Text(
                        text = freq,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA6AABF),
                        modifier = Modifier.width(56.dp),
                    )
                    Slider(
                        value = gain,
                        onValueChange = { IOSPlaybackController.updateEqBand(idx, it) },
                        valueRange = -12f..12f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${if (gain >= 0f) "+" else ""}${gain.toInt()} dB",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.width(56.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IpadSidebar(
    activeTab: IosTab,
    signedIn: Boolean,
    account: Account?,
    onSelectTab: (IosTab) -> Unit,
    onSignInClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(Color(0xFF10121C))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 20.dp),
    ) {
        Text(
            text = "BitChord",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(14.dp))

        IosTab.entries.forEach { tab ->
            val selected = tab == activeTab
            val icon = when (tab) {
                IosTab.HOME -> Icons.Rounded.Home
                IosTab.EXPLORE -> Icons.Rounded.Explore
                IosTab.LIBRARY -> Icons.Rounded.LibraryMusic
                IosTab.SEARCH -> Icons.Rounded.Search
                IosTab.SETTINGS -> Icons.Rounded.Equalizer
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = if (selected) Color.White else Color(0xFFA6AABF),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else Color(0xFFA6AABF),
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.weight(1f))

        // Sidebar Account / Sign-In Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (signedIn) Color(0xFF1A1E30) else Color(0xFFFF3B5C).copy(alpha = 0.18f))
                .clickable {
                    if (signedIn) onSelectTab(IosTab.SETTINGS) else onSignInClick()
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = if (signedIn) Icons.Rounded.AccountCircle else Icons.AutoMirrored.Rounded.Login,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (signedIn) (account?.name ?: "Signed In") else "Sign In",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (signedIn) "YouTube Music" else "Connect Google Account",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA6AABF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompactBottomTabBar(
    activeTab: IosTab,
    onSelectTab: (IosTab) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF181B28).copy(alpha = 0.95f))
            .padding(vertical = 10.dp),
    ) {
        IosTab.entries.forEach { tab ->
            val selected = tab == activeTab
            val icon = when (tab) {
                IosTab.HOME -> Icons.Rounded.Home
                IosTab.EXPLORE -> Icons.Rounded.Explore
                IosTab.LIBRARY -> Icons.Rounded.LibraryMusic
                IosTab.SEARCH -> Icons.Rounded.Search
                IosTab.SETTINGS -> Icons.Rounded.Settings
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = if (selected) Color.White else Color(0xFFA6AABF),
                )
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White else Color(0xFFA6AABF),
                )
            }
        }
    }
}

@Composable
private fun IpadMiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1C1F30).copy(alpha = 0.96f))
            .clickable { onExpand() }
            .padding(horizontal = 14.dp),
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2A2E45)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA6AABF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
        } else {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun BrowseCollectionOverlay(
    title: String,
    state: UiState<List<Song>>,
    onClose: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B10))
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (state) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = Color(0xFFA6AABF))
                }
            }

            is UiState.Success -> {
                val songs = state.data
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(songs) { idx, song ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlaySong(songs, idx) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222538)),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA6AABF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
