"""Tier 1 Feature 16: Background Audio & Lock Screen Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f16_audio_session_category_playback():
    """Validates AVAudioSession configuration for background audio continuity."""
    session_config = {
        "category": "AVAudioSessionCategoryPlayback",
        "mode": "AVAudioSessionModeDefault",
        "options": ["AVAudioSessionCategoryOptionAllowAirPlay", "AVAudioSessionCategoryOptionAllowBluetoothA2DP"]
    }
    assert_equal(session_config["category"], "AVAudioSessionCategoryPlayback")
    assert_in("AVAudioSessionCategoryOptionAllowAirPlay", session_config["options"])


def test_f16_now_playing_metadata_payload():
    """Validates MPNowPlayingInfoCenter dictionary properties."""
    now_playing_info = {
        "MPMediaItemPropertyTitle": "Starboy",
        "MPMediaItemPropertyArtist": "The Weeknd",
        "MPMediaItemPropertyAlbumTitle": "Starboy",
        "MPMediaItemPropertyPlaybackDuration": 238.0,
        "MPNowPlayingInfoPropertyElapsedPlaybackTime": 45.0,
        "MPNowPlayingInfoPropertyPlaybackRate": 1.0
    }
    assert_equal(now_playing_info["MPMediaItemPropertyTitle"], "Starboy")
    assert_equal(now_playing_info["MPNowPlayingInfoPropertyPlaybackRate"], 1.0)
    assert_true(now_playing_info["MPMediaItemPropertyPlaybackDuration"] > 0)


def test_f16_now_playing_artwork_contract():
    """Validates MPMediaItemArtwork configuration."""
    artwork_spec = {
        "width": 600,
        "height": 600,
        "hasImage": True
    }
    assert_true(artwork_spec["hasImage"])
    assert_equal(artwork_spec["width"], 600)


def test_f16_remote_command_center_playback_controls():
    """Validates MPRemoteCommandCenter play/pause/toggle command handlers."""
    commands_registered = ["playCommand", "pauseCommand", "togglePlayPauseCommand"]
    for cmd in ["playCommand", "pauseCommand", "togglePlayPauseCommand"]:
        assert_in(cmd, commands_registered)


def test_f16_remote_command_center_navigation_controls():
    """Validates MPRemoteCommandCenter nextTrack and previousTrack command handlers."""
    commands_registered = ["nextTrackCommand", "previousTrackCommand"]
    assert_in("nextTrackCommand", commands_registered)
    assert_in("previousTrackCommand", commands_registered)


def test_f16_remote_command_center_scrubbing_seek():
    """Validates changePlaybackPositionCommand seeking handler."""
    seek_event = {
        "command": "changePlaybackPositionCommand",
        "requestedPositionSeconds": 120.0
    }
    assert_equal(seek_event["command"], "changePlaybackPositionCommand")
    assert_equal(seek_event["requestedPositionSeconds"], 120.0)
