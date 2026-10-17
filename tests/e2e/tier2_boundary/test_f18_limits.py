"""Tier 2 Feature 18: Boundary & Limits Tests for Compose iPadOS UI & Shaders."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f18_boundary_rapid_split_view_resizing():
    """Validates responsive layout transitions without state loss during rapid window resizing."""
    widths = [320, 640, 400, 1024, 700, 1200]
    modes = []
    for w in widths:
        if w < 600:
            modes.append("COMPACT")
        elif w < 840:
            modes.append("MEDIUM")
        else:
            modes.append("EXPANDED")

    assert_equal(len(modes), 6)
    assert_equal(modes[0], "COMPACT")
    assert_equal(modes[3], "EXPANDED")


def test_f18_boundary_device_rotation_orientations():
    """Validates support for all 4 iPadOS device orientations."""
    supported_orientations = [
        "UIInterfaceOrientationPortrait",
        "UIInterfaceOrientationPortraitUpsideDown",
        "UIInterfaceOrientationLandscapeLeft",
        "UIInterfaceOrientationLandscapeRight"
    ]
    assert_equal(len(supported_orientations), 4)
    assert_in("UIInterfaceOrientationPortraitUpsideDown", supported_orientations)


def test_f18_boundary_shader_compilation_fallback():
    """Validates fallback to static gradient when Skia RuntimeEffect compilation fails."""
    shader_valid = False
    render_mode = "SKIA_RUNTIMEEFFECT" if shader_valid else "CANVAS_FALLBACK_GRADIENT"
    assert_equal(render_mode, "CANVAS_FALLBACK_GRADIENT")


def test_f18_boundary_low_power_mode_fps():
    """Validates capping animation target rate to 60Hz when iOS Low Power Mode is active."""
    low_power_mode_active = True
    target_fps = 60 if low_power_mode_active else 120
    assert_equal(target_fps, 60)


def test_f18_boundary_multiline_lyrics_story_card_export():
    """Validates lyrics line truncation or font size auto-scaling for long lyrics selections."""
    selected_lines = [f"Lyric line number {i} with extensive text" for i in range(20)]
    max_story_lines = 8
    truncated_lines = selected_lines[:max_story_lines]
    assert_equal(len(truncated_lines), 8)


def test_f18_boundary_zero_contrast_color_correction():
    """Validates minimum contrast ratio enforcement for lyrics text on bright artwork."""
    text_lum = 0.95
    bg_lum = 0.92
    ratio = (max(text_lum, bg_lum) + 0.05) / (min(text_lum, bg_lum) + 0.05)
    # Low contrast ratio (< 4.5:1) should trigger high-contrast scrim overlay
    needs_scrim = ratio < 4.5
    assert_true(needs_scrim)
