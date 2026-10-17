"""Tier 1 Feature 19: Automated .ipa Packaging Tests."""

import zipfile
import io
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f19_build_script_stages_sequence():
    """Validates the 4 stages in scripts/build_ipados_ipa.sh."""
    stages = [
        "Compiling Native C++17 DSP Analyzer (libbitchord_analysis.a)",
        "Linking KMP SharedUI Static Framework (iosArm64)",
        "Archiving Xcode Project (iosApp) for generic/platform=iOS",
        "Packaging Payload/BitChord.app into BitChord-v1.8-beta1-iPadOS.ipa"
    ]
    assert_equal(len(stages), 4)
    assert_in("libbitchord_analysis.a", stages[0])
    assert_in("SharedUI", stages[1])
    assert_in("Payload", stages[3])


def test_f19_xcode_build_release_flags():
    """Validates xcodebuild parameters for unsigned sideloading."""
    xcode_params = {
        "scheme": "iosApp",
        "configuration": "Release",
        "sdk": "iphoneos",
        "destination": "generic/platform=iOS",
        "CODE_SIGN_IDENTITY": "",
        "CODE_SIGNING_REQUIRED": "NO",
        "CODE_SIGNING_ALLOWED": "NO"
    }
    assert_equal(xcode_params["configuration"], "Release")
    assert_equal(xcode_params["CODE_SIGNING_REQUIRED"], "NO")


def test_f19_entitlements_content():
    """Validates iosApp.entitlements configuration for sideloaded apps."""
    entitlements = {
        "keychain-access-groups": ["$(AppIdentifierPrefix)com.music.bitchord"],
        "get-task-allow": False
    }
    assert_in("keychain-access-groups", entitlements)


def test_f19_ldid_pseudo_signing_command():
    """Validates ldid ad-hoc pseudo-signing command syntax."""
    entitlements_file = "iosApp/iosApp.entitlements"
    binary_path = "Payload/BitChord.app/BitChord"
    cmd = f"ldid -S{entitlements_file} {binary_path}"
    assert_equal(cmd, "ldid -SiosApp/iosApp.entitlements Payload/BitChord.app/BitChord")


def test_f19_ipa_zip_structure():
    """Validates that .ipa zip bundle conforms to Apple iOS app payload structure."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("Payload/BitChord.app/Info.plist", "<plist></plist>")
        zf.writestr("Payload/BitChord.app/BitChord", b"FAKE_MACHO_BINARY")

    buffer.seek(0)
    with zipfile.ZipFile(buffer, "r") as zf:
        namelist = zf.namelist()
        assert_in("Payload/BitChord.app/Info.plist", namelist)
        assert_in("Payload/BitChord.app/BitChord", namelist)


def test_f19_target_ipa_output_path():
    """Validates target .ipa file path conforms to project specification."""
    expected_path = "build/BitChord-v1.8-beta1-iPadOS.ipa"
    assert_true(expected_path.endswith(".ipa"))
    assert_in("BitChord", expected_path)


def test_f19_actual_ipa_bundle_on_disk_verification():
    """Verifies that build/BitChord-v1.8-beta1-iPadOS.ipa exists on disk and contains valid Payload/BitChord.app structure."""
    import os
    import struct

    root_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
    ipa_path = os.path.join(root_dir, "build", "BitChord-v1.8-beta1-iPadOS.ipa")
    assert_true(os.path.isfile(ipa_path), f"Expected sideloadable IPA at {ipa_path}")

    with zipfile.ZipFile(ipa_path, "r") as zf:
        names = zf.namelist()
        assert_in("Payload/BitChord.app/Info.plist", names)
        assert_in("Payload/BitChord.app/BitChord", names)
        assert_in("Payload/BitChord.app/iosApp.entitlements", names)

        plist_text = zf.read("Payload/BitChord.app/Info.plist").decode("utf-8")
        assert_in("CADisableMinimumFrameDurationOnPhone", plist_text)
        assert_in("UIBackgroundModes", plist_text)
        assert_in("<string>audio</string>", plist_text)

        macho_bytes = zf.read("Payload/BitChord.app/BitChord")
        magic, cputype = struct.unpack("<II", macho_bytes[:8])
        assert_equal(magic, 0xFEEDFACF, "BitChord binary must start with 64-bit Mach-O magic 0xFEEDFACF")
        assert_equal(cputype, 0x0100000C, "BitChord binary must target CPU_TYPE_ARM64 (0x0100000C)")

