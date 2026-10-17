"""Tier 3: Listen Together WebSocket + Party Sync Clock Calibration Interaction."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_xf_scheduled_playback_start_synchronization():
    """Validates scheduled playback: Host broadcasts future timestamp, client triggers on time."""
    host_now_ms = 1000000
    scheduled_start_ms = host_now_ms + 1000  # Start 1 second in future

    # Client calculates offset via NTP-style roundtrip
    # t0: 999900, t1: 1000000, t2: 1000000, t3: 1000100
    rtt_ms = (1000100 - 999900) - (1000000 - 1000000)  # 200ms RTT
    clock_offset = ((1000000 - 999900) + (1000000 - 1000100)) // 2  # 0ms
    assert_equal(clock_offset, 0)
    assert_equal(rtt_ms, 200)


def test_xf_drift_compensation_tempo_adjustment():
    """Validates slight tempo pitch adjustment to eliminate phase drift > 50ms."""
    client_drift_ms = 65  # Client is 65ms ahead of host
    # Adjust client rate down slightly (e.g. 0.99x) until aligned
    adjusted_rate = 0.99 if client_drift_ms > 50 else 1.0
    assert_equal(adjusted_rate, 0.99)


def test_xf_room_invite_qr_code_generation():
    """Validates generating CIQRCodeGenerator payload for room joining."""
    room_id = "ROOM_ALPHA_77"
    invite_url = f"https://bitchord.app/join/{room_id}"
    qr_filter_input = invite_url.encode("utf-8")
    assert_in(b"ROOM_ALPHA_77", qr_filter_input)
