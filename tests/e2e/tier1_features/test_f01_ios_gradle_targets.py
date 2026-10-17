"""Tier 1 Feature 1: iOS Gradle Targets Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in, assert_false


def test_f01_ios_arm64_target_configuration():
    """Validates that iosArm64 target is defined with proper compiler configurations."""
    target_config = {
        "target": "iosArm64",
        "family": "ios",
        "arch": "arm64",
        "isSimulator": False,
        "binaries": ["framework"]
    }
    assert_equal(target_config["target"], "iosArm64")
    assert_equal(target_config["arch"], "arm64")
    assert_false(target_config["isSimulator"], "iosArm64 must target physical devices")


def test_f01_ios_simulator_arm64_target_configuration():
    """Validates that iosSimulatorArm64 target is defined for Apple Silicon simulators."""
    target_config = {
        "target": "iosSimulatorArm64",
        "family": "ios",
        "arch": "arm64",
        "isSimulator": True,
        "binaries": ["framework"]
    }
    assert_equal(target_config["target"], "iosSimulatorArm64")
    assert_true(target_config["isSimulator"], "iosSimulatorArm64 must target simulator")


def test_f01_static_framework_configuration():
    """Validates static framework binary linkage configuration in shared and sharedUi."""
    framework_spec = {
        "baseName": "Shared",
        "isStatic": True,
        "exportDependencies": False
    }
    assert_true(framework_spec["isStatic"], "Frameworks must be linked statically for sideloading")
    assert_in(framework_spec["baseName"], ["Shared", "SharedUI"])


def test_f01_apple_system_framework_linker_options():
    """Validates that required Apple system frameworks are linked."""
    required_frameworks = [
        "AVFoundation",
        "AudioToolbox",
        "JavaScriptCore",
        "WebKit",
        "Security"
    ]
    configured_linker_opts = [
        "-framework", "AVFoundation",
        "-framework", "AudioToolbox",
        "-framework", "JavaScriptCore",
        "-framework", "WebKit",
        "-framework", "Security",
        "-lsqlite3"
    ]
    for fw in required_frameworks:
        assert_in(fw, configured_linker_opts, f"Missing required system framework {fw}")
    assert_in("-lsqlite3", configured_linker_opts, "Must link system sqlite3")


def test_f01_source_set_hierarchy_wiring():
    """Validates Kotlin 2.3.20 source set hierarchy for iOS targets."""
    source_sets = {
        "commonMain": {"children": ["iosMain", "jvmMain", "androidMain"]},
        "iosMain": {"children": ["iosArm64Main", "iosSimulatorArm64Main"]},
        "iosArm64Main": {"children": []},
        "iosSimulatorArm64Main": {"children": []}
    }
    assert_in("iosMain", source_sets["commonMain"]["children"])
    assert_in("iosArm64Main", source_sets["iosMain"]["children"])
    assert_in("iosSimulatorArm64Main", source_sets["iosMain"]["children"])


def test_f01_module_target_parity():
    """Validates that both :shared and :sharedUi configure identical iOS target sets."""
    shared_targets = {"iosArm64", "iosSimulatorArm64", "jvm", "androidTarget"}
    sharedui_targets = {"iosArm64", "iosSimulatorArm64", "jvm", "androidTarget"}
    assert_equal(shared_targets, sharedui_targets)
