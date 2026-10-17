package com.music.bitchord.data

import com.music.bitchord.data.listentogether.PartyPlayback
import com.music.bitchord.data.listentogether.PartyQueue
import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.listentogether.partyQueueIndexOf
import com.music.bitchord.data.listentogether.partyUpcomingAfter
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.QueueBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Empirical stress tests for Queue models and algorithms:
 * - PartyQueueReading (Listen Together queue sync, duplicate needle resolution, bounds checks)
 * - QueueBuilder (De-duplication, audio vs video ranking, per-artist & seed caps)
 * - TrackMatcher (Kana transliteration, duration parsing, script handling)
 */
class QueueModelsStressTest {

    // ── PartyQueueReading Stress Tests ───────────────────────────────────────

    @Test
    fun testPartyQueueEmptyQueue() {
        val emptyQ = PartyQueue(seq = 1, index = 0, items = emptyList())
        val pb = PartyPlayback(queueSeq = 1, queueIndex = 0)

        assertEquals(-1, partyQueueIndexOf(emptyQ, pb, "any_id"))
        assertEquals(-1, partyQueueIndexOf(emptyQ, pb, null))
        assertTrue(partyUpcomingAfter(emptyQ, pb, "any_id").isEmpty())
    }

    @Test
    fun testPartyQueueOutOfBoundsHints() {
        val q = PartyQueue(
            seq = 5,
            index = 9999,
            items = listOf(PartyTrack("id1"), PartyTrack("id2"), PartyTrack("id3"))
        )
        val pbStale = PartyPlayback(queueSeq = 5, queueIndex = -42)

        // Despite negative/out of bounds hint, it should safely search items without throwing
        assertEquals(1, partyQueueIndexOf(q, pbStale, "id2"))
        assertEquals(listOf("id3"), partyUpcomingAfter(q, pbStale, "id2").map { it.videoId })
    }

    @Test
    fun testPartyQueueMultipleDuplicatesResolution() {
        // [A, B, A, C, A, D]
        // Currently at index 2 (second A)
        val q = PartyQueue(
            seq = 10,
            index = 2,
            items = listOf("A", "B", "A", "C", "A", "D").map { PartyTrack(it) }
        )
        val pb = PartyPlayback(queueSeq = 10, queueIndex = 2)

        // Direct hit on index 2
        assertEquals(2, partyQueueIndexOf(q, pb, "A"))

        // Looking for A from position 3: the next A is at index 4
        val pbAdvance = PartyPlayback(queueSeq = 10, queueIndex = 3)
        assertEquals(4, partyQueueIndexOf(q, pbAdvance, "A"))
    }

    // ── QueueBuilder Stress Tests ────────────────────────────────────────────

    private fun testSong(
        id: String,
        title: String,
        artist: String,
        isVideo: Boolean = false,
        duration: String = "3:30",
        tier: QueueTier = QueueTier.CONTEXT
    ) = Song(
        videoId = id,
        title = title,
        artist = artist,
        durationText = duration,
        thumbnailUrl = "",
        isVideo = isVideo,
        queueTier = tier
    )

    @Test
    fun testQueueBuilderEmptyInputs() {
        val result = QueueBuilder.extend(emptyList(), emptyList(), limit = 10)
        assertTrue(result.isEmpty())

        val resultLimitZero = QueueBuilder.extend(
            listOf(testSong("1", "Song A", "Artist 1")),
            listOf(testSong("2", "Song B", "Artist 2")),
            limit = 0
        )
        assertTrue(resultLimitZero.isEmpty())
    }

    @Test
    fun testQueueBuilderAudioOverVideoPrecedence() {
        val candidates = listOf(
            testSong("vid1", "Kesariya", "Arijit Singh", isVideo = true),
            testSong("audio1", "Kesariya", "Arijit Singh", isVideo = false),
            testSong("vid2", "Deva Deva", "Arijit Singh", isVideo = true),
            testSong("audio2", "Deva Deva", "Arijit Singh", isVideo = false)
        )

        val result = QueueBuilder.extend(emptyList(), candidates, limit = 5)
        // songsOnly should filter out the video versions if non-videos exist
        assertTrue(result.all { !it.isVideo }, "Candidates should prioritize audio cuts over videos")
        assertEquals(2, result.size)
        assertEquals(listOf("audio1", "audio2"), result.map { it.videoId })
    }

    @Test
    fun testQueueBuilderPerArtistCaps() {
        val existing = listOf(testSong("s1", "Seed Track", "Seed Artist"))
        val candidates = listOf(
            // Seed artist tracks (cap = SEED_ARTIST_LIMIT = 4)
            testSong("sa1", "Seed Song 1", "Seed Artist"),
            testSong("sa2", "Seed Song 2", "Seed Artist"),
            testSong("sa3", "Seed Song 3", "Seed Artist"),
            testSong("sa4", "Seed Song 4", "Seed Artist"),
            testSong("sa5", "Seed Song 5", "Seed Artist"), // Exceeds cap 4
            // Other artist tracks (cap = PER_ARTIST_LIMIT = 2)
            testSong("oa1", "Other Song 1", "Other Artist"),
            testSong("oa2", "Other Song 2", "Other Artist"),
            testSong("oa3", "Other Song 3", "Other Artist")  // Exceeds cap 2
        )

        val result = QueueBuilder.extend(existing, candidates, limit = 20)
        val seedCount = result.count { it.artist == "Seed Artist" }
        val otherCount = result.count { it.artist == "Other Artist" }

        assertEquals(4, seedCount, "Seed artist exceeded SEED_ARTIST_LIMIT (4)")
        assertEquals(2, otherCount, "Other artist exceeded PER_ARTIST_LIMIT (2)")
    }

    @Test
    fun testQueueBuilderSameRecordingDetection() {
        val song1 = testSong("v1", "Starboy (Official Music Video)", "The Weeknd ft. Daft Punk")
        val song2 = testSong("v2", "Starboy", "The Weeknd feat Daft Punk")
        val song3 = testSong("v3", "Starboy (Remix)", "The Weeknd")

        assertTrue(QueueBuilder.isSameRecording(song1, song2), "Failed to detect identical recording across packaging")
        assertFalse(QueueBuilder.isSameRecording(song1, song3), "Remix was falsely detected as same recording")
    }

    // ── TrackMatcher Stress Tests ────────────────────────────────────────────

    @Test
    fun testTrackMatcherDurationParsing() {
        assertEquals(210, TrackMatcher.secondsOf("3:30"))
        assertEquals(3665, TrackMatcher.secondsOf("1:01:05"))
        assertEquals(59, TrackMatcher.secondsOf("0:59"))
        assertNull(TrackMatcher.secondsOf("invalid"))
        assertNull(TrackMatcher.secondsOf(""))
        assertNull(TrackMatcher.secondsOf(null))
        assertNull(TrackMatcher.secondsOf("1:2:3:4"))
    }

    @Test
    fun testTrackMatcherKanaTransliteration() {
        assertEquals("koikogare", TrackMatcher.romajiOf("コイコガレ"))
        assertEquals("koikogare", TrackMatcher.romajiOf("こいこがれ"))
        assertEquals("shinjuku", TrackMatcher.romajiOf("新宿").let { TrackMatcher.romajiOf("しんじゅく") })
        assertEquals("toukyou", TrackMatcher.romajiOf("とうきょう"))
    }
}
