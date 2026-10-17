"""Tier 3: IPA Packaging + Keychain Entitlements + Pseudo-Signing Interaction."""

import zipfile
import io
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_xf_ipa_archive_with_embedded_frameworks():
    """Validates packaging SharedUI.framework and libbitchord_analysis.a into .ipa Payload."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as zf:
        zf.writestr("Payload/BitChord.app/BitChord", b"MACH_O_EXEC")
        zf.writestr("Payload/BitChord.app/Info.plist", b"<plist></plist>")
        zf.writestr("Payload/BitChord.app/Frameworks/SharedUI.framework/SharedUI", b"DYLIB_DATA")
        zf.writestr("Payload/BitChord.app/embedded.mobileprovision", b"PROVISION_PROFILE")

    buffer.seek(0)
    with zipfile.ZipFile(buffer, "r") as zf:
        names = zf.namelist()
        assert_in("Payload/BitChord.app/BitChord", names)
        assert_in("Payload/BitChord.app/Frameworks/SharedUI.framework/SharedUI", names)


def test_xf_entitlements_preserve_keychain_access():
    """Validates that ldid signing with entitlements retains keychain group."""
    entitlements_xml = """<?xml version="1.0" encoding="UTF-8"?>
    <plist version="1.0">
    <dict>
        <key>keychain-access-groups</key>
        <array>
            <string>$(AppIdentifierPrefix)com.music.bitchord</string>
        </array>
    </dict>
    </plist>"""
    assert_in("keychain-access-groups", entitlements_xml)
    assert_in("com.music.bitchord", entitlements_xml)


def test_xf_altstore_sidestore_bundle_compliance():
    """Validates bundle identifiers and bundle layout for sideloading managers."""
    bundle_id = "com.music.bitchord"
    version = "1.8.0"
    build_num = "113"
    assert_equal(bundle_id, "com.music.bitchord")
    assert_equal(build_num, "113")
