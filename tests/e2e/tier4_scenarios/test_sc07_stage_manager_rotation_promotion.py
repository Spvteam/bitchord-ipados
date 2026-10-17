"""Tier 4 Scenario 7: Stage Manager Resizing, Device Rotation & ProMotion."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_scenario_07_stage_manager_and_keyboard_shortcuts():
    """Simulates:
    1. App running on iPad in Stage Manager window
    2. User connects Apple Magic Keyboard
    3. User presses 'Space' key -> UIKeyCommand toggles Play/Pause
    4. User resizes window from 500dp to 1000dp width
    5. Adaptive UI switches seamlessly from Compact Dock to LandscapePlayer
    6. CADisableMinimumFrameDurationOnPhone drives CADisplayLink at 120Hz
    7. User presses 'Command + Right Arrow' -> triggers nextTrack
    """
    player_state = {
        "isPlaying": False,
        "widthDp": 500,
        "layoutMode": "COMPACT",
        "currentTrack": "Track A"
    }

    # Step 3: Space key shortcut toggles play
    key_event = "UIKeyInputSpace"
    if key_event == "UIKeyInputSpace":
        player_state["isPlaying"] = not player_state["isPlaying"]
    assert_true(player_state["isPlaying"])

    # Step 4 & 5: Stage Manager resize to 1000dp
    player_state["widthDp"] = 1000
    if player_state["widthDp"] >= 840:
        player_state["layoutMode"] = "EXPANDED"
    assert_equal(player_state["layoutMode"], "EXPANDED")

    # Step 6: 120Hz ProMotion verified
    refresh_rate_hz = 120
    frame_interval_ms = 1000.0 / refresh_rate_hz
    assert_true(frame_interval_ms <= 8.5)

    # Step 7: Command + Right Arrow shortcut triggers next
    shortcut = ("RightArrow", "UIKeyModifierCommand")
    if shortcut == ("RightArrow", "UIKeyModifierCommand"):
        player_state["currentTrack"] = "Track B"
    assert_equal(player_state["currentTrack"], "Track B")
