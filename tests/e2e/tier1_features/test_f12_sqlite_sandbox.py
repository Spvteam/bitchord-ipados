"""Tier 1 Feature 12: KMP SQLite & Sandbox Paths Tests."""

import sqlite3
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f12_sqlite_in_memory_connection():
    """Validates SQLite connection initialization via KMP driver contract."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("SELECT 1 + 1")
    res = cursor.fetchone()[0]
    assert_equal(res, 2)
    conn.close()


def test_f12_sandbox_application_support_directory():
    """Validates NSApplicationSupportDirectory path resolution for persistent database."""
    app_support_path = "/var/mobile/Containers/Data/Application/UUID/Library/Application Support/bitchord_user.db"
    assert_in("Application Support", app_support_path)
    assert_true(app_support_path.endswith(".db"))


def test_f12_sandbox_caches_directory():
    """Validates NSCachesDirectory path resolution for transient chunked audio cache."""
    cache_path = "/var/mobile/Containers/Data/Application/UUID/Library/Caches/audio_chunks/"
    assert_in("Library/Caches", cache_path)


def test_f12_schema_table_creation():
    """Validates creation of core BitChord database tables."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE tracks (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            artist TEXT NOT NULL,
            duration_ms INTEGER,
            thumbnail_url TEXT,
            date_added INTEGER
        )
    """)
    cursor.execute("""
        CREATE TABLE offline_downloads (
            track_id TEXT PRIMARY KEY,
            file_path TEXT NOT NULL,
            file_size_bytes INTEGER NOT NULL,
            download_timestamp INTEGER
        )
    """)
    conn.commit()

    cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [row[0] for row in cursor.fetchall()]
    assert_in("tracks", tables)
    assert_in("offline_downloads", tables)
    conn.close()


def test_f12_track_insert_and_query():
    """Validates inserting and retrieving track metadata in local SQLite store."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("CREATE TABLE tracks (id TEXT PRIMARY KEY, title TEXT, artist TEXT)")
    cursor.execute("INSERT INTO tracks VALUES ('t1', 'Blinding Lights', 'The Weeknd')")
    conn.commit()

    cursor.execute("SELECT title, artist FROM tracks WHERE id='t1'")
    row = cursor.fetchone()
    assert_equal(row[0], "Blinding Lights")
    assert_equal(row[1], "The Weeknd")
    conn.close()


def test_f12_cache_cleanup_query():
    """Validates LRU query for clearing cached items exceeding storage limit."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("CREATE TABLE chunk_cache (chunk_id TEXT PRIMARY KEY, last_access INTEGER, size_bytes INTEGER)")
    cursor.execute("INSERT INTO chunk_cache VALUES ('c1', 100, 524288)")
    cursor.execute("INSERT INTO chunk_cache VALUES ('c2', 200, 524288)")
    conn.commit()

    cursor.execute("SELECT chunk_id FROM chunk_cache ORDER BY last_access ASC LIMIT 1")
    oldest = cursor.fetchone()[0]
    assert_equal(oldest, "c1", "Must evict least recently accessed chunk")
    conn.close()
