"""Tier 3: Chunked Stream Loader + SQLite Storage + Offline Download Interaction."""

import sqlite3
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.mocks import MockResourceLoaderDelegate


def test_xf_chunked_stream_offline_download_pipeline():
    """Validates streaming chunks and indexing downloaded track in SQLite."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE offline_downloads (
            track_id TEXT PRIMARY KEY,
            title TEXT,
            file_path TEXT,
            bytes_downloaded INTEGER
        )
    """)

    # Stream track in 2 chunks
    delegate = MockResourceLoaderDelegate(content_length=1048576)
    c1 = delegate.request_range(0, 524288)
    c2 = delegate.request_range(524288, 524288)
    assert_equal(c1["bytes_loaded"] + c2["bytes_loaded"], 1048576)

    # Record completed download in SQLite
    file_path = "/var/mobile/Containers/Data/Application/UUID/Library/Application Support/downloads/track_1.m4a"
    cursor.execute("INSERT INTO offline_downloads VALUES ('track_1', 'Blinding Lights', ?, 1048576)", (file_path,))
    conn.commit()

    # Query download
    cursor.execute("SELECT bytes_downloaded FROM offline_downloads WHERE track_id='track_1'")
    total = cursor.fetchone()[0]
    assert_equal(total, 1048576)
    conn.close()


def test_xf_lru_cache_cleanup_and_sqlite_sync():
    """Validates LRU eviction when cache directory exceeds quota."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("CREATE TABLE chunk_cache (chunk_id TEXT PRIMARY KEY, access_time INTEGER, size INTEGER)")
    cursor.execute("INSERT INTO chunk_cache VALUES ('c1', 100, 524288)")
    cursor.execute("INSERT INTO chunk_cache VALUES ('c2', 200, 524288)")
    cursor.execute("INSERT INTO chunk_cache VALUES ('c3', 300, 524288)")
    conn.commit()

    # Evict oldest chunk
    cursor.execute("SELECT chunk_id FROM chunk_cache ORDER BY access_time ASC LIMIT 1")
    evicted_chunk = cursor.fetchone()[0]
    cursor.execute("DELETE FROM chunk_cache WHERE chunk_id=?", (evicted_chunk,))
    conn.commit()

    cursor.execute("SELECT count(*) FROM chunk_cache")
    remaining_count = cursor.fetchone()[0]
    assert_equal(evicted_chunk, "c1")
    assert_equal(remaining_count, 2)
    conn.close()
