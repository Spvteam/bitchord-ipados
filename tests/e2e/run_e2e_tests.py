#!/usr/bin/env python3
"""BitChord iPadOS Port - E2E Master Test Runner Entry Point."""

import os
import sys

# Ensure repository root is in sys.path
root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
if root_dir not in sys.path:
    sys.path.insert(0, root_dir)

from tests.e2e.harness.test_runner import main

if __name__ == "__main__":
    main()
