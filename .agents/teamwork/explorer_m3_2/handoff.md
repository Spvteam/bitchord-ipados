# Handoff Report: iOS Clang Cross-Compilation & Static Library Packaging (WP-04)

**Agent**: `explorer_m3_2`  
**Milestone**: M3 (WP-04: C++17 DSP Analyzer iOS Bridge)  
**Date**: 2026-10-16  
**Type**: Hard Handoff  

---

## 1. Observation

1. **Native Analyzer Source Code Layout and Includes**:
   - `native/analyzer/` contains 5 C++ implementation files and 4 headers:
     - `audio_analysis.h` (5,525 bytes) and `audio_analysis.cpp` (29,730 bytes)
     - `tempo_analysis.cpp` (25,494 bytes)
     - `resampler.h` (2,645 bytes) and `resampler.cpp` (5,253 bytes)
     - `mel_spectrogram.h` (3,348 bytes) and `mel_spectrogram.cpp` (8,739 bytes)
     - `vocal_spectrogram.h` (3,500 bytes) and `vocal_spectrogram.cpp` (5,793 bytes)
   - Every `#include` across all these files was checked via `grep_search`:
     - `<algorithm>`, `<array>`, `<cmath>`, `<complex>`, `<cstddef>`, `<numeric>`, `<string>`, `<utility>`, `<vector>`.
     - Internal headers: `"audio_analysis.h"`, `"mel_spectrogram.h"`, `"resampler.h"`, `"vocal_spectrogram.h"`.
     - Zero external libraries, zero OS-specific APIs.

2. **Upstream Compiler Flags and Floating Point Behavior**:
   - In `app/src/main/cpp/CMakeLists.txt` lines 39–41:
     ```cmake
     # -ffast-math is deliberately NOT enabled: the analyzer's log/mel-adjacent
     # arithmetic (see audio_analysis.cpp) is tuned against exact IEEE behaviour.
     target_compile_options(bitchord_analysis PRIVATE -O3 -fvisibility=hidden)
     ```
   - In `desktopApp/native/CMakeLists.txt` lines 31–33:
     ```cmake
     set(CMAKE_CXX_STANDARD 17)
     set(CMAKE_CXX_STANDARD_REQUIRED ON)
     set(CMAKE_POSITION_INDEPENDENT_CODE ON)
     ```

3. **Host System Environment & Toolchains**:
   - Host OS: Windows 11 10.0 amd64.
   - Command search for compilers:
     - `gcc found at C:\MinGW\bin\gcc.exe` (MinGW.org GCC-6.3.0-1, 32-bit x86 Windows only, cannot target Mach-O or ARM64).
     - `clang`, `clang++`, `zig`, `cl`, `cmake`, `ninja`, `xcrun` NOT found in system PATH.
   - Kotlin/Native prebuilt distribution found at:
     - `C:\Users\supha\.konan\kotlin-native-prebuilt-windows-x86_64-2.3.20`.
     - `konan.properties` lines 166 & 218:
       `targetTriple.ios_arm64 = arm64-apple-ios`
       `targetTriple.ios_simulator_arm64 = arm64-apple-ios-simulator`
       `linkerKonanFlags.ios_simulator_arm64 = -lSystem -lc++ -lobjc -framework Foundation -framework UIKit`

4. **Gradle & Target Configuration**:
   - `shared/build.gradle.kts` lines 22–39 configures `iosArm64()` and `iosSimulatorArm64()` with `isStatic = true` frameworks.
   - Running `.\gradlew.bat :shared:tasks` succeeded (`BUILD SUCCESSFUL in 44s`), registering tasks:
     `compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64`, `linkDebugFrameworkIosArm64`, `linkReleaseFrameworkIosArm64`, etc.

5. **E2E Feature 7 Test Contracts**:
   - `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py` lines 68–78 requires:
     ```python
     cinterop_def = {
         "headers": "bitchord_ios_bridge.h",
         "package": "com.music.bitchord.native.analysis",
         "compilerOpts": "-std=c++17",
         "staticLibraries": "libbitchord_analysis.a"
     }
     ```
   - Running `python -m tests.e2e.harness.test_runner --feature 7 --verbose` passed all 51/51 tests (100% success rate).

---

## 2. Logic Chain

1. **Single Static Library vs Architecture Slices** (from Observation 1 & 3):
   - `iosArm64` is the 64-bit ARM architecture for physical iOS devices.
   - `iosSimulatorArm64` is ALSO the 64-bit ARM architecture for Apple Silicon iOS simulators.
   - Because both targets share the same slice architecture (`arm64`), Apple's `lipo` tool refuses to merge them into a single universal archive (`lipo: ... have the same architectures (arm64)`).
   - Therefore, the static library archives must be kept in separate directories:
     - `native/libs/iosArm64/libbitchord_analysis.a`
     - `native/libs/iosSimulatorArm64/libbitchord_analysis.a`

2. **Selection of Compiler Flags** (from Observation 1 & 2):
   - The analyzer uses C++17 structured bindings, `inline constexpr`, and standard containers -> `-std=c++17` is mandatory.
   - High computational throughput is required for STFT FFT loops and autocorrelation -> `-O3` is required.
   - The static library is linked into a framework/executable -> `-fPIC` is mandatory.
   - Only `extern "C"` bridge functions should be visible outside the static library -> `-fvisibility=hidden` should be applied.
   - While `BitChord_iPadOS_Master_Porting_Plan.md:541` suggested `-ffast-math`, Observation 2 shows the upstream Android build explicitly bans `-ffast-math` because log-mel and STFT arithmetic is mathematically calibrated against PyTorch IEEE behavior. Using `-ffast-math` would introduce floating-point drift and violate parity. Therefore, `-ffast-math` MUST NOT be used.

3. **Toolchain & Build Execution Strategy** (from Observation 3 & 4):
   - On the Windows development machine, no Apple Clang compiler exists.
   - On macOS CI runners (`macos-14` / `macos-15`) and developer Macs, `xcrun --sdk iphoneos clang++` and `xcrun --sdk iphonesimulator clang++` are standard.
   - To support seamless Gradle builds across all operating systems without requiring a local Clang cross-compiler on Windows, static libraries should be generated via `scripts/compile_dsp_ios.sh` and tracked in `native/libs/`, or cross-compiled if Zig C++ is available.

4. **Kotlin/Native Linking and Runtime Resolution** (from Observation 3, 4, 5):
   - The C++ static library references C++ stdlib symbols (`operator new`, `std::vector`, `std::string`, `std::complex`).
   - Without linking the C++ standard library, the Kotlin/Native linker will fail with unresolved `std::` symbols.
   - Therefore, `-lc++` is mandatory in `shared/build.gradle.kts` `linkerOpts`.
   - The cinterop definition `bitchord_analysis.def` must specify `staticLibraries = libbitchord_analysis.a` with `package = com.music.bitchord.native.analysis`, matching `test_f07_cpp_analyzer_bridge.py`.
   - Gradle `cinterops` block must pass `-libraryPath <libDir>` pointing dynamically to `native/libs/iosArm64` or `native/libs/iosSimulatorArm64` depending on the compilation target.

---

## 3. Caveats

1. **Host Cross-Compilation Constraints**:
   - The current Windows host does not have LLVM/Clang targeting Darwin or Zig C++ installed. Local native static library compilation on Windows requires either installing Zig or running the compilation on a macOS host / CI runner.
2. **Apple Deployment Target Version**:
   - Assumed deployment target is iOS 16.0 (`-miphoneos-version-min=16.0` and `-mios-simulator-version-min=16.0`), which aligns with `BitChord_iPadOS_Master_Porting_Plan.md` and `PROJECT.md`.
3. **C++ Standard Library Vendor**:
   - Apple platforms strictly use LLVM `libc++`. GNU `libstdc++` is obsolete on Apple platforms. `-lc++` links Apple's system `libc++.dylib`.

---

## 4. Conclusion

1. **Source Files**: Compile 6 files: `audio_analysis.cpp`, `tempo_analysis.cpp`, `resampler.cpp`, `mel_spectrogram.cpp`, `vocal_spectrogram.cpp`, and `bitchord_ios_bridge.cpp`.
2. **Compiler Flags**: Use `-std=c++17 -O3 -fPIC -fvisibility=hidden`. **DO NOT USE `-ffast-math`** in order to preserve mathematical equivalence with PyTorch `torchaudio` and Android/Desktop builds.
3. **Target Triple & Sysroots**:
   - `iosArm64`: `-target arm64-apple-ios16.0 -isysroot $(xcrun --sdk iphoneos --show-sdk-path)` -> `native/libs/iosArm64/libbitchord_analysis.a`
   - `iosSimulatorArm64`: `-target arm64-apple-ios16.0-simulator -isysroot $(xcrun --sdk iphonesimulator --show-sdk-path)` -> `native/libs/iosSimulatorArm64/libbitchord_analysis.a`
4. **Header Search Paths**: `native/analyzer` and `native/analyzer/ios_c_bridge`.
5. **Build Automation**: Provide `scripts/compile_dsp_ios.sh` and `native/analyzer/ios_c_bridge/build_ios_static.sh`.
6. **Kotlin/Native Linker**: Add `-L<libDir>`, `-lbitchord_analysis`, and `-lc++` to `binaries.framework.linkerOpts` in `shared/build.gradle.kts`.

---

## 5. Verification Method

1. **E2E Test Runner**:
   ```powershell
   python -m tests.e2e.harness.test_runner --feature 7 --verbose
   ```
   *Expected Result*: 51/51 test cases pass, verifying C ABI signatures, memory free contracts, mel spectrogram dimensions (128 bins), vocal dimensions (1487 bins), and `bitchord_analysis.def` schema.

2. **Mach-O Architecture Verification** (on macOS / Linux):
   ```bash
   file native/libs/iosArm64/libbitchord_analysis.a
   # Must return: current ar archive, Mach-O 64-bit arm64
   file native/libs/iosSimulatorArm64/libbitchord_analysis.a
   # Must return: current ar archive, Mach-O 64-bit arm64 (simulator)
   nm native/libs/iosArm64/libbitchord_analysis.a | grep bitchord_
   # Must list: bitchord_analyze_audio_json, bitchord_free_string, bitchord_compute_mel_spectrogram, bitchord_compute_vocal_spectrogram
   ```

3. **Kotlin/Native Compilation Verification**:
   ```powershell
   .\gradlew.bat :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
   ```
   *Expected Result*: `BUILD SUCCESSFUL`, generating klibs with `com.music.bitchord.native.analysis` bindings.
