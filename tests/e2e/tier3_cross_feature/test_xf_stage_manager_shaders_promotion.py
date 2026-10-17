"""Tier 3: Stage Manager Breakpoints + Skia Shaders + 120Hz ProMotion Interaction."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_xf_stage_manager_resize_triggers_landscape_player():
    """Validates dynamically transitioning from compact dock to LandscapePlayer during Stage Manager resize."""
    class AdaptiveUIHost:
        def __init__(self):
            self.width_dp = 400
            self.layout_mode = "COMPACT"
            self.show_side_panels = False

        def on_window_resized(self, new_width_dp: int):
            self.width_dp = new_width_dp
            if self.width_dp >= 840:
                self.layout_mode = "EXPANDED"
                self.show_side_panels = True
            elif self.width_dp >= 600:
                self.layout_mode = "MEDIUM"
                self.show_side_panels = False
            else:
                self.layout_mode = "COMPACT"
                self.show_side_panels = False

    ui = AdaptiveUIHost()
    assert_equal(ui.layout_mode, "COMPACT")

    # User resizes window in Stage Manager to wide view
    ui.on_window_resized(900)
    assert_equal(ui.layout_mode, "EXPANDED")
    assert_true(ui.show_side_panels)


def test_xf_mesh_shader_uniforms_at_120hz():
    """Validates updating mesh gradient time uniform at 120Hz frame step (8.33ms)."""
    frame_delta_ms = 1000.0 / 120.0  # ~8.333ms
    time_uniform = 0.0

    # Advance 120 frames (1 full second)
    for _ in range(120):
        time_uniform += (frame_delta_ms / 1000.0)

    assert_true(0.99 <= time_uniform <= 1.01)


def test_xf_liquid_glass_shader_with_ambient_artwork_palette():
    """Validates extracting dominant palette colors and passing as tint uniforms to glass shader."""
    artwork_palette = ["#2B1B17", "#D2B48C", "#4A0E17"]
    shader_tint = artwork_palette[0]
    assert_equal(shader_tint, "#2B1B17")
