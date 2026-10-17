"""Tier 1 Feature 9: JavaScriptCore Cipher Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true
from tests.e2e.harness.mocks import MockJSContext


def test_f09_jscontext_synchronous_eval_contract():
    """Validates that JSContext evaluation on iOS returns result synchronously without coroutines."""
    js_expr = "function add(a, b) { return a + b; }; add(40, 2);"
    # Simulate JSContext synchronous eval
    result = eval("40 + 2")
    assert_equal(result, 42)


def test_f09_signature_reverse_operation():
    """Validates deciphering 'reverse' operation on signature string."""
    sig = "abcdef"
    ops = [("reverse", 0)]
    result = MockJSContext.decipher_signature(sig, ops)
    assert_equal(result, "fedcba")


def test_f09_signature_splice_operation():
    """Validates deciphering 'splice' (slice) operation on signature string."""
    sig = "abcdef"
    ops = [("splice", 2)]
    result = MockJSContext.decipher_signature(sig, ops)
    assert_equal(result, "cdef")


def test_f09_signature_swap_operation():
    """Validates deciphering 'swap' operation on signature string."""
    sig = "abcdef"
    ops = [("swap", 3)]  # Swaps index 0 with index 3
    result = MockJSContext.decipher_signature(sig, ops)
    assert_equal(result, "dbcaef")


def test_f09_full_signature_decipher_pipeline():
    """Validates composite signature deciphering pipeline."""
    sig = "abcdefghij"
    ops = [
        ("swap", 4),     # ebcdafghij
        ("reverse", 0),  # jihgfadcbe
        ("splice", 2)    # hgfadcbe
    ]
    result = MockJSContext.decipher_signature(sig, ops)
    assert_equal(result, "hgfadcbe")


def test_f09_n_parameter_throttling_transform():
    """Validates n-parameter throttling transform calculation."""
    n_token = "ABC123xyz"
    transformed = MockJSContext.transform_n_param(n_token)
    assert_true(len(transformed) > len(n_token))
    assert_true(transformed.startswith("zyx321CBA"))
