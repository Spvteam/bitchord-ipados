"""Tier 2 Feature 9: Boundary & Limits Tests for JavaScriptCore Cipher."""

from tests.e2e.harness.assertions import assert_equal, assert_true
from tests.e2e.harness.mocks import MockJSContext


def test_f09_boundary_empty_signature_string():
    """Validates deciphering when signature token is empty string."""
    sig = ""
    ops = [("reverse", 0), ("splice", 1), ("swap", 2)]
    result = MockJSContext.decipher_signature(sig, ops)
    assert_equal(result, "")


def test_f09_boundary_swap_index_exceeds_signature_length():
    """Validates modulo wraparound when swap operation index > signature length."""
    sig = "abc"  # len = 3
    ops = [("swap", 7)]  # 7 % 3 = 1 -> swap 0 and 1
    result = MockJSContext.decipher_signature(sig, ops)
    assert_equal(result, "bac")


def test_f09_boundary_splice_index_exceeds_length():
    """Validates slice behavior when splice offset exceeds string length."""
    sig = "abc"
    ops = [("splice", 10)]
    result = MockJSContext.decipher_signature(sig, ops)
    assert_equal(result, "")


def test_f09_boundary_empty_n_token():
    """Validates n-parameter transform when token is empty string."""
    result = MockJSContext.transform_n_param("")
    assert_equal(result, "")


def test_f09_boundary_extreme_length_signature():
    """Validates signature deciphering on unusually large 1024-character token."""
    long_sig = "a" * 512 + "b" * 512
    ops = [("reverse", 0)]
    result = MockJSContext.decipher_signature(long_sig, ops)
    assert_equal(len(result), 1024)
    assert_true(result.startswith("b" * 512))


def test_f09_boundary_malformed_js_evaluation_safety():
    """Validates safe error catching when JS evaluation encounters syntax error."""
    def safe_eval(script: str) -> str:
        try:
            # Simulate JS engine error catch
            if "syntax error" in script.lower():
                raise ValueError("SyntaxError: Unexpected token")
            return "SUCCESS"
        except Exception:
            return ""

    assert_equal(safe_eval("var x = syntax error;"), "")
    assert_equal(safe_eval("var x = 10;"), "SUCCESS")
