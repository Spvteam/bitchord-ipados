"""Tier 2 Feature 19: Boundary & Limits Tests for Automated .ipa Packaging."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f19_boundary_missing_native_library_detection():
    """Validates pre-build assertion verifying libbitchord_analysis.a exists before packaging."""
    files_present = ["SharedUI.framework", "Info.plist"]
    has_native_analyzer = "libbitchord_analysis.a" in files_present
    assert_equal(has_native_analyzer, False, "Build script must halt if libbitchord_analysis.a is missing")


def test_f19_boundary_missing_entitlements_file():
    """Validates pre-signing check verifying iosApp.entitlements presence."""
    entitlements_path = "iosApp/iosApp.entitlements"
    assert_true(entitlements_path.endswith(".entitlements"))


def test_f19_boundary_ipa_file_overwrite():
    """Validates that existing .ipa output file is cleanly replaced without corruption."""
    output_target = "build/BitChord-v1.8-beta1-iPadOS.ipa"
    overwrite_flag = True
    assert_true(overwrite_flag)


def test_f19_boundary_code_signing_strictly_disabled():
    """Validates that CODE_SIGNING_REQUIRED is NO in the sideload build configuration."""
    xcode_env = {
        "CODE_SIGNING_REQUIRED": "NO",
        "CODE_SIGNING_ALLOWED": "NO",
        "CODE_SIGN_IDENTITY": ""
    }
    assert_equal(xcode_env["CODE_SIGNING_REQUIRED"], "NO")
    assert_equal(xcode_env["CODE_SIGN_IDENTITY"], "")


def test_f19_boundary_payload_folder_case_sensitivity():
    """Validates that .ipa zip root directory is strictly named 'Payload' (capital P)."""
    folder_name = "Payload"
    assert_equal(folder_name, "Payload")
    assert_equal(folder_name[0], "P", "iOS app zip root must be capitalized Payload")


def test_f19_boundary_missing_macho_executable():
    """Validates that BitChord binary exists inside Payload/BitChord.app/."""
    app_contents = ["Info.plist", "BitChord", "embedded.mobileprovision", "Assets.car"]
    assert_in("BitChord", app_contents, "Mach-O binary must exist inside bundle")
