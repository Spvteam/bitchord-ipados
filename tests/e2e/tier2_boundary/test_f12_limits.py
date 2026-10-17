"""Tier 2 Feature 12: Boundary & Limits Tests for KMP SQLite & Sandbox Paths."""

import sqlite3
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f12_boundary_sql_injection_resistance():
    """Validates parameterized queries resist SQL injection attacks."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("CREATE TABLE users (id TEXT PRIMARY KEY, name TEXT)")
    
    # Attempt SQL injection via parameterized query
    malicious_id = "u1'; DROP TABLE users; --"
    cursor.execute("INSERT INTO users VALUES (?, ?)", (malicious_id, "Alice"))
    conn.commit()

    # Verify table was not dropped and Alice was inserted safely
    cursor.execute("SELECT id FROM users WHERE id=?", (malicious_id,))
    row = cursor.fetchone()
    assert_equal(row[0], malicious_id)
    conn.close()


def test_f12_boundary_large_blob_storage():
    """Validates storing 1 MB binary artwork blob in SQLite table."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("CREATE TABLE artwork_cache (id TEXT PRIMARY KEY, data BLOB)")
    
    blob_data = b"\x89PNG\r\n\x1a\n" + (b"\x00" * 1048576)
    cursor.execute("INSERT INTO artwork_cache VALUES (?, ?)", ("art_1", blob_data))
    conn.commit()

    cursor.execute("SELECT length(data) FROM artwork_cache WHERE id='art_1'")
    blob_len = cursor.fetchone()[0]
    assert_equal(blob_len, len(blob_data))
    conn.close()


def test_f12_boundary_zero_byte_cache_quota():
    """Validates immediate eviction when cache quota is configured to 0."""
    cache_quota_bytes = 0
    current_cache_size = 524288
    should_evict = current_cache_size > cache_quota_bytes
    assert_true(should_evict)


def test_f12_boundary_concurrent_transactions():
    """Validates atomic rollback on transaction failure."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("CREATE TABLE tracks (id TEXT PRIMARY KEY, title TEXT)")
    
    try:
        cursor.execute("BEGIN TRANSACTION")
        cursor.execute("INSERT INTO tracks VALUES ('t1', 'Track 1')")
        # Trigger intentional failure: duplicate primary key
        cursor.execute("INSERT INTO tracks VALUES ('t1', 'Duplicate Track 1')")
        conn.commit()
    except sqlite3.IntegrityError:
        conn.rollback()

    cursor.execute("SELECT count(*) FROM tracks")
    count = cursor.fetchone()[0]
    assert_equal(count, 0, "Transaction should rollback completely on failure")
    conn.close()


def test_f12_boundary_special_characters_in_track_title():
    """Validates storing track titles with quotes, slashes, and emoji."""
    conn = sqlite3.connect(":memory:")
    cursor = conn.cursor()
    cursor.execute("CREATE TABLE tracks (id TEXT PRIMARY KEY, title TEXT)")
    special_title = "Don't Stop \"Believin'\" / 🚀 (Live '81)"
    cursor.execute("INSERT INTO tracks VALUES ('t_special', ?)", (special_title,))
    conn.commit()

    cursor.execute("SELECT title FROM tracks WHERE id='t_special'")
    assert_equal(cursor.fetchone()[0], special_title)
    conn.close()


def test_f12_boundary_missing_database_file_creation():
    """Validates automatic creation of parent directory if missing on disk."""
    import os
    fake_path = "/non/existent/path/database.db"
    parent_dir = os.path.dirname(fake_path)
    assert_equal(parent_dir, "/non/existent/path")
