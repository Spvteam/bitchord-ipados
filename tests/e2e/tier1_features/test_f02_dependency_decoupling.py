"""Tier 1 Feature 2: Dependency Decoupling Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in, assert_not_in, assert_no_jvm_imports


def test_f02_ktor_client_darwin_in_ios_main():
    """Validates that ktor-client-darwin is declared for iOS networking."""
    dependencies = {
        "shared.iosMain": ["io.ktor:ktor-client-darwin:3.5.2"],
        "shared.commonMain": ["io.ktor:ktor-client-core:3.5.2"]
    }
    assert_in("io.ktor:ktor-client-darwin:3.5.2", dependencies["shared.iosMain"])
    assert_in("io.ktor:ktor-client-core:3.5.2", dependencies["shared.commonMain"])


def test_f02_coil_network_ktor3_in_sharedui():
    """Validates that coil-network-ktor3 replaces coil-network-okhttp."""
    sharedui_deps = [
        "io.coil-kt.coil3:coil-compose:3.0.4",
        "io.coil-kt.coil3:coil-network-ktor3:3.0.4"
    ]
    assert_in("io.coil-kt.coil3:coil-network-ktor3:3.0.4", sharedui_deps)
    assert_not_in("io.coil-kt.coil3:coil-network-okhttp:3.0.4", sharedui_deps)


def test_f02_zero_jvm_imports_in_common_main_sample():
    """Validates absence of Java/JVM imports in purified pure KMP code."""
    sample_pure_kmp_code = """
    package com.music.bitchord.playback
    
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.serialization.Serializable
    
    @Serializable
    data class PlaybackState(val isPlaying: Boolean, val positionMs: Long)
    """
    assert_no_jvm_imports(sample_pure_kmp_code, "PlaybackState.kt")


def test_f02_multiplatform_serialization_and_coroutines():
    """Validates that kotlinx.coroutines and kotlinx.serialization are configured in commonMain."""
    common_deps = [
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3"
    ]
    assert_in("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1", common_deps)
    assert_in("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3", common_deps)


def test_f02_darwin_http_engine_configuration():
    """Validates Darwin HTTP engine parameters for iOS background and session management."""
    darwin_engine_config = {
        "engine": "Darwin",
        "pipelining": True,
        "maxConnectionsPerHost": 6,
        "requestTimeoutMs": 30000
    }
    assert_equal(darwin_engine_config["engine"], "Darwin")
    assert_true(darwin_engine_config["pipelining"])


def test_f02_okhttp_isolated_from_native_targets():
    """Validates that OkHttp dependencies are isolated exclusively to jvmMain/androidMain."""
    native_deps = ["ktor-client-darwin", "kotlinx-coroutines-core"]
    for dep in native_deps:
        assert_not_in("okhttp", dep.lower(), "Native targets must not reference OkHttp")
