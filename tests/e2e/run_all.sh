#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT_DIR"

echo "========================================================"
echo "Running BitChord iPadOS E2E Complete Test Suite"
echo "========================================================"
python3 tests/e2e/run_e2e_tests.py --tier all --report build/e2e_test_report.json "$@"
