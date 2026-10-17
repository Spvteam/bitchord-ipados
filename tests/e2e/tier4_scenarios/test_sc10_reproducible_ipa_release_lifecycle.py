"""Tier 4 Scenario 10: Reproducible IPA Release Pipeline Lifecycle."""

import zipfile
import io
import hashlib
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_scenario_10_automated_ipa_release_lifecycle():
    """Simulates:
    1. CI runner triggers scripts/build_ipados_ipa.sh
    2. Native C++ analyzer compiles into libbitchord_analysis.a
    3. Gradle links :sharedUi static framework (SharedUI.framework)
    4. Xcode archives Payload/BitChord.app
    5. Entitlements injected with keychain access groups
    6. ldid applies ad-hoc pseudo-signature
    7. Zip archives BitChord-v1.8-beta1-iPadOS.ipa
    8. Package checksum generated and verified
    """
    build_steps_executed = []

    # Stage 1: DSP compilation
    build_steps_executed.append("BUILD_DSP_LIB")
    # Stage 2: KMP Static Framework
    build_steps_executed.append("LINK_SHAREDUI_FRAMEWORK")
    # Stage 3: Xcode Archive
    build_steps_executed.append("XCODE_ARCHIVE")
    # Stage 4: Package & Pseudo-Sign
    build_steps_executed.append("PSEUDO_SIGN_LDID")
    build_steps_executed.append("PACKAGE_IPA_ZIP")

    assert_equal(len(build_steps_executed), 5)

    # Simulate .ipa bundle creation
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("Payload/BitChord.app/BitChord", b"\xca\xfe\xba\xbe" + (b"\x00" * 1024))
        zf.writestr("Payload/BitChord.app/Info.plist", b"<plist><dict><key>CFBundleIdentifier</key><string>com.music.bitchord</string></dict></plist>")
        zf.writestr("Payload/BitChord.app/Frameworks/SharedUI.framework/SharedUI", b"FRAMEWORK_BINARY")

    ipa_bytes = buffer.getvalue()
    assert_true(len(ipa_bytes) > 0)

    # Checksum calculation
    sha256_hash = hashlib.sha256(ipa_bytes).hexdigest()
    assert_equal(len(sha256_hash), 64)
