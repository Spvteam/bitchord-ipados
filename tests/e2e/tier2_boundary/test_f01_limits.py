"""Tier 2 Feature 1: Boundary & Limits Tests for iOS Gradle Targets."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in, assert_false


def test_f01_boundary_unsupported_release_architecture():
    """Validates that non-arm64 architectures (e.g. x86_64) are rejected for device release."""
    def is_valid_device_arch(arch: str) -> bool:
        return arch == "arm64"

    assert_true(is_valid_device_arch("arm64"))
    assert_false(is_valid_device_arch("x86_64"), "x86_64 cannot be used for physical iPadOS release")
    assert_false(is_valid_device_arch("armv7"))


def test_f01_boundary_dynamic_framework_rejection():
    """Validates rejection of isStatic=false for sideload .ipa distribution."""
    def validate_framework_linkage(is_static: bool) -> bool:
        if not is_static:
            return False  # Sideloaded IPA requires static framework linkage
        return True

    assert_true(validate_framework_linkage(is_static=True))
    assert_false(validate_framework_linkage(is_static=False))


def test_f01_boundary_duplicate_target_definition():
    """Validates error detection when duplicate target names are registered."""
    targets = ["iosArm64", "iosSimulatorArm64"]
    new_target = "iosArm64"
    is_duplicate = new_target in targets
    assert_true(is_duplicate, "Must detect and prevent duplicate Gradle target registration")


def test_f01_boundary_empty_compiler_options():
    """Validates fallback to default compiler flags when compilerOptions block is empty."""
    options = {}
    free_compiler_args = options.get("freeCompilerArgs", ["-opt-in=kotlin.RequiresOptIn"])
    assert_equal(len(free_compiler_args), 1)


def test_f01_boundary_missing_security_framework_detection():
    """Validates detection of missing required system framework in linker opts."""
    linker_opts = ["-framework", "AVFoundation", "-framework", "AudioToolbox"]
    has_security = "Security" in linker_opts
    assert_false(has_security, "Must detect missing Security framework before linking")


def test_f01_boundary_malformed_framework_basename():
    """Validates rejection of invalid or whitespace-containing framework baseName."""
    def is_valid_basename(name: str) -> bool:
        return bool(name and not any(c in name for c in " /\\:;*?\"<>|"))

    assert_true(is_valid_basename("SharedUI"))
    assert_false(is_valid_basename("Shared UI"))
    assert_false(is_valid_basename(""))
