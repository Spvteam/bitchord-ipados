#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# BitChord C++17 DSP Analyzer - iOS Static Library Build Script
# Targets: iosArm64 (Device) & iosSimulatorArm64 (Apple Silicon Simulator)
# ==============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANALYZER_DIR="$REPO_ROOT/native/analyzer"
BRIDGE_DIR="$ANALYZER_DIR/ios_c_bridge"
BUILD_DIR="$REPO_ROOT/build/dsp_ios"

echo "=== Compiling BitChord C++17 DSP Analyzer for iOS ==="

SRCS=(
  "$ANALYZER_DIR/audio_analysis.cpp"
  "$ANALYZER_DIR/tempo_analysis.cpp"
  "$ANALYZER_DIR/resampler.cpp"
  "$ANALYZER_DIR/mel_spectrogram.cpp"
  "$ANALYZER_DIR/vocal_spectrogram.cpp"
  "$BRIDGE_DIR/bitchord_ios_bridge.cpp"
)

# ------------------------------------------------------------------------------
# 1. Target: iosArm64 (Physical iPad / iPhone)
# ------------------------------------------------------------------------------
echo "[1/2] Building for iosArm64 (Device)..."
IPHONEOS_SDK="$(xcrun --sdk iphoneos --show-sdk-path 2>/dev/null || true)"
OUT_DIR_ARM64="$REPO_ROOT/native/libs/iosArm64"
OBJ_DIR_ARM64="$BUILD_DIR/obj/iosArm64"
mkdir -p "$OBJ_DIR_ARM64" "$OUT_DIR_ARM64"

if [ -n "$IPHONEOS_SDK" ]; then
  for src in "${SRCS[@]}"; do
    obj="$OBJ_DIR_ARM64/$(basename "$src" .cpp).o"
    xcrun --sdk iphoneos clang++ \
      -target arm64-apple-ios16.0 \
      -isysroot "$IPHONEOS_SDK" \
      -miphoneos-version-min=16.0 \
      -std=c++17 \
      -O3 \
      -fPIC \
      -fvisibility=hidden \
      -I "$ANALYZER_DIR" \
      -I "$BRIDGE_DIR" \
      -c "$src" -o "$obj"
  done
  xcrun --sdk iphoneos ar -rcs "$OUT_DIR_ARM64/libbitchord_analysis.a" "$OBJ_DIR_ARM64"/*.o
  xcrun --sdk iphoneos ranlib "$OUT_DIR_ARM64/libbitchord_analysis.a"
  echo "  -> Produced $OUT_DIR_ARM64/libbitchord_analysis.a"
else
  echo "  [INFO] Xcode/Apple SDK not detected on host. Using prebuilt archive."
fi

# ------------------------------------------------------------------------------
# 2. Target: iosSimulatorArm64 (Apple Silicon Mac Simulator)
# ------------------------------------------------------------------------------
echo "[2/2] Building for iosSimulatorArm64 (Simulator)..."
IPHONESIM_SDK="$(xcrun --sdk iphonesimulator --show-sdk-path 2>/dev/null || true)"
OUT_DIR_SIM="$REPO_ROOT/native/libs/iosSimulatorArm64"
OBJ_DIR_SIM="$BUILD_DIR/obj/iosSimulatorArm64"
mkdir -p "$OBJ_DIR_SIM" "$OUT_DIR_SIM"

if [ -n "$IPHONESIM_SDK" ]; then
  for src in "${SRCS[@]}"; do
    obj="$OBJ_DIR_SIM/$(basename "$src" .cpp).o"
    xcrun --sdk iphonesimulator clang++ \
      -target arm64-apple-ios16.0-simulator \
      -isysroot "$IPHONESIM_SDK" \
      -mios-simulator-version-min=16.0 \
      -std=c++17 \
      -O3 \
      -fPIC \
      -fvisibility=hidden \
      -I "$ANALYZER_DIR" \
      -I "$BRIDGE_DIR" \
      -c "$src" -o "$obj"
  done
  xcrun --sdk iphonesimulator ar -rcs "$OUT_DIR_SIM/libbitchord_analysis.a" "$OBJ_DIR_SIM"/*.o
  xcrun --sdk iphonesimulator ranlib "$OUT_DIR_SIM/libbitchord_analysis.a"
  echo "  -> Produced $OUT_DIR_SIM/libbitchord_analysis.a"
else
  echo "  [INFO] Xcode/Apple SDK not detected on host. Using prebuilt archive."
fi

echo "=== DSP Static Library Compilation Complete ==="
