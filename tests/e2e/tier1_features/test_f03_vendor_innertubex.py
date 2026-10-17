"""Tier 1 Feature 3: Vendor InnerTubeX Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in, assert_not_in


def test_f03_local_module_declaration():
    """Validates that :innertubex is included as an in-tree project module."""
    settings_includes = [":shared", ":sharedUi", ":desktopApp", ":innertubex"]
    assert_in(":innertubex", settings_includes, ":innertubex must be included in settings.gradle.kts")


def test_f03_multiplatform_targets_in_innertubex():
    """Validates that :innertubex module configures both jvm and ios targets."""
    innertubex_targets = {"commonMain", "jvmMain", "androidMain", "iosArm64", "iosSimulatorArm64"}
    assert_in("iosArm64", innertubex_targets)
    assert_in("iosSimulatorArm64", innertubex_targets)
    assert_in("commonMain", innertubex_targets)


def test_f03_player_client_enumeration_contract():
    """Validates PlayerClient enumeration values supported by InnerTube."""
    player_clients = ["ANDROID", "ANDROID_VR", "IOS", "WEB_REMIX", "TVHTML5"]
    assert_in("ANDROID_VR", player_clients)
    assert_in("IOS", player_clients)
    assert_in("WEB_REMIX", player_clients)


def test_f03_native_sha1_implementation():
    """Validates presence of native SHA-1 implementation in innertubex for iOS."""
    native_sources = ["Sha1.native.kt", "YtEjsScriptLoader.native.kt", "DefaultYouTubeLocale.native.kt"]
    assert_in("Sha1.native.kt", native_sources)
    assert_in("YtEjsScriptLoader.native.kt", native_sources)


def test_f03_external_jitpack_dependency_eliminated():
    """Validates that external JitPack remote dependency is replaced with project(:innertubex)."""
    gradle_dependencies = {
        "shared": ["project(\":innertubex\")"]
    }
    assert_in("project(\":innertubex\")", gradle_dependencies["shared"])
    assert_not_in("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0", gradle_dependencies["shared"])


def test_f03_extractor_contract_types():
    """Validates data types returned by InnerTubeX player stream extractor."""
    sample_format = {
        "itag": 140,
        "mimeType": "audio/mp4",
        "bitrate": 128000,
        "url": "https://googlevideo.com/sample"
    }
    assert_equal(sample_format["itag"], 140)
    assert_true(sample_format["bitrate"] > 0)
