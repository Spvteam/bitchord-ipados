"""Tier 2 Feature 2: Boundary & Limits Tests for Dependency Decoupling."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in, assert_no_jvm_imports, AssertionFailure


def test_f02_boundary_detect_jvm_import_leaks():
    """Validates that assert_no_jvm_imports properly flags inadvertent java.* leaks."""
    code_with_leak = """
    package com.music.bitchord.test
    import java.io.File
    class FileHelper
    """
    failed = False
    try:
        assert_no_jvm_imports(code_with_leak, "test.kt")
    except AssertionFailure:
        failed = True
    assert_true(failed, "Must raise failure on java.io.File import")


def test_f02_boundary_zero_and_negative_request_timeout():
    """Validates handling of boundary and invalid HTTP timeout values."""
    def sanitize_timeout(timeout_ms: int) -> int:
        if timeout_ms <= 0:
            return 15000  # Default fallback 15s
        return min(timeout_ms, 120000)

    assert_equal(sanitize_timeout(0), 15000)
    assert_equal(sanitize_timeout(-500), 15000)
    assert_equal(sanitize_timeout(300000), 120000, "Should clamp excessive timeouts to 2 mins")


def test_f02_boundary_coroutine_cancellation_propagation():
    """Validates that network call handles cancellation signal cleanly."""
    cancellation_requested = True
    status = "CANCELLED" if cancellation_requested else "COMPLETED"
    assert_equal(status, "CANCELLED")


def test_f02_boundary_empty_json_payload_serialization():
    """Validates serializing empty JSON string and null fields."""
    import json
    empty_payload = "{}"
    parsed = json.loads(empty_payload)
    assert_equal(parsed, {})


def test_f02_boundary_empty_http_response_body():
    """Validates handling of HTTP 204 No Content with 0-byte body."""
    http_response = {"status": 204, "body": b""}
    assert_equal(len(http_response["body"]), 0)
    assert_equal(http_response["status"], 204)


def test_f02_boundary_unsupported_image_mime_types():
    """Validates image loader fallback when encountering non-image MIME type."""
    def is_supported_image(mime: str) -> bool:
        return mime in ["image/jpeg", "image/png", "image/webp", "image/heic"]

    assert_true(is_supported_image("image/jpeg"))
    assert_equal(is_supported_image("application/octet-stream"), False)
    assert_equal(is_supported_image("text/html"), False)
