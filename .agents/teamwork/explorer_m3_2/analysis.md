# WP-04 Technical Analysis: iOS Clang Cross-Compilation & Static Library Packaging

**Agent**: `explorer_m3_2`  
**Milestone**: M3 (WP-04: C++17 DSP Analyzer iOS Bridge)  
**Date**: 2026-10-16  
**Status**: COMPLETE  

---

## Executive Summary

BitChord features a 100% portable, dependency-free C++17 digital signal processing (DSP) audio analysis engine located in `native/analyzer/`. It performs offline whole-track analysis (BPM, beat interval, beat confidence, beat grid tracking, downbeats, key, camelot notation, phrases, RMS energy curves, mix-in/mix-out cue scoring) and STFT spectrogram feature extraction (log-mel for `beat_this` beat/downbeat ONNX model, linear-magnitude STFT for `open-unmix` vocal activity ONNX model).

To port this engine to iPadOS/iOS without rewriting the mathematically calibrated DSP algorithms in Swift/vDSP, the C++17 sources plus an `extern "C"` ABI bridge must be compiled into static library archives (`libbitchord_analysis.a`) for physical devices (`iosArm64`) and Apple Silicon simulators (`iosSimulatorArm64`), and linked into Kotlin/Native's `:shared` framework via `cinterop`.

This report provides the complete specification for compiler toolchains, compilation flags, static library directory layout, build automation, and Kotlin/Native linker configuration.

---

## 1. Upstream Native C++ Analyzer Audit

### 1.1 Source Files to Compile
The engine consists of 5 core C++17 source files and 4 headers in `native/analyzer/`:

| Source File | Header File | Size | Role / Algorithm |
| :--- | :--- | :--- | :--- |
| `audio_analysis.cpp` | `audio_analysis.h` | 29.7 KB | Whole-track DSP analysis: onset detection, autocorrelation, harmonic pitch chroma, key estimation, phrase boundaries, RMS energy curves, mix-in/mix-out cue scoring |
| `tempo_analysis.cpp` | `audio_analysis.h` | 25.5 KB | Phase-locked loop (PLL) beat tracker, tempo histogram, downbeat grid tracking |
| `resampler.cpp` | `resampler.h` | 5.3 KB | Windowed sinc band-limited PCM resampler (e.g., 44.1 kHz / 48 kHz -> 11.025 kHz / 22.05 kHz) |
| `mel_spectrogram.cpp` | `mel_spectrogram.h` | 8.7 KB | 128-band Slaney log-mel spectrogram front end (22,050 Hz, n_fft=1024, hop=441, 50 fps) calibrated for `beat_this.onnx` |
| `vocal_spectrogram.cpp` | `vocal_spectrogram.h` | 5.8 KB | Planar stereo 1487-bin STFT magnitude front end (44,100 Hz, n_fft=4096, hop=1024) calibrated for `open-unmix` vocal activity |
| `bitchord_ios_bridge.cpp` | `bitchord_ios_bridge.h` | ~5 KB | `extern "C"` ABI bridge and JSON serializer (to be placed in `native/analyzer/ios_c_bridge/`) |

### 1.2 Dependency & Header Analysis
An exhaustive `#include` audit of all files in `native/analyzer/` reveals:
- **Standard Library Includes Only**: `<algorithm>`, `<array>`, `<cmath>`, `<complex>`, `<cstddef>`, `<numeric>`, `<string>`, `<utility>`, `<vector>`.
- **Zero Third-Party Dependencies**: No external math libraries (no FFTW, no KissFFT, no Eigen, no Boost). Cooley-Tukey radix-2 FFT and Slaney filterbanks are implemented directly in-tree.
- **Zero Platform Dependencies**: No Win32, POSIX, Android NDK, or Apple frameworks are referenced. The code is 100% ISO C++17 standard compliant.
- **Thread Safety**: All functions are reentrant. State is entirely function-local and heap-allocated; no global mutable variables exist.

---

## 2. Target Architectures & The Dual-Slice ARM64 Problem

### 2.1 Target Architectures
iPadOS 16.0+ support requires two distinct Apple platforms:

1. **`iosArm64` (Physical Devices)**:
   - Target triple: `arm64-apple-ios16.0` (or `arm64-apple-ios`)
   - Platform SDK: `iphoneos` (`$(xcrun --sdk iphoneos --show-sdk-path)`)
   - CPU Architecture: 64-bit ARM (`arm64`, Apple A-series / M-series in iPad)
2. **`iosSimulatorArm64` (Apple Silicon Simulator)**:
   - Target triple: `arm64-apple-ios16.0-simulator` (or `arm64-apple-ios-simulator`)
   - Platform SDK: `iphonesimulator` (`$(xcrun --sdk iphonesimulator --show-sdk-path)`)
   - CPU Architecture: 64-bit ARM (`arm64`, Apple Silicon Mac host)

### 2.2 Why `lipo` Fat Universal Libraries CANNOT Be Used
In classic iOS development, `lipo -create` merged `arm64` (device) and `x86_64` (simulator) into a single "universal" `libbitchord_analysis.a`.

However, on modern Apple Silicon Macs (M1/M2/M3/M4), simulator targets are ALSO `arm64`. Attempting to merge `iosArm64/libbitchord_analysis.a` and `iosSimulatorArm64/libbitchord_analysis.a` with `lipo` produces a fatal toolchain error:
```
fatal error: /usr/bin/lipo: libbitchord_analysis_device.a and libbitchord_analysis_sim.a have the same architectures (arm64) and can't be in the same fat output file
```

### 2.3 The Architectural Solution: Separate Static Library Directories
To support both targets cleanly in Gradle and Kotlin/Native without colliding:
- Package static archives into distinct architecture subdirectories:
  - `native/libs/iosArm64/libbitchord_analysis.a`
  - `native/libs/iosSimulatorArm64/libbitchord_analysis.a`
- Configure Gradle Kotlin Multiplatform to feed the matching directory path to each target's `cinterop` and `binaries.framework` link tasks.

---

## 3. Host System Inspection & Toolchain Strategy

### 3.1 Host Inspection Results (Windows 11)
- **Host OS**: Windows 11 10.0 amd64
- **Host Compilers in PATH**:
  - `gcc.exe` / `g++.exe`: MinGW.org GCC 6.3.0 (32-bit x86, release year 2016). Incompatible with modern C++17 cross-compilation to Apple Mach-O ARM64.
  - `clang` / `clang++`: Not installed in system PATH.
  - `zig`: Not installed in system PATH.
  - `cmake` / `ninja`: Not installed in system PATH.
- **Kotlin/Native Prebuilt Distribution**:
  - Located at `C:\Users\supha\.konan\kotlin-native-prebuilt-windows-x86_64-2.3.20`.
  - Properties (`konan.properties`) define target triples:
    - `targetTriple.ios_arm64 = arm64-apple-ios`
    - `targetTriple.ios_simulator_arm64 = arm64-apple-ios-simulator`
    - Target toolchains: Xcode 16 toolchains (`target-toolchain-xcode_26.0.1_17A400`).
- **Gradle & JVM**:
  - Gradle 8.11.1 on JDK 21 (Eclipse Adoptium 21.0.12.1+1-LTS).
  - Gradle evaluates `:shared` and successfully registers Kotlin/Native compilation tasks (`compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64`, etc.).

### 3.2 Dual-Track Build Strategy

Because BitChord development and CI span multiple platforms (Windows workstations for logic, macOS/Xcode runners for `.ipa` signing and packaging), the static library packaging pipeline must support two operational tracks:

#### Track 1: Canonical Native Apple Clang Build (macOS / CI / Xcode)
- Uses Apple's official `xcrun clang++`, `ar`, and `ranlib` provided by Xcode Command Line Tools.
- Executed via `scripts/compile_dsp_ios.sh` and `native/analyzer/ios_c_bridge/build_ios_static.sh`.
- Run during GitHub Actions CI (`macos-14` / `macos-15`) and local macOS development.

#### Track 2: Prebuilt Committed Static Archives & Cross-Tool Verification (Windows / KMP)
- Static archives are compiled once and tracked in Git under `native/libs/iosArm64/` and `native/libs/iosSimulatorArm64/`.
- Gradle build tasks on Windows or Linux consume the prebuilt archives directly without requiring a local Clang cross-compiler installation.
- If Zig C++ (`zig c++`) or LLVM is available, an alternative cross-compilation command (`zig c++ -target aarch64-ios ...`) can compile them from any host.

---

## 4. Compiler Flags & Optimization Deep Dive

### 4.1 Flags Table

| Compiler Flag | Value | Rationale |
| :--- | :--- | :--- |
| **Language Standard** | `-std=c++17` | Required for structured bindings, `inline constexpr`, `<complex>`, and `std::string_view`. |
| **Optimization** | `-O3` | High-level vectorization and loop unrolling critical for FFT loops, sinc resampling, and autocorrelation. |
| **Position Independent Code** | `-fPIC` | Mandatory for static archives linked into iOS dynamic frameworks or Mach-O executables. |
| **Symbol Visibility** | `-fvisibility=hidden` | Hides internal C++ classes/symbols. Only the `extern "C"` bridge symbols are exported, preventing symbol namespace collisions. |
| **Fast Math Policy** | **DO NOT USE `-ffast-math`** | **CRITICAL AUDIT FINDING**: Upstream Android `app/src/main/cpp/CMakeLists.txt:39-40` states: `"-ffast-math is deliberately NOT enabled: the analyzer's log/mel-adjacent arithmetic is tuned against exact IEEE behaviour."` `-ffast-math` enables non-IEEE reordering and breaks parity with PyTorch `torchaudio`. |
| **Target Architecture** | `-arch arm64` | Generates 64-bit ARM Mach-O object code. |
| **Sysroot** | `-isysroot <SDK_PATH>` | Points to Apple `iPhoneOS.platform` or `iPhoneSimulator.platform` SDK headers. |
| **Deployment Target** | `-miphoneos-version-min=16.0` / `-mios-simulator-version-min=16.0` | Targets iPadOS 16.0+ ABI. |

### 4.2 Critical Discovery: `-ffast-math` vs Numerical Parity
In the preliminary blueprint (`BitChord_iPadOS_Master_Porting_Plan.md` line 541), `-ffast-math` was proposed. However, our direct inspection of upstream Android and Desktop builds revealed:
1. `app/src/main/cpp/CMakeLists.txt` lines 39-40:
   ```cmake
   # -ffast-math is deliberately NOT enabled: the analyzer's log/mel-adjacent
   # arithmetic (see audio_analysis.cpp) is tuned against exact IEEE behaviour.
   target_compile_options(bitchord_analysis PRIVATE -O3 -fvisibility=hidden)
   ```
2. The mel filterbank (`mel_spectrogram.cpp`) and linear STFT (`vocal_spectrogram.cpp`) are strictly calibrated against PyTorch's `torchaudio.transforms.MelSpectrogram` and `openunmix.transforms.TorchSTFT`.
3. `-ffast-math` allows unsafe floating-point associations, disables signed zero handling, and modifies NaN propagation. Even a small shift in log-mel values degrades `beat_this` beat-tracking accuracy.
4. **Recommendation**: Strictly omit `-ffast-math` (or pass `-fno-fast-math`), retaining `-O3 -fvisibility=hidden`.

---

## 5. Repository Layout & File Architecture

```
D:\Bitchord\
├── native/
│   ├── analyzer/
│   │   ├── audio_analysis.h
│   │   ├── audio_analysis.cpp
│   │   ├── tempo_analysis.cpp
│   │   ├── resampler.h
│   │   ├── resampler.cpp
│   │   ├── mel_spectrogram.h
│   │   ├── mel_spectrogram.cpp
│   │   ├── vocal_spectrogram.h
│   │   ├── vocal_spectrogram.cpp
│   │   └── ios_c_bridge/
│   │       ├── bitchord_ios_bridge.h
│   │       ├── bitchord_ios_bridge.cpp
│   │       └── build_ios_static.sh
│   └── libs/
│       ├── iosArm64/
│       │   └── libbitchord_analysis.a
│       └── iosSimulatorArm64/
│           └── libbitchord_analysis.a
├── scripts/
│   ├── compile_dsp_ios.sh
│   └── compile_dsp_ios.ps1
└── shared/
    ├── build.gradle.kts
    └── src/
        ├── nativeInterop/
        │   └── cinterop/
        │       └── bitchord_analysis.def
        └── iosMain/
            └── kotlin/com/music/bitchord/playback/
                ├── MelSpectrogram.ios.kt
                ├── VocalSpectrogram.ios.kt
                └── NativeTrackAnalyzer.ios.kt
```

---

## 6. Exact Build & Compilation Commands

### 6.1 Canonical Apple Clang Compilation (`compile_dsp_ios.sh`)

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANALYZER_DIR="$REPO_ROOT/native/analyzer"
BRIDGE_DIR="$ANALYZER_DIR/ios_c_bridge"
BUILD_DIR="$REPO_ROOT/build/dsp_native"

echo "=== Compiling BitChord C++17 DSP Analyzer for iOS ==="

SRCS=(
  "$ANALYZER_DIR/audio_analysis.cpp"
  "$ANALYZER_DIR/tempo_analysis.cpp"
  "$ANALYZER_DIR/resampler.cpp"
  "$ANALYZER_DIR/mel_spectrogram.cpp"
  "$ANALYZER_DIR/vocal_spectrogram.cpp"
  "$BRIDGE_DIR/bitchord_ios_bridge.cpp"
)

# ---------------------------------------------------------
# Target 1: iosArm64 (Physical iPad / iPhone)
# ---------------------------------------------------------
echo "[1/2] Building for iosArm64 (Device)..."
IPHONEOS_SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
OBJ_DIR_ARM64="$BUILD_DIR/obj/iosArm64"
OUT_DIR_ARM64="$REPO_ROOT/native/libs/iosArm64"
mkdir -p "$OBJ_DIR_ARM64" "$OUT_DIR_ARM64"

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

# ---------------------------------------------------------
# Target 2: iosSimulatorArm64 (Apple Silicon Mac Simulator)
# ---------------------------------------------------------
echo "[2/2] Building for iosSimulatorArm64 (Simulator)..."
IPHONESIM_SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"
OBJ_DIR_SIM="$BUILD_DIR/obj/iosSimulatorArm64"
OUT_DIR_SIM="$REPO_ROOT/native/libs/iosSimulatorArm64"
mkdir -p "$OBJ_DIR_SIM" "$OUT_DIR_SIM"

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

echo "=== DSP Static Library Compilation Complete ==="
```

### 6.2 Alternative Zig C++ Cross-Compilation (Host Agnostic)
For environments where Zig is installed:
```bash
# iosArm64
zig c++ -target aarch64-ios -std=c++17 -O3 -fPIC -fvisibility=hidden \
  -I native/analyzer -I native/analyzer/ios_c_bridge -c \
  native/analyzer/audio_analysis.cpp \
  native/analyzer/tempo_analysis.cpp \
  native/analyzer/resampler.cpp \
  native/analyzer/mel_spectrogram.cpp \
  native/analyzer/vocal_spectrogram.cpp \
  native/analyzer/ios_c_bridge/bitchord_ios_bridge.cpp
zig ar rcs native/libs/iosArm64/libbitchord_analysis.a *.o

# iosSimulatorArm64
zig c++ -target aarch64-ios-simulator -std=c++17 -O3 -fPIC -fvisibility=hidden \
  -I native/analyzer -I native/analyzer/ios_c_bridge -c \
  native/analyzer/audio_analysis.cpp \
  native/analyzer/tempo_analysis.cpp \
  native/analyzer/resampler.cpp \
  native/analyzer/mel_spectrogram.cpp \
  native/analyzer/vocal_spectrogram.cpp \
  native/analyzer/ios_c_bridge/bitchord_ios_bridge.cpp
zig ar rcs native/libs/iosSimulatorArm64/libbitchord_analysis.a *.o
```

---

## 7. Kotlin/Native `cinterop` & Linker Configuration

### 7.1 Cinterop Definition File
Location: `shared/src/nativeInterop/cinterop/bitchord_analysis.def`
(Strictly satisfies contract in `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py:68-78`):
```ini
headers = bitchord_ios_bridge.h
package = com.music.bitchord.native.analysis
compilerOpts = -std=c++17
staticLibraries = libbitchord_analysis.a
```

### 7.2 Gradle Configuration in `shared/build.gradle.kts`
Wire the target-specific paths in `shared/build.gradle.kts`:

```kotlin
kotlin {
    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64()
    )

    iosTargets.forEach { target ->
        val targetSubdir = if (target.name == "iosArm64") "iosArm64" else "iosSimulatorArm64"
        val libDir = rootProject.file("native/libs/$targetSubdir").absolutePath

        target.compilations.getByName("main").cinterops {
            val bitchord_analysis by creating {
                defFile(project.file("src/nativeInterop/cinterop/bitchord_analysis.def"))
                includeDirs(
                    rootProject.file("native/analyzer"),
                    rootProject.file("native/analyzer/ios_c_bridge")
                )
                extraOpts("-libraryPath", libDir)
            }
        }

        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            linkerOpts(
                "-L$libDir",
                "-lbitchord_analysis",
                "-lc++",
                "-framework", "AVFoundation",
                "-framework", "AudioToolbox",
                "-framework", "JavaScriptCore",
                "-framework", "WebKit",
                "-framework", "Security",
                "-lsqlite3"
            )
        }
    }
}
```

### 7.3 Mandatory Linker Flag: `-lc++`
`libbitchord_analysis.a` is compiled C++17 object code that references:
- `operator new(size_t)` / `operator delete(void*)`
- `std::__1::basic_string<char>` symbols
- `std::__1::vector<float>` allocations
- `std::complex<float>` / `std::complex<double>` routines
Without `-lc++` in `linkerOpts`, the Kotlin/Native framework link step fails with unresolved symbol errors (`Undefined symbols for architecture arm64: "___cxa_allocate_exception", "std::terminate()"`). Including `-lc++` ensures the standard Apple libc++ runtime is linked.

---

## 8. Verification Strategy

1. **Header & Symbol Audit**:
   - Run `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py` (`pytest tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py`) to verify all 6 test cases pass (C ABI signatures, JSON schema, memory free contract, mel dimensions, vocal dimensions, cinterop def file configuration).
2. **Mach-O Architecture Verification** (on macOS / Linux with `file` / `lipo` / `otool`):
   ```bash
   file native/libs/iosArm64/libbitchord_analysis.a
   # Expected output: current ar archive, Mach-O 64-bit arm64
   file native/libs/iosSimulatorArm64/libbitchord_analysis.a
   # Expected output: current ar archive, Mach-O 64-bit arm64 (simulator)
   nm native/libs/iosArm64/libbitchord_analysis.a | grep bitchord_
   # Expected output: bitchord_analyze_audio_json, bitchord_free_string, etc.
   ```
3. **Gradle Compilation**:
   ```bash
   ./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
   ```
   Ensures Kotlin/Native cinterop generates the `com.music.bitchord.native.analysis` package and compiles cleanly into metadata klibs.
