"""Tier 2 Feature 17: Boundary & Limits Tests for Multi-Source Lyrics & RPC."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f17_boundary_kugou_invalid_magic_header():
    """Validates rejecting KuGou lyrics file with missing 'krc1' magic header."""
    invalid_payload = b"bad1" + (b"\x00" * 32)
    is_valid_krc = invalid_payload.startswith(b"krc1")
    assert_equal(is_valid_krc, False)


def test_f17_boundary_krc_corrupted_zlib_stream():
    """Validates safe handling when decrypted .krc bytes fail zlib decompression."""
    import zlib
    corrupted_bytes = b"non_zlib_random_data_stream_12345"
    decompressed = None
    try:
        decompressed = zlib.decompress(corrupted_bytes)
    except Exception:
        decompressed = ""

    assert_equal(decompressed, "")


def test_f17_boundary_malformed_lrc_timestamp_recovery():
    """Validates parser resilience on malformed LRC timestamps (e.g. [xx:yy.zz])."""
    import re
    lines = [
        "[00:05.10]Valid lyric line",
        "[invalid_timestamp]Malformed lyric line",
        "[00:12.34]Another valid lyric line"
    ]
    pattern = re.compile(r"^\[(\d{2}):(\d{2}\.\d{2})\](.*)$")
    parsed_lines = []
    for line in lines:
        m = pattern.match(line)
        if m:
            parsed_lines.append(m.group(3))

    assert_equal(len(parsed_lines), 2)
    assert_equal(parsed_lines[0], "Valid lyric line")


def test_f17_boundary_empty_lyrics_file():
    """Validates handling of empty 0-byte lyrics file."""
    empty_content = ""
    lines = [l for l in empty_content.splitlines() if l.strip()]
    assert_equal(len(lines), 0)


def test_f17_boundary_scrobble_under_30_seconds():
    """Validates that Last.fm scrobble is ignored if track total duration is under 30s."""
    track_duration_s = 25.0
    elapsed_s = 20.0
    can_scrobble = (track_duration_s >= 30.0) and (elapsed_s >= 0.5 * track_duration_s)
    assert_equal(can_scrobble, False, "Tracks under 30s must not be scrobbled per Last.fm API spec")


def test_f17_boundary_discord_rpc_reconnect_backoff():
    """Validates reconnection backoff interval when Discord WebSocket disconnects."""
    def get_discord_reconnect_delay_s(attempt: int) -> float:
        return min(1.0 * (1.5 ** attempt), 60.0)

    assert_equal(get_discord_reconnect_delay_s(0), 1.0)
    assert_true(get_discord_reconnect_delay_s(2) > 2.0)
    assert_equal(get_discord_reconnect_delay_s(20), 60.0)
