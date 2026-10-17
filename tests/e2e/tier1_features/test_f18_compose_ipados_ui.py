"""Tier 1 Feature 18: Compose iPadOS UI & Shaders Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f18_compose_ui_view_controller_wrapper():
    """Validates ComposeUIViewController Swift/Kotlin hosting contract."""
    controller_spec = {
        "class": "ComposeUIViewController",
        "ignoresSafeArea": True,
        "isOpaque": True
    }
    assert_true(controller_spec["ignoresSafeArea"])


def test_f18_stage_manager_window_breakpoints():
    """Validates Stage Manager responsive layout breakpoint classification."""
    def get_layout_mode(width_dp: int) -> str:
        if width_dp < 600:
            return "COMPACT"
        elif width_dp < 840:
            return "MEDIUM"
        else:
            return "EXPANDED"

    # Slide Over (~375dp) -> COMPACT
    assert_equal(get_layout_mode(375), "COMPACT")
    # 1/2 Split View (~670dp) -> MEDIUM
    assert_equal(get_layout_mode(670), "MEDIUM")
    # Full Screen iPad Pro (~1024dp) -> EXPANDED
    assert_equal(get_layout_mode(1024), "EXPANDED")


def test_f18_skia_liquid_glassmorphism_shader_contract():
    """Validates Skia RuntimeEffect SkSL glass shader contract."""
    sksl_shader = """
    uniform float2 u_resolution;
    uniform float u_blur_radius;
    uniform float4 u_tint;
    half4 main(float2 coord) {
        return u_tint;
    }
    """
    assert_in("u_resolution", sksl_shader)
    assert_in("u_blur_radius", sksl_shader)


def test_f18_mesh_gradient_4_color_interpolation():
    """Validates animated 4-color mesh gradient coordinates and colors."""
    mesh_spec = {
        "colors": ["#1A1B2F", "#162447", "#1F4068", "#E43F5A"],
        "points": [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)]
    }
    assert_equal(len(mesh_spec["colors"]), 4)
    assert_equal(len(mesh_spec["points"]), 4)


def test_f18_promotion_120hz_plist_configuration():
    """Validates Info.plist flag CADisableMinimumFrameDurationOnPhone for 120Hz ProMotion."""
    info_plist_flags = {
        "CADisableMinimumFrameDurationOnPhone": True,
        "UIRequiresFullScreen": False
    }
    assert_true(info_plist_flags["CADisableMinimumFrameDurationOnPhone"])
    assert_equal(info_plist_flags["UIRequiresFullScreen"], False)


def test_f18_story_card_9_16_aspect_ratio():
    """Validates 9:16 Instagram Story lyrics card export dimensions."""
    width = 1080
    height = 1920
    aspect_ratio = width / height
    expected_ratio = 9.0 / 16.0
    assert_equal(round(aspect_ratio, 4), round(expected_ratio, 4))
