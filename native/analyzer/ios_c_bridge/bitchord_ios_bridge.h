/*
 * BitChord iOS C ABI Bridge
 *
 * Exposes a pure C99/C11 ABI for BitChord's dependency-free C++17 DSP engine.
 * Operating exclusively on primitive types and raw float pointers.
 * Safe for direct zero-copy Kotlin/Native cinterop binding.
 */

#ifndef BITCHORD_IOS_BRIDGE_H
#define BITCHORD_IOS_BRIDGE_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ========================================================================= */
/* 1. Authoritative Core Functions (PROJECT.md §3 & E2E Contracts)           */
/* ========================================================================= */

/**
 * Runs whole-track DSP analysis (envelope, key, tempo, mix cues, structure) on
 * mono float PCM in range [-1.0, 1.0].
 * Automatically resamples to the internal 11,025 Hz rate if needed.
 * Serializes the result to a heap-allocated UTF-8 JSON string.
 *
 * @param mono_pcm    Pointer to contiguous mono Float32 audio samples.
 * @param count       Number of float samples in mono_pcm.
 * @param sample_rate Sample rate of input audio (e.g. 44100, 48000, 22050, 11025).
 * @return Heap-allocated, null-terminated JSON string.
 *         Caller MUST free this memory using bitchord_free_string().
 *         Returns NULL if input parameters are invalid.
 */
const char* bitchord_analyze_audio_json(
    const float* mono_pcm,
    int count,
    int sample_rate
);

/**
 * Frees a JSON string allocated by bitchord_analyze_audio_json.
 * Safe no-op if str is NULL.
 *
 * @param str Pointer to heap-allocated string to be freed.
 */
void bitchord_free_string(const char* str);

/**
 * Computes 128-band log-mel spectrogram for Beat This! ONNX beat tracker.
 * Calibrated for 22,050 Hz, FFT size 1024, hop size 441 (50 fps).
 * Writes flattened row-major [frames * 128] float values into out_buffer.
 *
 * @param mono_pcm    Pointer to mono Float32 PCM.
 * @param count       Number of samples in mono_pcm.
 * @param sample_rate Audio sample rate (resampled to 22,050 Hz if different).
 * @param out_buffer  Caller-allocated buffer of size >= frames * 128 floats.
 */
void bitchord_compute_mel_spectrogram(
    const float* mono_pcm,
    int count,
    int sample_rate,
    float* out_buffer
);

/**
 * Computes stereo linear-magnitude STFT for Open-Unmix vocal separation model.
 * Calibrated for 44,100 Hz, FFT size 4096, hop size 1024 (~43 fps).
 * Writes bin-major [2 * 1487 * frames] float values into out_buffer
 * (clamped to Open-Unmix 16 kHz cutoff: 1487 STFT bins).
 *
 * @param left        Pointer to left channel Float32 samples.
 * @param right       Pointer to right channel Float32 samples.
 * @param count       Number of samples per channel (left and right must match).
 * @param sample_rate Audio sample rate (resampled to 44,100 Hz if different).
 * @param out_buffer  Caller-allocated buffer of size >= 2 * 1487 * frames floats.
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

int32_t bitchord_mel_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate);
int32_t bitchord_mel_spectrogram_bin_count(void);
int32_t bitchord_vocal_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate);
int32_t bitchord_vocal_spectrogram_bin_count(void);

/* ========================================================================= */
/* 3. Callee-Allocated Bridge Functions                                      */
/* ========================================================================= */

char* bitchord_analyze_pcm_json(
    const float* samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t duration_ms
);

float* bitchord_compute_mel_spectrogram_alloc(
    const float* samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t* out_frames
);

float* bitchord_compute_vocal_spectrogram_alloc(
    const float* left_samples,
    const float* right_samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t* out_frames
);

void bitchord_free_floats(float* ptr);

/* ========================================================================= */
/* 4. Resampling & Audio Math Utilities                                      */
/* ========================================================================= */

int32_t bitchord_resample_output_count(int32_t input_count, double input_rate, double output_rate);

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

double bitchord_analyzer_sample_rate(void);
double bitchord_mel_sample_rate(void);
int32_t bitchord_mel_hop_size(void);
int32_t bitchord_mel_fft_size(void);
double bitchord_vocal_sample_rate(void);
int32_t bitchord_vocal_hop_size(void);
int32_t bitchord_vocal_fft_size(void);

#ifdef __cplusplus
}
#endif

#endif /* BITCHORD_IOS_BRIDGE_H */
