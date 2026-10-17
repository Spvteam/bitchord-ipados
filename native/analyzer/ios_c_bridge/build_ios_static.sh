#!/usr/bin/env bash
set -euo pipefail

# Convenience forwarder to scripts/compile_dsp_ios.sh
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

exec bash "$REPO_ROOT/scripts/compile_dsp_ios.sh" "$@"
