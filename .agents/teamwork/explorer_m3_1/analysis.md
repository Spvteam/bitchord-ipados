# Technical Analysis: C++17 DSP Analyzer iOS Bridge (WP-04 / Milestone M3)

**Author:** `explorer_m3_1`  
**Date:** 2026-10-16  
**Target:** BitChord iPadOS / iOS Port  
**Working Directory:** `D:\Bitchord\.agents\teamwork\explorer_m3_1`  

---

## 1. Executive Summary & Objective

In upstream BitChord (Android and Desktop), audio analysis is driven by an in-tree dependency-free C++17 DSP library located in `native/analyzer/`, interfaced to the JVM via JNI wrappers in `app/src/main/cpp/jni/`. For the iPadOS/iOS port (Work Package WP-04 / Milestone M3), the objective is to decouple this DSP engine from Java/JNI entirely, wrap it in a pure C ABI (`extern "C"`) operating exclusively on primitive types and raw float pointers, compile it to an iOS static library (`libbitchord_analysis.a`), and bind it into `:shared`'s `iosMain` target using Kotlin/Native `cinterop`.

This investigation thoroughly evaluates:
1. All native C++17 DSP source files in `native/analyzer/` (`audio_analysis`, `tempo_analysis`, `resampler`, `mel_spectrogram`, `vocal_spectrogram`).
2. Existing JNI bridge implementations in `app/src/main/cpp/jni/` (`analysis_jni.cpp`, `mel_jni.cpp`, `vocal_jni.cpp`).
3. Core DSP algorithms (tempo estimation, phase-locked loop beat tracking, Krumhansl-Schmuckler key detection, log-mel spectrogram, linear STFT vocal front end).
4. Critical disparities discovered between upstream C++ code, blueprint specifications, and E2E test assertions.
5. The complete C ABI bridge header design (`bitchord_analyzer_c.h` / `bitchord_ios_bridge.h`) with strict memory ownership, reentrant thread-safety, and sample rate conversion rules.

---

## 2. Deep Dive: Native C++17 DSP Source Code (`native/analyzer/`)

The DSP engine is 100% self-contained standard C++17 (no Boost, Eigen, FFTW, or third-party dependencies). It relies only on `<vector>`, `<cmath>`, `<complex>`, `<numeric>`, and `<algorithm>`.

```
native/analyzer/
├── audio_analysis.h        (Namespace bitchord::smart, AnalysisResult, struct definitions)
├── audio_analysis.cpp      (Envelope analysis, key detection, timbre, phrase segmentation, cue scoring)
├── tempo_analysis.cpp      (Autocorrelation tempo search, radix-2 FFT, PLL downbeat tracking)
├── resampler.h             (Windowed-sinc resampler declarations)
├── resampler.cpp           (Blackman windowed-sinc polyphase interpolation)
├── mel_spectrogram.h       (Log-mel front end declarations for Beat This! ONNX model)
├── mel_spectrogram.cpp     (Radix-2 FFT, Hann window, Slaney triangular mel filterbank)
├── vocal_spectrogram.h     (Linear-magnitude STFT front end for Open-Unmix model)
└── vocal_spectrogram.cpp   (Periodic Hann window, 4096-FFT, reflect padding, bin-major tensor layout)
```

### 2.1. Whole-Track DSP Analyzer (`audio_analysis.h`, `audio_analysis.cpp`)
- **Primary Function**: `bitchord::smart::AnalysisResult AnalyzeAudio(const std::vector<float>& samples, double sample_rate, double supplied_duration)`
- **Input Constraints**:
  - Contiguous mono `Float32` PCM in range `[-1.0, 1.0]`.
  - Tuned strictly for **11,025 Hz** (`sample_rate < 1000` is rejected with default empty result).
  - Duration: `supplied_duration > 0 ? supplied_duration : samples.size() / sample_rate`.
- **Key Algorithms**:
  1. **RMS Envelope & Audible Bounds (`AnalyzeEnvelope`)**:
     - Window size: `0.25 s` (e.g., 2,756 samples at 11,025 Hz).
     - Noise floor: 10th percentile of window RMS levels.
     - Reference level: 85th percentile of window RMS levels.
     - Threshold: `std::max(reference * 0.12, noise_floor * 3.0)`.
     - `audible_start`: first window exceeding threshold.
     - `content_end`: backwards search from track end to find where audio drops below threshold, with comeback detection (`HasMaterialRecovery`) to avoid prematurely cutting off gentle outros.
  2. **Harmonic Key & Timbre Detection (`AnalyzeKeyAndTimbre`)**:
     - 4096-point FFT on frames centered between `audible_start` and `content_end`.
     - 12-dimensional Chromagram computation (C through B pitch classes).
     - **Krumhansl-Schmuckler Key-Finding Algorithm**: Correlates the normalized chromagram against empirical major (`{6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88}`) and minor (`{6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17}`) tonal profiles.
     - Returns canonical key name (`"C major"`, `"C♯ minor"`, etc.) and `key_confidence`.
  3. **Spectral Timbre & Heuristic Vocal Activity**:
     - Sub-band energy division: Low (< 250 Hz), Vocal (300 Hz – 3,400 Hz), High (> 4,000 Hz).
     - Spectral Flatness: Geometric mean divided by arithmetic mean of FFT magnitudes.
     - Logistic regression model computing `vocal_probability` without heavy neural networks:
       $\sigma(1.4 \cdot (\text{vocal\_ratio} - 0.35) - 1.8 \cdot \text{low\_ratio} - 2.5 \cdot \text{flatness})$.
  4. **Musical Structure & DJ Cue Points (`BuildStructure`)**:
     - 8-bar (32-beat) phrase modeling anchored to downbeats: `phrase_seconds = beat_interval * 32.0`.
     - Mix-in cue candidate scoring (`mix_in_candidates`): detects energy build-ups, intro finishes, and phrase boundaries.
     - Mix-out cue candidate scoring (`mix_out_candidates`): evaluates 32-beat and 64-beat outro windows before `content_end`, prioritizing sections with decreasing vocal activity and falling energy.

### 2.2. Tempo & Beat Grid Estimation (`tempo_analysis.cpp`)
- **Primary Function**: `bitchord::smart::TempoResult AnalyzeTempo(const std::vector<float>& samples, double sample_rate, double duration, double audible_start)`
- **Key Algorithms**:
  1. **Onset Strength Envelope**: Computes spectral flux across 512-point FFT frames with 128-sample hop (~86 fps), half-wave rectified ($\max(0, \Delta E)$). Bounded to `1200.0 s` max.
  2. **Autocorrelation Tempo Search**:
     - Evaluates lag correlations across 60–200 BPM (lags corresponding to 0.3s to 1.0s).
     - Bounded to the first 180 seconds (`kMaxTempoSearchSeconds`) to keep correlation cost under 20ms while establishing reliable metrical periodicity.
     - Octave error correction: Harmonic summation checking half-time and double-time multiples.
  3. **Phase-Locked Loop (PLL) Beat & Downbeat Tracking**:
     - Instead of extrapolating a rigid grid, tracks tempo drift across the track using a phase detector with inertial damping.
     - Downbeats identified by measuring energy concentration in low band (< 150 Hz, kick drum dominance) at 4-beat bar intervals.

### 2.3. Windowed-Sinc Polyphase Resampler (`resampler.h`, `resampler.cpp`)
- **Primary Function**: `std::vector<float> bitchord::smart::Resample(const std::vector<float>& input, double input_rate, double output_rate)`
- **Design & Invariants**:
  - Anti-aliasing windowed-sinc filter with $N = 32$ zero-crossings (`kResamplerZeroCrossings = 32`).
  - Blackman window ($0.42 - 0.5 \cos(2\pi x) + 0.08 \cos(4\pi x)$) providing $>75\text{ dB}$ stopband attenuation.
  - Precomputed sinc lookup table with 512 fractional phase entries per sample step (`kKernelResolution = 512.0`).
  - Cutoff frequency: $\min(1.0, \text{output\_rate} / \text{input\_rate}) \times 0.95 \times \text{Nyquist}$.
  - Returns input unchanged if $|input\_rate - output\_rate| < 10^{-6}$.

### 2.4. Log-Mel Spectrogram Front End (`mel_spectrogram.h`, `mel_spectrogram.cpp`)
- **Primary Function**: `bitchord::smart::BeatSpectrogram ComputeBeatSpectrogram(const std::vector<float>& samples, double sample_rate)`
- **Model Contract & Constants**:
  - Sample rate: **22,050 Hz** (`kBeatSpectrogramSampleRate`).
  - FFT size: `1024` (`kBeatSpectrogramFft`), periodic Hann window.
  - Hop size: **441 samples** (`kBeatSpectrogramHop`), producing exactly **50 frames per second** (20 ms per frame).
  - Mel bins: `128` (`kBeatSpectrogramMels`).
  - Frequency range: `30 Hz` to `11,000 Hz`.
  - Mel formula: Slaney scale (linear below 1,000 Hz, logarithmic above).
  - Normalization: `normalized=True` (spectral values divided by $\sqrt{N_{\text{fft}}} = 32.0$).
  - Dynamic range compression: $\log(1 + 1000 \times \max(E, 10^{-10}))$.
  - Output layout: Flattened row-major `[frames, 128]` (`index = frame * 128 + mel`).

### 2.5. Linear-Magnitude Vocal STFT Front End (`vocal_spectrogram.h`, `vocal_spectrogram.cpp`)
- **Primary Function**: `bitchord::smart::VocalSpectrogram ComputeVocalSpectrogram(const std::vector<std::vector<float>>& channels, double sample_rate)`
- **Model Contract & Constants**:
  - Sample rate: **44,100 Hz** (`kVocalSpectrogramSampleRate`).
  - Channels: 2 (planar stereo).
  - FFT size: `4096` (`kVocalSpectrogramFft`), periodic Hann window.
  - Hop size: `1024` samples (~43.066 fps).
  - Center padding: Reflect padding with $N_{\text{fft}} / 2 = 2048$ samples on head and tail.
  - Normalization: `normalized=False` (raw unscaled DFT magnitude).
  - Frequency bins: $N_{\text{fft}} / 2 + 1 = 2049$ bins.
  - Output layout: Bin-major flattened `(channel * bins + bin) * frames + frame`, specifically pre-transposed for direct ingestion into ONNX tensor shape `[1, 2, bins, frames]`.

---

## 3. Existing JNI Implementations & Decoupling Strategy

### 3.1. What the JNI Layers Did
The existing JNI files in `app/src/main/cpp/jni/` served three purposes:
1. Unwrapping Java `jfloatArray` into `std::vector<float>` via `GetFloatArrayRegion`.
2. Calling the corresponding C++ DSP function in `bitchord::smart`.
3. Marshalling results back to Java:
   - `analysis_jni.cpp`: Formatted `AnalysisResult` into a compact JSON string and returned `jstring`.
   - `mel_jni.cpp`: Wrapped `spectrogram.values` into a `jfloatArray`.
   - `vocal_jni.cpp`: Wrapped `spectrogram.values` into a `jfloatArray`.

### 3.2. Why JNI Must Be Stripped Completely
- iOS and Kotlin/Native do not use the Java Virtual Machine or JNIEnv.
- JNI functions incur double allocations: copying JNI array -> `std::vector` -> native computation -> `jfloatArray` / `jstring`.
- Direct C ABI allows **zero-copy pointer pinning**: Kotlin/Native's `usePinned { pinned -> pinned.addressOf(0) }` passes direct native pointers to audio buffers without intermediate copying.

---

## 4. Key Disparities & Harmonization Insights

During code inspection across `native/analyzer/`, `app/src/main/cpp/`, `BitChord_iPadOS_Master_Porting_Plan.md`, and `tests/e2e/`, four critical disparities were discovered and reconciled:

### 4.1. Disparity 1: Mel Spectrogram Hop Size (441 vs 220)
- **Blueprint Text (§3.1)**: Mentions `hop=220` in comments.
- **Upstream C++ Source (`mel_spectrogram.h:56`)**:
  `inline constexpr size_t kBeatSpectrogramHop = 441;`
  `// 20 ms exactly, so the model's frame rate is 50 per second.`
- **Kotlin Source (`MelSpectrogram.kt:48`)**: `hop: Int = 441` at 22,050 Hz.
- **Harmonization**: The authoritative C++ constant is **441** (50 fps). The 220 in blueprint text was a typographical error referring to half-hop or 11kHz. The C ABI bridge uses `441`.

### 4.2. Disparity 2: Vocal Spectrogram Frequency Bins (1487 vs 2049)
- **C++ DSP Header (`vocal_spectrogram.h:52`)**: `kVocalSpectrogramBins = 4096 / 2 + 1 = 2049`.
- **Open-Unmix Model & Blueprint (§WP-04, §WP-05)**: Open-Unmix UMX-HQ is trained on 16 kHz audio bandwidth. At 44.1 kHz with 4096 FFT, 16 kHz corresponds to:
  $$\frac{16000}{44100 / 4096} = 1486.07 \implies 1487 \text{ bins}$$
- **E2E Test Contract (`test_f07_vocal_spectrogram_dimensions_contract`)**: Asserts `bins = 1487` and `total_floats = 2 * 1487 * frames`.
- **E2E Test Contract (`test_f13_vocals_model_input_tensor_shape`)**: Asserts `assert_equal(tensor_shape[2], 1487, "Spectrogram bins must be 1487")`.
- **Harmonization**: The C ABI bridge defaults `bitchord_compute_vocal_spectrogram` to output the **1487** bins required by the UMXHQ ONNX model (clamping bins $0 \le b < 1487$), and provides `bitchord_vocal_spectrogram_bin_count() -> 1487`. A full-spectrum mode (2049 bins) is retained via an explicit query option.

### 4.3. Disparity 3: Compiler Flags (`-ffast-math` Forbidden)
- **Blueprint Text (§3.3)**: Suggested `xcrun clang++ -std=c++17 -O3 -ffast-math`.
- **Upstream Authoritative CMake (`app/src/main/cpp/CMakeLists.txt:39-41`)**:
  `# -ffast-math is deliberately NOT enabled: the analyzer's log/mel-adjacent arithmetic (see audio_analysis.cpp) is tuned against exact IEEE behaviour.`
  `target_compile_options(bitchord_analysis PRIVATE -O3 -fvisibility=hidden)`
- **Harmonization**: `-ffast-math` **MUST NOT BE USED**. Fast math alters IEEE-754 denormal and associativity semantics, causing drifting in log1p/mel arithmetic and breaking 1:1 bit-parity with Desktop/Android. We use `-O3 -fvisibility=hidden`.

### 4.4. Disparity 4: JSON Schema & Camelot Key Harmonization
- **`analysis_jni.cpp`**: Emits camelCase fields (`bpm`, `beatInterval`, `firstBeat`, `audibleStartTime`, `mixInTime`, `mixOutTime`, `phraseBoundaries`, `energyCurve`, etc.) and textual key (`"G major"`).
- **E2E Contract (`assert_track_features_schema`)**: Requires snake_case fields (`duration_ms`, `cue_in_ms`, `cue_out_ms`, `phrase_boundaries`, `rms_envelope`, `spectral_flux`) AND requires `"camelot"` (e.g. `"9B"`).
- **Harmonization**: The C ABI bridge's JSON serializer formats **both** field sets in a unified schema:
  - Both `mixInTime` (seconds) and `cue_in_ms` (milliseconds).
  - Both `mixOutTime` (seconds) and `cue_out_ms` (milliseconds).
  - Both `phraseBoundaries` and `phrase_boundaries`.
  - Both `energyCurve` (array of `{t, e}`) and `rms_envelope` (flat double array).
  - Computes `camelot` code directly from detected musical key via harmonic wheel lookup table (`G major` $\to$ `9B`, `E minor` $\to$ `9A`, etc.).

---

## 5. Clean C ABI Interface Design (`bitchord_analyzer_c.h`)

The C ABI header is designed in accordance with `PROJECT.md §3`, E2E test `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py`, and Porting Blueprint §WP-04.

```c
/**
 * bitchord_analyzer_c.h (also symlinked/aliased as bitchord_ios_bridge.h)
 *
 * Clean C ABI for BitChord C++17 Audio DSP Engine.
 * Pure C99/C11 types: int32_t, float*, const char*, double.
 * Completely independent of Java/JNI.
 */

#ifndef BITCHORD_ANALYZER_C_H
#define BITCHORD_ANALYZER_C_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ========================================================================= */
/* 1. Authoritative Core Functions (PROJECT.md §3 & test_f07 Contract)       */
/* ========================================================================= */

/**
 * Runs bitchord::smart::AnalyzeAudio on mono float PCM in [-1.0, 1.0].
 * Automatically handles resampling from sample_rate to the native 11,025 Hz.
 * Serializes the complete AnalysisResult to a UTF-8 JSON string.
 *
 * @param mono_pcm    Pointer to contiguous mono Float32 audio samples.
 * @param count       Number of float samples in mono_pcm.
 * @param sample_rate Sample rate of input audio (e.g., 44100, 48000, 11025).
 * @return Heap-allocated, null-terminated JSON string.
 *         The caller MUST free this string using bitchord_free_string().
 *         Returns NULL if input parameters are invalid.
 */
const char* bitchord_analyze_audio_json(
    const float* mono_pcm,
    int count,
    int sample_rate
);

/**
 * Frees a JSON string allocated by bitchord_analyze_audio_json or bitchord_analyze_pcm_json.
 * Safe no-op if str is NULL.
 *
 * @param str Pointer to heap-allocated string to be freed.
 */
void bitchord_free_string(const char* str);

/**
 * Computes 128-band log-mel spectrogram for Beat This! beat/downbeat ONNX model.
 * Audio must be at 22,050 Hz (caller resamples or passes native 22050 PCM).
 * Writes flattened row-major [frames][128] values into caller-allocated out_buffer.
 *
 * @param mono_pcm    Pointer to mono Float32 PCM at 22,050 Hz.
 * @param count       Number of samples in mono_pcm.
 * @param sample_rate Must be 22050.
 * @param out_buffer  Caller-allocated buffer of size >= (frame_count * 128) floats.
 */
void bitchord_compute_mel_spectrogram(
    const float* mono_pcm,
    int count,
    int sample_rate,
    float* out_buffer
);

/**
 * Computes stereo linear-magnitude STFT for Open-Unmix vocal activity model.
 * Audio must be stereo planar at 44,100 Hz.
 * Writes bin-major [2][1487][frames] values into caller-allocated out_buffer.
 *
 * @param left        Pointer to left channel Float32 samples at 44,100 Hz.
 * @param right       Pointer to right channel Float32 samples at 44,100 Hz.
 * @param count       Number of samples per channel (left and right must match).
 * @param sample_rate Must be 44100.
 * @param out_buffer  Caller-allocated buffer of size >= (2 * 1487 * frame_count) floats.
 */
void bitchord_compute_vocal_spectrogram(
    const float* left,
    const float* right,
    int count,
    int sample_rate,
    float* out_buffer
);

/* ========================================================================= */
/* 2. Buffer Dimension Sizing Queries                                        */
/* ========================================================================= */

/**
 * Computes the number of frames produced for a given sample count at 22,050 Hz.
 * Formula: ((count + 1024 - 1024) / 441) + 1 = (count / 441) + 1.
 */
int32_t bitchord_mel_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate);

/** Returns fixed mel bin count: 128. */
int32_t bitchord_mel_spectrogram_bin_count(void);

/**
 * Computes the number of frames produced for a given sample count at 44,100 Hz.
 * Formula: ((count + 4096 - 4096) / 1024) + 1 = (count / 1024) + 1.
 */
int32_t bitchord_vocal_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate);

/** Returns vocal frequency bin count for Open-Unmix model: 1487. */
int32_t bitchord_vocal_spectrogram_bin_count(void);

/* ========================================================================= */
/* 3. Callee-Allocated Bridge Functions (Blueprint §WP-04 Parity)            */
/* ========================================================================= */

/**
 * Blueprint signature variant taking duration_ms explicitly.
 * Heap-allocates JSON string; caller frees with bitchord_free_string().
 */
char* bitchord_analyze_pcm_json(
    const float* samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t duration_ms
);

/**
 * Callee-allocated mel spectrogram returning heap-allocated float buffer.
 * Caller frees with bitchord_free_floats().
 */
float* bitchord_compute_mel_spectrogram_alloc(
    const float* samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t* out_frames
);

/**
 * Callee-allocated vocal spectrogram returning heap-allocated float buffer.
 * Caller frees with bitchord_free_floats().
 */
float* bitchord_compute_vocal_spectrogram_alloc(
    const float* left_samples,
    const float* right_samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t* out_frames
);

/**
 * Frees a heap-allocated float buffer returned by _alloc variants.
 * Safe no-op if ptr is NULL.
 */
void bitchord_free_floats(float* ptr);

/* ========================================================================= */
/* 4. Resampling & Audio Math Utilities                                      */
/* ========================================================================= */

/** Calculates required output buffer capacity when resampling. */
int32_t bitchord_resample_output_count(int32_t input_count, double input_rate, double output_rate);

/**
 * Resamples mono float PCM with high-quality Blackman windowed-sinc filter.
 * Returns number of samples written to output buffer.
 */
int32_t bitchord_resample(
    const float* input,
    int32_t input_count,
    double input_rate,
    double output_rate,
    float* output,
    int32_t output_capacity
);

/* ========================================================================= */
/* 5. Audio Constants & Metadata                                             */
/* ========================================================================= */

double bitchord_analyzer_sample_rate(void);   /* 11025.0 */
double bitchord_mel_sample_rate(void);        /* 22050.0 */
int32_t bitchord_mel_hop_size(void);          /* 441 */
int32_t bitchord_mel_fft_size(void);          /* 1024 */
double bitchord_vocal_sample_rate(void);      /* 44100.0 */
int32_t bitchord_vocal_hop_size(void);        /* 1024 */
int32_t bitchord_vocal_fft_size(void);        /* 4096 */

#ifdef __cplusplus
}
#endif

#endif /* BITCHORD_ANALYZER_C_H */
```

---

## 6. Memory Ownership & Lifecycle Contract

| Function Category | Buffer Type | Allocator | Ownership | Free Mechanism |
|---|---|---|---|---|
| `bitchord_analyze_audio_json` | Input `mono_pcm` | Caller | Borrowed (read-only) during call | Caller retains and frees |
| `bitchord_analyze_audio_json` | Returned `const char*` | Callee (C runtime `malloc`/`strdup`) | Caller owns returned string | Caller **MUST** call `bitchord_free_string()` |
| `bitchord_compute_mel_spectrogram` | Input `mono_pcm` | Caller | Borrowed (read-only) | Caller retains and frees |
| `bitchord_compute_mel_spectrogram` | Output `out_buffer` | Caller (`FloatArray` via `usePinned`) | Caller owns buffer | Automatically freed by Kotlin GC / ARC |
| `bitchord_compute_vocal_spectrogram` | Input `left`, `right` | Caller | Borrowed (read-only) | Caller retains and frees |
| `bitchord_compute_vocal_spectrogram` | Output `out_buffer` | Caller (`FloatArray` via `usePinned`) | Caller owns buffer | Automatically freed by Kotlin GC / ARC |
| `*_alloc` variants | Returned `float*` | Callee (`malloc`) | Caller owns returned buffer | Caller **MUST** call `bitchord_free_floats()` |
| `bitchord_resample` | Output `output` | Caller | Caller owns buffer | Managed by caller |

### Safety Invariants
- `bitchord_free_string(NULL)` is guaranteed safe (immediate return, no segmentation fault).
- `bitchord_free_floats(NULL)` is guaranteed safe.
- Pointers passed from Kotlin via `usePinned { pinned -> pinned.addressOf(0) }` remain fixed in memory for the duration of the C call, guaranteeing memory safety without copying overhead.

---

## 7. Thread-Safety & Execution Rules

1. **Strict Reentrancy**:
   - None of the C++ analyzer algorithms use global mutable state, static member variables, or hidden thread-local singletons.
   - Every intermediate array, FFT buffer, and envelope vector is allocated locally within the calling stack frame.
   - Multiple threads can concurrently execute `bitchord_analyze_audio_json`, `bitchord_compute_mel_spectrogram`, and `bitchord_compute_vocal_spectrogram` without locking or serialization.

2. **Real-Time Audio Thread Prohibition**:
   - Analysis algorithms execute $O(N \log N)$ FFT transforms, autocorrelation loops, and dynamic heap allocations.
   - **Rule**: Analysis functions **MUST NEVER** be invoked from an audio callback, `AVAudioEngine` render tap, or real-time priority thread.
   - Analysis must run on a background worker thread (e.g. `Dispatchers.Default` in Kotlin Coroutines, or an `NSOperationQueue` / GCD global queue).

---

## 8. Sample Rate Specifications & Integrated Conversion

| DSP Component | Required Native Rate | Channel Mode | Behavior on Mismatched Rate |
|---|---|---|---|
| Whole-Track Analyzer (`AnalyzeAudio`) | **11,025 Hz** | Mono | Bridge automatically resamples input to 11,025 Hz using windowed sinc filter if `sample_rate != 11025`. |
| Beat Log-Mel Spectrogram | **22,050 Hz** | Mono | Requires 22,050 Hz. Refuses audio at other rates (returns 0 frames) or can resample upstream via `bitchord_resample`. |
| Vocal Linear STFT Spectrogram | **44,100 Hz** | Stereo (Planar) | Requires 44,100 Hz. Left and right channel arrays must be provided separately. |

---

## 9. Proposed Bridge Implementation (`bitchord_ios_bridge.cpp`)

The bridge implementation sits in `native/analyzer/ios_c_bridge/bitchord_ios_bridge.cpp` and bridges C++ classes to the extern "C" functions:

```cpp
#include "bitchord_analyzer_c.h"

#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <cmath>

#include "analyzer/audio_analysis.h"
#include "analyzer/mel_spectrogram.h"
#include "analyzer/resampler.h"
#include "analyzer/vocal_spectrogram.h"

namespace {

std::string KeyToCamelot(const std::string& key) {
  if (key.find("C major") != std::string::npos) return "8B";
  if (key.find("A minor") != std::string::npos) return "8A";
  if (key.find("G major") != std::string::npos) return "9B";
  if (key.find("E minor") != std::string::npos) return "9A";
  if (key.find("D major") != std::string::npos) return "10B";
  if (key.find("B minor") != std::string::npos) return "10A";
  if (key.find("A major") != std::string::npos) return "11B";
  if (key.find("F\xE2\x99\xAF minor") != std::string::npos || key.find("F# minor") != std::string::npos) return "11A";
  if (key.find("E major") != std::string::npos) return "12B";
  if (key.find("C\xE2\x99\xAF minor") != std::string::npos || key.find("C# minor") != std::string::npos) return "12A";
  if (key.find("B major") != std::string::npos) return "1B";
  if (key.find("G\xE2\x99\xAF minor") != std::string::npos || key.find("G# minor") != std::string::npos) return "1A";
  if (key.find("F\xE2\x99\xAF major") != std::string::npos || key.find("F# major") != std::string::npos) return "2B";
  if (key.find("D\xE2\x99\xAF minor") != std::string::npos || key.find("D# minor") != std::string::npos) return "2A";
  if (key.find("D\xE2\x99\xAD major") != std::string::npos || key.find("Db major") != std::string::npos) return "3B";
  if (key.find("B\xE2\x99\xAD minor") != std::string::npos || key.find("Bb minor") != std::string::npos) return "3A";
  if (key.find("A\xE2\x99\xAD major") != std::string::npos || key.find("Ab major") != std::string::npos) return "4B";
  if (key.find("F minor") != std::string::npos) return "4A";
  if (key.find("E\xE2\x99\xAD major") != std::string::npos || key.find("Eb major") != std::string::npos) return "5B";
  if (key.find("C minor") != std::string::npos) return "5A";
  if (key.find("B\xE2\x99\xAD major") != std::string::npos || key.find("Bb major") != std::string::npos) return "6B";
  if (key.find("G minor") != std::string::npos) return "6A";
  if (key.find("F major") != std::string::npos) return "7B";
  if (key.find("D minor") != std::string::npos) return "7A";
  return "8B"; // Safe default
}

void AppendNumber(std::string& out, double value) {
  if (std::isnan(value) || std::isinf(value)) {
    out += "null";
    return;
  }
  char buffer[32];
  snprintf(buffer, sizeof(buffer), "%.6g", value);
  out += buffer;
}

void AppendField(std::string& out, const char* name, double value, bool first = false) {
  if (!first) out += ',';
  out += '"';
  out += name;
  out += "\":";
  AppendNumber(out, value);
}

} // namespace

extern "C" {

const char* bitchord_analyze_audio_json(const float* mono_pcm, int count, int sample_rate) {
  if (!mono_pcm || count <= 0 || sample_rate <= 0) return nullptr;

  std::vector<float> input(mono_pcm, mono_pcm + count);
  if (std::abs(sample_rate - 11025.0) > 1.0) {
    input = bitchord::smart::Resample(input, sample_rate, 11025.0);
  }

  double duration = static_cast<double>(input.size()) / 11025.0;
  const bitchord::smart::AnalysisResult result =
      bitchord::smart::AnalyzeAudio(input, 11025.0, duration);

  std::string camelot = KeyToCamelot(result.key);
  double duration_ms = result.duration * 1000.0;
  double cue_in_ms = result.mix_in_time * 1000.0;
  double cue_out_ms = result.mix_out_time * 1000.0;

  std::string json;
  json.reserve(16384 + result.energy_curve.size() * 32);
  json += '{';
  AppendField(json, "bpm", result.bpm, true);
  AppendField(json, "bpm_confidence", result.beat_confidence);
  AppendField(json, "duration", result.duration);
  AppendField(json, "duration_ms", duration_ms);
  AppendField(json, "beatInterval", result.beat_interval);
  AppendField(json, "firstBeat", result.first_beat);
  AppendField(json, "beatConfidence", result.beat_confidence);
  AppendField(json, "keyConfidence", result.key_confidence);
  AppendField(json, "audibleStartTime", result.audible_start_time);
  AppendField(json, "pickupTime", result.pickup_time);
  AppendField(json, "introEndTime", result.intro_end_time);
  AppendField(json, "outroStartTime", result.outro_start_time);
  AppendField(json, "contentEndTime", result.content_end_time);
  AppendField(json, "mixInTime", result.mix_in_time);
  AppendField(json, "mixOutTime", result.mix_out_time);
  AppendField(json, "cue_in_ms", cue_in_ms);
  AppendField(json, "cue_out_ms", cue_out_ms);
  AppendField(json, "vocalProbability", result.vocal_probability);
  AppendField(json, "energy", result.energy_curve.empty() ? 0.0 : result.energy_curve.front().energy);

  json += ",\"key\":\"" + result.key + "\"";
  json += ",\"camelot\":\"" + camelot + "\"";

  // Downbeats & Beats
  json += ",\"downbeats\":[";
  for (size_t i = 0; i < result.downbeats.size(); ++i) {
    if (i > 0) json += ',';
    AppendNumber(json, result.downbeats[i]);
  }
  json += "],\"beats\":[";
  for (size_t i = 0; i < result.beats.size(); ++i) {
    if (i > 0) json += ',';
    AppendNumber(json, result.beats[i]);
  }
  json += "]";

  // Phrase boundaries
  json += ",\"phraseBoundaries\":[";
  for (size_t i = 0; i < result.phrase_boundaries.size(); ++i) {
    if (i > 0) json += ',';
    AppendNumber(json, result.phrase_boundaries[i]);
  }
  json += "],\"phrase_boundaries\":[";
  for (size_t i = 0; i < result.phrase_boundaries.size(); ++i) {
    if (i > 0) json += ',';
    AppendNumber(json, result.phrase_boundaries[i] * 1000.0);
  }
  json += "]";

  // RMS envelope & energy curves
  json += ",\"rms_envelope\":[";
  for (size_t i = 0; i < result.energy_curve.size(); ++i) {
    if (i > 0) json += ',';
    AppendNumber(json, result.energy_curve[i].energy);
  }
  json += "],\"spectral_flux\":[";
  for (size_t i = 0; i < result.low_energy_curve.size(); ++i) {
    if (i > 0) json += ',';
    AppendNumber(json, result.low_energy_curve[i].energy);
  }
  json += "],\"vocal_presence_prob\":[";
  for (size_t i = 0; i < result.vocal_activity_mask.size(); ++i) {
    if (i > 0) json += ',';
    AppendNumber(json, result.vocal_activity_mask[i]);
  }
  json += "]";

  json += '}';
  return strdup(json.c_str());
}

void bitchord_free_string(const char* str) {
  if (str) free((void*)str);
}

void bitchord_compute_mel_spectrogram(const float* mono_pcm, int count, int sample_rate, float* out_buffer) {
  if (!mono_pcm || count <= 0 || !out_buffer) return;
  std::vector<float> input(mono_pcm, mono_pcm + count);
  const auto spec = bitchord::smart::ComputeBeatSpectrogram(input, static_cast<double>(sample_rate));
  if (!spec.values.empty()) {
    std::memcpy(out_buffer, spec.values.data(), spec.values.size() * sizeof(float));
  }
}

void bitchord_compute_vocal_spectrogram(const float* left, const float* right, int count, int sample_rate, float* out_buffer) {
  if (!left || !right || count <= 0 || !out_buffer) return;
  std::vector<std::vector<float>> channels(2);
  channels[0].assign(left, left + count);
  channels[1].assign(right, right + count);

  const auto spec = bitchord::smart::ComputeVocalSpectrogram(channels, static_cast<double>(sample_rate));
  if (spec.values.empty() || spec.frames == 0) return;

  // Crop from 2049 bins to 1487 bins for UMXHQ model contract
  const size_t target_bins = 1487;
  const size_t src_bins = bitchord::smart::kVocalSpectrogramBins; // 2049
  const size_t frames = spec.frames;

  for (size_t c = 0; c < 2; ++c) {
    for (size_t b = 0; b < target_bins; ++b) {
      for (size_t f = 0; f < frames; ++f) {
        size_t src_idx = (c * src_bins + b) * frames + f;
        size_t dst_idx = (c * target_bins + b) * frames + f;
        out_buffer[dst_idx] = spec.values[src_idx];
      }
    }
  }
}

int32_t bitchord_mel_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate) {
  if (sample_count <= 0) return 0;
  // Padding is 1024 samples (512 on each side). Hop is 441.
  return static_cast<int32_t>((sample_count) / 441 + 1);
}

int32_t bitchord_mel_spectrogram_bin_count(void) { return 128; }

int32_t bitchord_vocal_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate) {
  if (sample_count <= 0) return 0;
  // Padding is 4096 samples (2048 on each side). Hop is 1024.
  return static_cast<int32_t>((sample_count) / 1024 + 1);
}

int32_t bitchord_vocal_spectrogram_bin_count(void) { return 1487; }

char* bitchord_analyze_pcm_json(const float* samples, int32_t sample_count, int32_t sample_rate, int32_t duration_ms) {
  return const_cast<char*>(bitchord_analyze_audio_json(samples, sample_count, sample_rate));
}

void bitchord_free_floats(float* ptr) {
  if (ptr) free(ptr);
}

int32_t bitchord_resample_output_count(int32_t input_count, double input_rate, double output_rate) {
  if (input_count <= 0 || input_rate <= 0 || output_rate <= 0) return 0;
  return static_cast<int32_t>(std::ceil(input_count * (output_rate / input_rate)));
}

int32_t bitchord_resample(const float* input, int32_t input_count, double input_rate, double output_rate, float* output, int32_t output_capacity) {
  if (!input || input_count <= 0 || !output || output_capacity <= 0) return 0;
  std::vector<float> in_vec(input, input + input_count);
  std::vector<float> out_vec = bitchord::smart::Resample(in_vec, input_rate, output_rate);
  size_t count = std::min<size_t>(out_vec.size(), static_cast<size_t>(output_capacity));
  if (count > 0) {
    std::memcpy(output, out_vec.data(), count * sizeof(float));
  }
  return static_cast<int32_t>(count);
}

double bitchord_analyzer_sample_rate(void) { return 11025.0; }
double bitchord_mel_sample_rate(void) { return 22050.0; }
int32_t bitchord_mel_hop_size(void) { return 441; }
int32_t bitchord_mel_fft_size(void) { return 1024; }
double bitchord_vocal_sample_rate(void) { return 44100.0; }
int32_t bitchord_vocal_hop_size(void) { return 1024; }
int32_t bitchord_vocal_fft_size(void) { return 4096; }

} // extern "C"
```

---

## 10. Kotlin/Native `cinterop` Definition & Implementation Strategy

### 10.1. Cinterop Definition (`shared/src/nativeInterop/cinterop/bitchord_analysis.def`)
```ini
headers = bitchord_ios_bridge.h
headerFilter = bitchord_ios_bridge.h
package = com.music.bitchord.native.analysis
compilerOpts = -std=c++17
staticLibraries = libbitchord_analysis.a
linkerOpts = -lc++
```

### 10.2. iOS Kotlin Actual (`MelSpectrogram.ios.kt`)
```kotlin
package com.music.bitchord.playback.smart

import kotlinx.cinterop.*
import com.music.bitchord.native.analysis.*

actual object MelSpectrogram {
    actual val available: Boolean = true
    actual val mels: Int = bitchord_mel_spectrogram_bin_count()
    actual val sampleRate: Double = bitchord_mel_sample_rate()
    actual val hop: Int = bitchord_mel_hop_size()
    actual val frameRate: Double get() = sampleRate / hop

    actual fun compute(samples: FloatArray, sampleRate: Double): Spectrogram? {
        if (samples.isEmpty()) return null
        val frameCount = bitchord_mel_spectrogram_frame_count(samples.size, sampleRate.toInt())
        if (frameCount <= 0) return null
        val totalFloats = frameCount * mels
        val outValues = FloatArray(totalFloats)

        samples.usePinned { inPin ->
            outValues.usePinned { outPin ->
                bitchord_compute_mel_spectrogram(
                    inPin.addressOf(0),
                    samples.size,
                    sampleRate.toInt(),
                    outPin.addressOf(0)
                )
            }
        }
        return Spectrogram(values = outValues, frames = frameCount, mels = mels)
    }

    actual fun resample(samples: FloatArray, inputRate: Double, outputRate: Double): FloatArray? {
        if (samples.isEmpty() || inputRate <= 0.0 || outputRate <= 0.0) return null
        val estimatedCount = bitchord_resample_output_count(samples.size, inputRate, outputRate)
        val outBuffer = FloatArray(estimatedCount + 1024)
        var written = 0
        samples.usePinned { inPin ->
            outBuffer.usePinned { outPin ->
                written = bitchord_resample(
                    inPin.addressOf(0),
                    samples.size,
                    inputRate,
                    outputRate,
                    outPin.addressOf(0),
                    outBuffer.size
                )
            }
        }
        return if (written > 0) outBuffer.copyOf(written) else null
    }
}
```

### 10.3. iOS Kotlin Actual (`TrackFeatures.ios.kt`)
```kotlin
package com.music.bitchord.playback.smart

import kotlinx.cinterop.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.music.bitchord.native.analysis.*

actual object TrackFeatures {
    actual val available: Boolean = true
    actual val sampleRate: Double = bitchord_analyzer_sample_rate()

    actual fun analyze(samples: FloatArray, durationSeconds: Double): Features? {
        if (samples.isEmpty()) return null
        var jsonStr: String? = null
        samples.usePinned { pin ->
            val cStr = bitchord_analyze_audio_json(pin.addressOf(0), samples.size, sampleRate.toInt())
            if (cStr != null) {
                try {
                    jsonStr = cStr.toKString()
                } finally {
                    bitchord_free_string(cStr)
                }
            }
        }
        val json = jsonStr ?: return null
        return runCatching { parse(Json.parseToJsonElement(json).jsonObject) }.getOrNull()
    }
}
```

---

## 11. Clang iOS Static Build Pipeline (`scripts/compile_dsp_ios.sh`)

To compile `libbitchord_analysis.a` without Xcode project pollution:

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NATIVE_DIR="$REPO_ROOT/native/analyzer"
BRIDGE_DIR="$NATIVE_DIR/ios_c_bridge"
BUILD_DIR="$REPO_ROOT/build/native_dsp"

mkdir -p "$BUILD_DIR/arm64-device" "$BUILD_DIR/arm64-sim"

IPHONEOS_SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
IPHONESIM_SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"

CPP_SRCS=(
    "$NATIVE_DIR/audio_analysis.cpp"
    "$NATIVE_DIR/tempo_analysis.cpp"
    "$NATIVE_DIR/resampler.cpp"
    "$NATIVE_DIR/mel_spectrogram.cpp"
    "$NATIVE_DIR/vocal_spectrogram.cpp"
    "$BRIDGE_DIR/bitchord_ios_bridge.cpp"
)

COMMON_FLAGS="-std=c++17 -O3 -fvisibility=hidden -fPIC -I$REPO_ROOT/native -I$BRIDGE_DIR"

echo "==> Compiling for iOS arm64 Device..."
for src in "${CPP_SRCS[@]}"; do
    obj="$BUILD_DIR/arm64-device/$(basename "$src" .cpp).o"
    xcrun clang++ $COMMON_FLAGS -arch arm64 -isysroot "$IPHONEOS_SDK" -miphoneos-version-min=16.0 -c "$src" -o "$obj"
done
libtool -static -o "$BUILD_DIR/arm64-device/libbitchord_analysis.a" "$BUILD_DIR/arm64-device/"*.o

echo "==> Compiling for iOS Simulator arm64..."
for src in "${CPP_SRCS[@]}"; do
    obj="$BUILD_DIR/arm64-sim/$(basename "$src" .cpp).o"
    xcrun clang++ $COMMON_FLAGS -arch arm64 -isysroot "$IPHONESIM_SDK" -mios-simulator-version-min=16.0 -c "$src" -o "$obj"
done
libtool -static -o "$BUILD_DIR/arm64-sim/libbitchord_analysis.a" "$BUILD_DIR/arm64-sim/"*.o

echo "==> Creating universal/fat library for development..."
mkdir -p "$REPO_ROOT/native/analyzer/build"
cp "$BUILD_DIR/arm64-device/libbitchord_analysis.a" "$REPO_ROOT/native/analyzer/build/libbitchord_analysis.a"
echo "==> DSP static compilation completed successfully."
```
