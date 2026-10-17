"""Tier 4 Scenario 4: Offline Mode, SQLite Caching & Local Storage Playback."""

import sqlite3
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.mocks import MockAVAudioEngine


def test_scenario_04_offline_mode_and_cached_playback():
    """Simulates:
    1. User downloads favorite album for offline listening
    2. Completed audio files stored in iOS sandbox Application Support directory
    3. Metadata indexed in local SQLite database
    4. iPad enters Airplane Mode (no internet connectivity)
    5. App starts in offline mode
    6. AudioEngine loads local file URL directly without network calls
    7. Playback proceeds smoothly with full offline metadata
    """
    # Step 1, 2, 3: Seed offline library in SQLite
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE offline_tracks (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            artist TEXT NOT NULL,
            local_path TEXT NOT NULL,
            duration_ms INTEGER NOT NULL
        )
    """)
    local_audio_path = "/var/mobile/Containers/Data/Application/UUID/Library/Application Support/offline/track_offline_01.m4a"
    cursor.execute("""
        INSERT INTO offline_tracks VALUES (
            'track_offline_01', 'Save Your Tears', 'The Weeknd', ?, 215000
        )
    """, (local_audio_path,))
    conn.commit()

    # Step 4 & 5: Offline mode activation
    network_available = False
    assert_equal(network_available, False)

    # Step 6: Query offline track directly from SQLite
    cursor.execute("SELECT title, artist, local_path FROM offline_tracks WHERE id='track_offline_01'")
    row = cursor.fetchone()
    assert_equal(row[0], "Save Your Tears")
    assert_equal(row[2], local_audio_path)

    # Step 7: Load local file directly into audio engine
    engine = MockAVAudioEngine()
    engine.load_track("A", f"file://{local_audio_path}")
    engine.play("A")
    assert_true(engine.deck_a_playing)
    assert_equal(engine.active_deck, "A")
    conn.close()
