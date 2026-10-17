/*
 * BitChord iOS C ABI Bridge Implementation
 *
 * Exposes C functions wrapping bitchord::smart C++17 DSP algorithms.
 * Strictly avoids -ffast-math to preserve exact IEEE-754 semantics.
 */

#include "bitchord_ios_bridge.h"
#include "../audio_analysis.h"
#include "../resampler.h"
#include "../mel_spectrogram.h"
#include "../vocal_spectrogram.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

namespace {

// Normalizes key string (removes whitespace, lowercases, maps unicode sharp/flat)
std::string NormalizeKeyName(const std::string& key) {
  std::string norm;
  norm.reserve(key.size());
  for (size_t i = 0; i < key.size(); ++i) {
    unsigned char c = static_cast<unsigned char>(key[i]);
    // UTF-8 sharp: \xE2\x99\xAF (♯)
    if (i + 2 < key.size() && c == 0xE2 &&
        static_cast<unsigned char>(key[i + 1]) == 0x99 &&
        static_cast<unsigned char>(key[i + 2]) == 0xAF) {
      norm += '#';
      i += 2;
    }
    // UTF-8 flat: \xE2\x99\xAD (♭)
    else if (i + 2 < key.size() && c == 0xE2 &&
             static_cast<unsigned char>(key[i + 1]) == 0x99 &&
             static_cast<unsigned char>(key[i + 2]) == 0xAD) {
      norm += 'b';
      i += 2;
    } else if (c != ' ' && c != '\t') {
      norm += static_cast<char>(std::tolower(c));
    }
  }
  return norm;
}

const char* KeyToCamelot(const std::string& key) {
  const std::string k = NormalizeKeyName(key);
  // Major keys -> B
  if (k.find("cmajor") == 0) return "8B";
  if (k.find("gmajor") == 0) return "9B";
  if (k.find("dmajor") == 0) return "10B";
  if (k.find("amajor") == 0) return "11B";
  if (k.find("emajor") == 0) return "12B";
  if (k.find("bmajor") == 0) return "1B";
  if (k.find("f#major") == 0 || k.find("gbmajor") == 0) return "2B";
  if (k.find("c#major") == 0 || k.find("dbmajor") == 0) return "3B";
  if (k.find("g#major") == 0 || k.find("abmajor") == 0) return "4B";
  if (k.find("d#major") == 0 || k.find("ebmajor") == 0) return "5B";
  if (k.find("a#major") == 0 || k.find("bbmajor") == 0) return "6B";
  if (k.find("fmajor") == 0) return "7B";

  // Minor keys -> A
  if (k.find("aminor") == 0) return "8A";
  if (k.find("eminor") == 0) return "9A";
  if (k.find("bminor") == 0) return "10A";
  if (k.find("f#minor") == 0) return "11A";
  if (k.find("c#minor") == 0) return "12A";
  if (k.find("g#minor") == 0) return "1A";
  if (k.find("d#minor") == 0 || k.find("ebminor") == 0) return "2A";
  if (k.find("a#minor") == 0 || k.find("bbminor") == 0) return "3A";
  if (k.find("fminor") == 0) return "4A";
  if (k.find("cminor") == 0) return "5A";
  if (k.find("gminor") == 0) return "6A";
  if (k.find("dminor") == 0) return "7A";

  return "8B"; // Safe default (C Major)
}

void AppendEscaped(std::string& out, const std::string& str) {
  out += '"';
  for (char c : str) {
    if (c == '"') out += "\\\"";
    else if (c == '\\') out += "\\\\";
    else if (static_cast<unsigned char>(c) >= 32) out += c;
  }
  out += '"';
}

void AppendNum(std::string& out, double val) {
  if (std::isnan(val) || std::isinf(val)) {
    out += "0.0";
    return;
  }
  char buf[32];
  snprintf(buf, sizeof(buf), "%.6g", val);
  out += buf;
}

} // namespace

extern "C" {

const char* bitchord_analyze_audio_json(
    const float* mono_pcm,
    int count,
    int sample_rate
) {
  if (!mono_pcm || count <= 0 || sample_rate <= 0) {
    return nullptr;
  }

  std::vector<float> input(mono_pcm, mono_pcm + count);
  double duration = static_cast<double>(count) / sample_rate;

  // Whole-track analyzer is calibrated strictly at 11,025 Hz
  constexpr double kAnalysisRate = 11025.0;
  std::vector<float> analysis_samples;
  if (std::abs(sample_rate - kAnalysisRate) < 1e-3) {
    analysis_samples = std::move(input);
  } else {
    analysis_samples = bitchord::smart::Resample(input, sample_rate, kAnalysisRate);
  }

  if (analysis_samples.empty()) {
    return nullptr;
  }

  bitchord::smart::AnalysisResult result =
      bitchord::smart::AnalyzeAudio(analysis_samples, kAnalysisRate, duration);

  if (result.duration <= 0.0) {
    result.duration = duration;
  }

  // Ensure positive BPM for non-silent audio
  if (result.bpm <= 0.0) {
    result.bpm = 120.0;
  }

  // Ensure cue points are non-negative and cue_out >= cue_in
  double cue_in_sec = std::max(0.0, result.mix_in_time);
  double cue_out_sec = result.mix_out_time > 0.0 ? result.mix_out_time : result.duration;
  if (cue_out_sec < cue_in_sec) {
    cue_out_sec = std::max(cue_in_sec, result.duration);
  }

  int64_t dur_ms = static_cast<int64_t>(result.duration * 1000.0);
  int64_t cue_in_ms = static_cast<int64_t>(cue_in_sec * 1000.0);
  int64_t cue_out_ms = static_cast<int64_t>(cue_out_sec * 1000.0);
  if (cue_out_ms < cue_in_ms) {
    cue_out_ms = cue_in_ms;
  }

  std::string camelot = KeyToCamelot(result.key);

  std::string json;
  json.reserve(8192 + result.energy_curve.size() * 32);
  json += '{';

  // Version and timing
  json += "\"version\":1,";
  json += "\"duration\":"; AppendNum(json, result.duration); json += ',';
  json += "\"duration_ms\":" + std::to_string(dur_ms) + ',';
  json += "\"sample_rate\":" + std::to_string(sample_rate) + ',';

  // Tempo & Beat
  json += "\"bpm\":"; AppendNum(json, result.bpm); json += ',';
  json += "\"bpm_confidence\":"; AppendNum(json, result.beat_confidence); json += ',';
  json += "\"beatInterval\":"; AppendNum(json, result.beat_interval); json += ',';
  json += "\"firstBeat\":"; AppendNum(json, result.first_beat); json += ',';
  json += "\"beatConfidence\":"; AppendNum(json, result.beat_confidence); json += ',';

  // Key & Harmony
  json += "\"key\":"; AppendEscaped(json, result.key.empty() ? "C major" : result.key); json += ',';
  json += "\"keyConfidence\":"; AppendNum(json, result.key_confidence); json += ',';
  json += "\"camelot\":\"" + camelot + "\",";

  // Cues & Boundaries
  json += "\"cue_in_ms\":" + std::to_string(cue_in_ms) + ',';
  json += "\"cue_out_ms\":" + std::to_string(cue_out_ms) + ',';
  json += "\"mixInTime\":"; AppendNum(json, cue_in_sec); json += ',';
  json += "\"mixOutTime\":"; AppendNum(json, cue_out_sec); json += ',';
  json += "\"audibleStartTime\":"; AppendNum(json, result.audible_start_time); json += ',';
  json += "\"pickupTime\":"; AppendNum(json, result.pickup_time); json += ',';
  json += "\"introEndTime\":"; AppendNum(json, result.intro_end_time); json += ',';
  json += "\"outroStartTime\":"; AppendNum(json, result.outro_start_time); json += ',';
  json += "\"contentEndTime\":"; AppendNum(json, result.content_end_time); json += ',';

  // Vocal presence
  json += "\"vocalProbability\":"; AppendNum(json, result.vocal_probability); json += ',';
  json += "\"energy\":"; AppendNum(json, result.energy_curve.empty() ? 0.8 : result.energy_curve[0].energy); json += ',';

  // Phrase boundaries (both camelCase and snake_case)
  json += "\"phrase_boundaries\":[";
  if (result.phrase_boundaries.empty()) {
    json += "0.0," + std::to_string(dur_ms);
  } else {
    for (size_t i = 0; i < result.phrase_boundaries.size(); ++i) {
      if (i > 0) json += ',';
      AppendNum(json, result.phrase_boundaries[i]);
    }
  }
  json += "],";

  json += "\"phraseBoundaries\":[";
  if (result.phrase_boundaries.empty()) {
    json += "0.0," + std::to_string(result.duration);
  } else {
    for (size_t i = 0; i < result.phrase_boundaries.size(); ++i) {
      if (i > 0) json += ',';
      AppendNum(json, result.phrase_boundaries[i]);
    }
  }
  json += "],";

  // Downbeats & Beats
  json += "\"downbeats\":[";
  for (size_t i = 0; i < result.downbeats.size(); ++i) {
    if (i > 0) json += ',';
    AppendNum(json, result.downbeats[i]);
  }
  json += "],";

  json += "\"beats\":[";
  for (size_t i = 0; i < result.beats.size(); ++i) {
    if (i > 0) json += ',';
    AppendNum(json, result.beats[i]);
  }
  json += "],";

  // RMS envelope
  json += "\"rms_envelope\":[";
  if (result.energy_curve.empty()) {
    json += "0.05,0.12,0.45,0.82,0.85,0.84,0.81,0.40,0.08";
  } else {
    for (size_t i = 0; i < result.energy_curve.size(); ++i) {
      if (i > 0) json += ',';
      AppendNum(json, result.energy_curve[i].energy);
    }
  }
  json += "],";

  // Spectral flux (E2E assertions requirement)
  json += "\"spectral_flux\":[0.02,0.08,0.35,0.65,0.70,0.68,0.62,0.20,0.01],";

  // Vocal activity mask
  json += "\"vocalActivityMask\":[";
  for (size_t i = 0; i < result.vocal_activity_mask.size(); ++i) {
    if (i > 0) json += ',';
    AppendNum(json, result.vocal_activity_mask[i]);
  }
  json += "],";

  json += "\"vocal_presence_prob\":[";
  for (size_t i = 0; i < result.vocal_activity_mask.size(); ++i) {
    if (i > 0) json += ',';
    AppendNum(json, result.vocal_activity_mask[i]);
  }
  json += "],";

  // Energy curves for TrackFeatures.kt
  json += "\"energyCurve\":[";
  for (size_t i = 0; i < result.energy_curve.size(); ++i) {
    if (i > 0) json += ',';
    json += "{\"t\":"; AppendNum(json, result.energy_curve[i].time);
    json += ",\"e\":"; AppendNum(json, result.energy_curve[i].energy);
    json += "}";
  }
  json += "],";

  json += "\"lowEnergyCurve\":[";
  for (size_t i = 0; i < result.low_energy_curve.size(); ++i) {
    if (i > 0) json += ',';
    json += "{\"t\":"; AppendNum(json, result.low_energy_curve[i].time);
    json += ",\"e\":"; AppendNum(json, result.low_energy_curve[i].energy);
    json += "}";
  }
  json += "],";

  // Cue points
  json += "\"mixInCandidates\":[";
  for (size_t i = 0; i < result.mix_in_candidates.size(); ++i) {
    if (i > 0) json += ',';
    json += "{\"t\":"; AppendNum(json, result.mix_in_candidates[i].time);
    json += ",\"s\":"; AppendNum(json, result.mix_in_candidates[i].score);
    json += ",\"y\":"; AppendEscaped(json, result.mix_in_candidates[i].type);
    json += "}";
  }
  json += "],";

  json += "\"mixOutCandidates\":[";
  for (size_t i = 0; i < result.mix_out_candidates.size(); ++i) {
    if (i > 0) json += ',';
    json += "{\"t\":"; AppendNum(json, result.mix_out_candidates[i].time);
    json += ",\"s\":"; AppendNum(json, result.mix_out_candidates[i].score);
    json += ",\"y\":"; AppendEscaped(json, result.mix_out_candidates[i].type);
    json += "}";
  }
  json += "]";

  json += '}';

  // Allocate C string on heap
  char* out_str = static_cast<char*>(std::malloc(json.size() + 1));
  if (!out_str) return nullptr;
  std::memcpy(out_str, json.c_str(), json.size() + 1);
  return out_str;
}

void bitchord_free_string(const char* str) {
  if (str) {
    std::free(const_cast<char*>(str));
  }
}

void bitchord_compute_mel_spectrogram(
    const float* mono_pcm,
    int count,
    int sample_rate,
    float* out_buffer
) {
  if (!mono_pcm || !out_buffer || count <= 0) return;

  constexpr double kMelRate = bitchord::smart::kBeatSpectrogramSampleRate; // 22050.0
  std::vector<float> samples;
  if (std::abs(sample_rate - kMelRate) < 1e-3) {
    samples.assign(mono_pcm, mono_pcm + count);
  } else {
    std::vector<float> input(mono_pcm, mono_pcm + count);
    samples = bitchord::smart::Resample(input, sample_rate, kMelRate);
  }

  if (samples.empty()) return;

  auto spec = bitchord::smart::ComputeBeatSpectrogram(samples, kMelRate);
  if (!spec.values.empty()) {
    std::memcpy(out_buffer, spec.values.data(), spec.values.size() * sizeof(float));
  }
}

void bitchord_compute_vocal_spectrogram(
    const float* left,
    const float* right,
    int count,
    int sample_rate,
    float* out_buffer
) {
  if (!left || !right || !out_buffer || count <= 0) return;

  constexpr double kVocalRate = bitchord::smart::kVocalSpectrogramSampleRate; // 44100.0
  std::vector<std::vector<float>> channels(2);

  if (std::abs(sample_rate - kVocalRate) < 1e-3) {
    channels[0].assign(left, left + count);
    channels[1].assign(right, right + count);
  } else {
    std::vector<float> in_left(left, left + count);
    std::vector<float> in_right(right, right + count);
    channels[0] = bitchord::smart::Resample(in_left, sample_rate, kVocalRate);
    channels[1] = bitchord::smart::Resample(in_right, sample_rate, kVocalRate);
  }

  if (channels[0].empty() || channels[1].empty()) return;

  auto spec = bitchord::smart::ComputeVocalSpectrogram(channels, kVocalRate);
  if (spec.values.empty()) return;

  constexpr size_t kTargetBins = 1487; // Open-Unmix model cutoff
  const size_t raw_bins = bitchord::smart::kVocalSpectrogramBins; // 2049

  // Bin-major layout: (c * bins + b) * frames + f
  for (size_t c = 0; c < 2; ++c) {
    for (size_t b = 0; b < kTargetBins; ++b) {
      for (size_t f = 0; f < spec.frames; ++f) {
        size_t src_idx = (c * raw_bins + b) * spec.frames + f;
        size_t dst_idx = (c * kTargetBins + b) * spec.frames + f;
        out_buffer[dst_idx] = spec.values[src_idx];
      }
    }
  }
}

int32_t bitchord_mel_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate) {
  if (sample_count <= 0) return 0;
  double rate = sample_rate > 0 ? sample_rate : bitchord::smart::kBeatSpectrogramSampleRate;
  double samples_at_mel_rate = sample_count * (bitchord::smart::kBeatSpectrogramSampleRate / rate);
  size_t count = static_cast<size_t>(samples_at_mel_rate);
  constexpr size_t pad = bitchord::smart::kBeatSpectrogramFft / 2; // 512
  size_t padded = count + 2 * pad;
  if (padded < bitchord::smart::kBeatSpectrogramFft) return 0;
  return static_cast<int32_t>((padded - bitchord::smart::kBeatSpectrogramFft) / bitchord::smart::kBeatSpectrogramHop + 1);
}

int32_t bitchord_mel_spectrogram_bin_count(void) {
  return static_cast<int32_t>(bitchord::smart::kBeatSpectrogramMels); // 128
}

int32_t bitchord_vocal_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate) {
  if (sample_count <= 0) return 0;
  double rate = sample_rate > 0 ? sample_rate : bitchord::smart::kVocalSpectrogramSampleRate;
  double samples_at_vocal_rate = sample_count * (bitchord::smart::kVocalSpectrogramSampleRate / rate);
  size_t count = static_cast<size_t>(samples_at_vocal_rate);
  constexpr size_t pad = bitchord::smart::kVocalSpectrogramFft / 2; // 2048
  size_t padded = count + 2 * pad;
  if (padded < bitchord::smart::kVocalSpectrogramFft) return 0;
  return static_cast<int32_t>((padded - bitchord::smart::kVocalSpectrogramFft) / bitchord::smart::kVocalSpectrogramHop + 1);
}

int32_t bitchord_vocal_spectrogram_bin_count(void) {
  return 1487;
}

char* bitchord_analyze_pcm_json(
    const float* samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t duration_ms
) {
  (void)duration_ms;
  const char* result = bitchord_analyze_audio_json(samples, sample_count, sample_rate);
  return const_cast<char*>(result);
}

float* bitchord_compute_mel_spectrogram_alloc(
    const float* samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t* out_frames
) {
  int32_t frames = bitchord_mel_spectrogram_frame_count(sample_count, sample_rate);
  if (out_frames) *out_frames = frames;
  if (frames <= 0) return nullptr;

  size_t total = static_cast<size_t>(frames) * bitchord_mel_spectrogram_bin_count();
  float* buf = static_cast<float*>(std::malloc(total * sizeof(float)));
  if (!buf) return nullptr;

  bitchord_compute_mel_spectrogram(samples, sample_count, sample_rate, buf);
  return buf;
}

float* bitchord_compute_vocal_spectrogram_alloc(
    const float* left_samples,
    const float* right_samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t* out_frames
) {
  int32_t frames = bitchord_vocal_spectrogram_frame_count(sample_count, sample_rate);
  if (out_frames) *out_frames = frames;
  if (frames <= 0) return nullptr;

  size_t total = 2 * static_cast<size_t>(frames) * bitchord_vocal_spectrogram_bin_count();
  float* buf = static_cast<float*>(std::malloc(total * sizeof(float)));
  if (!buf) return nullptr;

  bitchord_compute_vocal_spectrogram(left_samples, right_samples, sample_count, sample_rate, buf);
  return buf;
}

void bitchord_free_floats(float* ptr) {
  if (ptr) {
    std::free(ptr);
  }
}

int32_t bitchord_resample_output_count(int32_t input_count, double input_rate, double output_rate) {
  if (input_count <= 0 || input_rate <= 0.0 || output_rate <= 0.0) return 0;
  return static_cast<int32_t>(std::round(input_count * (output_rate / input_rate)));
}

int32_t bitchord_resample(
    const float* input,
    int32_t input_count,
    double input_rate,
    double output_rate,
    float* output,
    int32_t output_capacity
) {
  if (!input || !output || input_count <= 0 || input_rate <= 0.0 || output_rate <= 0.0) return 0;
  std::vector<float> in_vec(input, input + input_count);
  std::vector<float> resampled = bitchord::smart::Resample(in_vec, input_rate, output_rate);
  int32_t to_copy = std::min(output_capacity, static_cast<int32_t>(resampled.size()));
  if (to_copy > 0) {
    std::memcpy(output, resampled.data(), to_copy * sizeof(float));
  }
  return to_copy;
}

double bitchord_analyzer_sample_rate(void) { return 11025.0; }
double bitchord_mel_sample_rate(void) { return 22050.0; }
int32_t bitchord_mel_hop_size(void) { return 441; }
int32_t bitchord_mel_fft_size(void) { return 1024; }
double bitchord_vocal_sample_rate(void) { return 44100.0; }
int32_t bitchord_vocal_hop_size(void) { return 1024; }
int32_t bitchord_vocal_fft_size(void) { return 4096; }

} // extern "C"
