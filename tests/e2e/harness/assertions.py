"""Assertion helpers and mathematical invariant validators for BitChord E2E tests."""

import json
import math
import re

class AssertionFailure(AssertionError):
    """Specific error for test assertion failures with diagnostic details."""
    pass


def assert_equal(actual, expected, msg: str = ""):
    if actual != expected:
        detail = f"Expected {expected!r}, but got {actual!r}."
        full_msg = f"{msg} -> {detail}" if msg else detail
        raise AssertionFailure(full_msg)


def assert_true(expr, msg: str = ""):
    if not expr:
        detail = f"Expression expected to be True, but was {expr!r}."
        full_msg = f"{msg} -> {detail}" if msg else detail
        raise AssertionFailure(full_msg)


def assert_false(expr, msg: str = ""):
    if expr:
        detail = f"Expression expected to be False, but was {expr!r}."
        full_msg = f"{msg} -> {detail}" if msg else detail
        raise AssertionFailure(full_msg)


def assert_in(item, container, msg: str = ""):
    if item not in container:
        detail = f"Item {item!r} not found in container."
        full_msg = f"{msg} -> {detail}" if msg else detail
        raise AssertionFailure(full_msg)


def assert_not_in(item, container, msg: str = ""):
    if item in container:
        detail = f"Item {item!r} unexpectedly found in container."
        full_msg = f"{msg} -> {detail}" if msg else detail
        raise AssertionFailure(full_msg)


def assert_almost_equal(actual, expected, delta: float = 1e-4, msg: str = ""):
    diff = abs(actual - expected)
    if diff > delta:
        detail = f"Actual {actual} differs from expected {expected} by {diff} (tolerance: {delta})."
        full_msg = f"{msg} -> {detail}" if msg else detail
        raise AssertionFailure(full_msg)


def assert_equal_power_crossfade(gain_a: float, gain_b: float, tolerance: float = 1e-3, msg: str = ""):
    """Verifies that Gain_A^2 + Gain_B^2 == 1.0 (0 dB acoustic power conservation)."""
    power = (gain_a ** 2) + (gain_b ** 2)
    diff = abs(power - 1.0)
    if diff > tolerance:
        detail = f"Equal-power invariant violated: {gain_a}^2 + {gain_b}^2 = {power} != 1.0 (diff={diff})."
        full_msg = f"{msg} -> {detail}" if msg else detail
        raise AssertionFailure(full_msg)


def assert_sha1_hex(value: str, msg: str = ""):
    """Verifies string is a valid 40-character lowercase hexadecimal SHA-1 string."""
    assert_true(isinstance(value, str), f"{msg}: Expected string for SHA-1")
    assert_equal(len(value), 40, f"{msg}: SHA-1 length must be 40 chars")
    assert_true(bool(re.match(r"^[0-9a-f]{40}$", value)), f"{msg}: Invalid SHA-1 hex format: {value}")


def assert_md5_hex(value: str, msg: str = ""):
    """Verifies string is a valid 32-character lowercase hexadecimal MD5 string."""
    assert_true(isinstance(value, str), f"{msg}: Expected string for MD5")
    assert_equal(len(value), 32, f"{msg}: MD5 length must be 32 chars")
    assert_true(bool(re.match(r"^[0-9a-f]{32}$", value)), f"{msg}: Invalid MD5 hex format: {value}")


def assert_valid_json(content: str, msg: str = ""):
    """Parses and returns valid JSON, or raises AssertionFailure."""
    try:
        return json.loads(content)
    except Exception as e:
        raise AssertionFailure(f"{msg}: Invalid JSON content: {e}\nContent was: {content[:200]}")


def assert_no_jvm_imports(file_content: str, filepath: str = ""):
    """Verifies that no Java/JVM imports exist in pure KMP sources."""
    forbidden = ["import java.", "import javax.", "java.lang.", "java.util."]
    violations = []
    lines = file_content.splitlines()
    for idx, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*"):
            continue
        for f in forbidden:
            if f in line:
                violations.append(f"Line {idx}: {line.strip()}")
    if violations:
        raise AssertionFailure(f"Forbidden JVM imports found in {filepath}:\n" + "\n".join(violations))


def assert_track_features_schema(features: dict, msg: str = ""):
    """Verifies TrackFeatures dictionary adheres to the C++ DSP & Kotlin contract."""
    required_keys = [
        "bpm", "key", "camelot", "duration_ms", "phrase_boundaries",
        "cue_in_ms", "cue_out_ms", "rms_envelope", "spectral_flux"
    ]
    for key in required_keys:
        assert_in(key, features, f"{msg}: Missing key {key} in TrackFeatures")

    assert_true(features["bpm"] > 0, f"{msg}: BPM must be positive, got {features['bpm']}")
    assert_true(isinstance(features["camelot"], str), f"{msg}: camelot must be string")
    assert_true(bool(re.match(r"^[1-9]|1[0-2][AB]$", features["camelot"])), f"{msg}: Invalid Camelot format: {features['camelot']}")
    assert_true(features["cue_in_ms"] >= 0, f"{msg}: cue_in_ms must be non-negative")
    assert_true(features["cue_out_ms"] >= features["cue_in_ms"], f"{msg}: cue_out_ms must be >= cue_in_ms")


def assert_valid_stream_result(result: dict, msg: str = ""):
    """Verifies ResolvedStreamResult schema from innertube resolver."""
    required = ["streamUrl", "itag", "mimeType", "bitrate", "contentLength", "poTokenUsed"]
    for r in required:
        assert_in(r, result, f"{msg}: Missing stream result field {r}")
    assert_true(result["streamUrl"].startswith("http"), f"{msg}: Stream URL must be HTTP(S)")
    assert_true(result["bitrate"] > 0, f"{msg}: Bitrate must be positive")
    assert_true(result["contentLength"] > 0, f"{msg}: Content length must be positive")
    assert_in(result["itag"], [140, 141, 251, 250, 249], f"{msg}: Unexpected itag {result['itag']}")
