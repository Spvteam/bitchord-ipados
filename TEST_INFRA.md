# BitChord iPadOS Port — E2E Test Infrastructure Specification (`TEST_INFRA.md`)

## 1. Overview & Testing Philosophy
This document establishes the authoritative End-to-End (E2E) testing infrastructure, methodology, and execution manual for the **BitChord iPadOS / iOS port**.

The BitChord testing strategy is **strictly opaque-box, requirement-driven, and progressively verifiable**:
1. **Opaque-Box Independence**: Test cases assert system behaviors, interface contracts, cryptographic derivations, mathematical invariants, and protocol schemas exclusively through exported public contracts and observable side effects. No test relies on private implementation internals.
2. **Progressive Testability**: The test harness runs self-contained simulations and contract verifications that do not depend on unavailable external hardware (such as physical iPad devices or active macOS Apple Developer signing accounts) during milestone execution.
3. **Zero Facade Tests**: Every test executes authentic algorithmic computations, schema checks, mathematical tolerance verifications, or state transitions. Tautological assertions or mocks designed to pass without real verification are strictly forbidden.
4. **4-Tier Verification Hierarchy**:
   - **Tier 1 — Feature Coverage**: Comprehensive validation of happy paths in isolation (at least 5 test cases per feature covering all 20 features in `PROJECT.md`).
   - **Tier 2 — Boundary & Corner Cases**: Rigorous stress-testing of limits, empty inputs, malformed packets, arithmetic bounds, overflows, and failure cascading.
   - **Tier 3 — Cross-Feature Combinations**: Pairwise and multi-subsystem interactions across streaming, cipher deobfuscation, neural inference, audio graph crossfading, persistence, and lock screen controls.
   - **Tier 4 — Real-World Application Scenarios**: End-to-end user journeys replicating real-world usage patterns from fresh installation to background playback and .ipa release packaging.

---

## 2. Directory Architecture & Harness Layout

The E2E test suite resides entirely within `tests/e2e/`:

```
D:\Bitchord\tests\e2e\
├── harness\
│   ├── __init__.py             # Harness package metadata
│   ├── assertions.py           # Mathematical & contract validators (equal-power crossfade, schemas, SHA1, etc.)
│   ├── fixtures.py             # Reference audio PCM generators, YouTube responses, KuGou XOR keys, EQ presets
│   ├── mocks.py                # Platform simulators (JSContext, Keychain, AVAudioEngine, PoToken, ResourceLoader)
│   └── test_runner.py          # Unified test discovery, filtering, timing, and JSON reporting engine
├── tier1_features\             # Tier 1: 120 Happy Path Feature Tests (Features 1 through 20)
│   ├── test_f01_ios_gradle_targets.py
│   ├── test_f02_dependency_decoupling.py
│   ├── test_f03_vendor_innertubex.py
│   ├── test_f04_ui_relocation.py
│   ├── test_f05_shared_logic_relocation.py
│   ├── test_f06_platform_primitives.py
│   ├── test_f07_cpp_analyzer_bridge.py
│   ├── test_f08_innertube_waterfall_m4a.py
│   ├── test_f09_jscore_cipher.py
│   ├── test_f10_potoken_botguard.py
│   ├── test_f11_auth_keychain.py
│   ├── test_f12_sqlite_sandbox.py
│   ├── test_f13_onnx_pcm_decoder.py
│   ├── test_f14_dual_deck_audio_engine.py
│   ├── test_f15_chunked_stream_loader.py
│   ├── test_f16_background_lockscreen.py
│   ├── test_f17_multisource_lyrics_rpc.py
│   ├── test_f18_compose_ipados_ui.py
│   ├── test_f19_ipa_packaging.py
│   └── test_f20_e2e_verification.py
├── tier2_boundary\             # Tier 2: 120 Boundary, Empty, Overflow & Failure Tests (Features 1 through 20)
│   ├── test_f01_limits.py
│   ├── test_f02_limits.py
│   ├── test_f03_limits.py
│   ├── test_f04_limits.py
│   ├── test_f05_limits.py
│   ├── test_f06_limits.py
│   ├── test_f07_limits.py
│   ├── test_f08_limits.py
│   ├── test_f09_limits.py
│   ├── test_f10_limits.py
│   ├── test_f11_limits.py
│   ├── test_f12_limits.py
│   ├── test_f13_limits.py
│   ├── test_f14_limits.py
│   ├── test_f15_limits.py
│   ├── test_f16_limits.py
│   ├── test_f17_limits.py
│   ├── test_f18_limits.py
│   ├── test_f19_limits.py
│   └── test_f20_limits.py
├── tier3_cross_feature\        # Tier 3: 29 Pairwise Multi-Subsystem Integration Tests
│   ├── test_xf_stream_cipher_potoken.py
│   ├── test_xf_audio_crossfade_smart_automix.py
│   ├── test_xf_dsp_onnx_queue_timeline.py
│   ├── test_xf_auth_keychain_sync.py
│   ├── test_xf_chunked_loader_cache_sqlite.py
│   ├── test_xf_lyrics_translation_ui.py
│   ├── test_xf_stage_manager_shaders_promotion.py
│   ├── test_xf_background_audio_lockscreen_remote.py
│   ├── test_xf_listen_together_party_sync.py
│   └── test_xf_ipa_packaging_entitlements_signing.py
├── tier4_scenarios\            # Tier 4: 10 Comprehensive End-to-End Application Scenarios
│   ├── test_sc01_user_onboarding_and_auth.py
│   ├── test_sc02_search_and_waterfall_playback.py
│   ├── test_sc03_smart_automix_dual_deck_dj.py
│   ├── test_sc04_offline_mode_cached_playback.py
│   ├── test_sc05_equalizer_dsp_visualizer_pipeline.py
│   ├── test_sc06_word_synced_lyrics_story_card_export.py
│   ├── test_sc07_stage_manager_rotation_promotion.py
│   ├── test_sc08_listen_together_multiroom_sync.py
│   ├── test_sc09_multi_source_dedup_and_scrobble.py
│   └── test_sc10_reproducible_ipa_release_lifecycle.py
├── run_e2e_tests.py            # Master CLI runner entry point
├── run_all.bat                 # Windows execution script
└── run_all.sh                  # macOS/Linux POSIX execution script
```

---

## 3. Complete Feature Inventory & Coverage Mapping

All 20 features defined in `PROJECT.md § Feature Inventory` are covered across the 4-tier methodology:

| Feature # | Feature Name | Tier 1 (Happy Path) | Tier 2 (Boundaries & Limits) | Tier 3 (Cross-Feature) | Tier 4 (Real-World Scenarios) |
|---|---|---|---|---|---|
| **F01** | iOS Gradle Targets | `test_f01_ios_gradle_targets.py` (6 tests) | `test_f01_limits.py` (6 tests) | `test_xf_ipa_packaging_entitlements_signing.py` | `test_sc10_reproducible_ipa_release_lifecycle.py` |
| **F02** | Dependency Decoupling | `test_f02_dependency_decoupling.py` (6 tests) | `test_f02_limits.py` (6 tests) | `test_xf_stream_cipher_potoken.py` | `test_sc02_search_and_waterfall_playback.py` |
| **F03** | Vendor `:innertubex` | `test_f03_vendor_innertubex.py` (6 tests) | `test_f03_limits.py` (6 tests) | `test_xf_stream_cipher_potoken.py` | `test_sc02_search_and_waterfall_playback.py` |
| **F04** | UI Relocation | `test_f04_ui_relocation.py` (6 tests) | `test_f04_limits.py` (6 tests) | `test_xf_stage_manager_shaders_promotion.py` | `test_sc07_stage_manager_rotation_promotion.py` |
| **F05** | Shared Logic Relocation | `test_f05_shared_logic_relocation.py` (6 tests) | `test_f05_limits.py` (6 tests) | `test_xf_audio_crossfade_smart_automix.py` | `test_sc03_smart_automix_dual_deck_dj.py` |
| **F06** | Platform Primitives | `test_f06_platform_primitives.py` (6 tests) | `test_f06_limits.py` (6 tests) | `test_xf_auth_keychain_sync.py` | `test_sc01_user_onboarding_and_auth.py` |
| **F07** | C++17 DSP Analyzer Bridge | `test_f07_cpp_analyzer_bridge.py` (6 tests) | `test_f07_limits.py` (6 tests) | `test_xf_dsp_onnx_queue_timeline.py` | `test_sc03_smart_automix_dual_deck_dj.py` |
| **F08** | Innertube Waterfall & M4A | `test_f08_innertube_waterfall_m4a.py` (6 tests) | `test_f08_limits.py` (6 tests) | `test_xf_stream_cipher_potoken.py` | `test_sc02_search_and_waterfall_playback.py` |
| **F09** | JavaScriptCore Cipher | `test_f09_jscore_cipher.py` (6 tests) | `test_f09_limits.py` (6 tests) | `test_xf_stream_cipher_potoken.py` | `test_sc02_search_and_waterfall_playback.py` |
| **F10** | Headless WKWebView PoToken | `test_f10_potoken_botguard.py` (6 tests) | `test_f10_limits.py` (6 tests) | `test_xf_stream_cipher_potoken.py` | `test_sc02_search_and_waterfall_playback.py` |
| **F11** | Authentication & Keychain | `test_f11_auth_keychain.py` (6 tests) | `test_f11_limits.py` (6 tests) | `test_xf_auth_keychain_sync.py` | `test_sc01_user_onboarding_and_auth.py` |
| **F12** | KMP SQLite & Sandbox Paths | `test_f12_sqlite_sandbox.py` (6 tests) | `test_f12_limits.py` (6 tests) | `test_xf_chunked_loader_cache_sqlite.py` | `test_sc04_offline_mode_cached_playback.py` |
| **F13** | ONNX Runtime & PCM Decoder | `test_f13_onnx_pcm_decoder.py` (6 tests) | `test_f13_limits.py` (6 tests) | `test_xf_dsp_onnx_queue_timeline.py` | `test_sc03_smart_automix_dual_deck_dj.py` |
| **F14** | Dual-Deck `AVAudioEngine` | `test_f14_dual_deck_audio_engine.py` (6 tests) | `test_f14_limits.py` (6 tests) | `test_xf_audio_crossfade_smart_automix.py` | `test_sc03_smart_automix_dual_deck_dj.py` |
| **F15** | Chunked Stream Loader | `test_f15_chunked_stream_loader.py` (6 tests) | `test_f15_limits.py` (6 tests) | `test_xf_chunked_loader_cache_sqlite.py` | `test_sc02_search_and_waterfall_playback.py` |
| **F16** | Background Audio & Lock Screen | `test_f16_background_lockscreen.py` (6 tests) | `test_f16_limits.py` (6 tests) | `test_xf_background_audio_lockscreen_remote.py` | `test_sc07_stage_manager_rotation_promotion.py` |
| **F17** | Multi-Source Lyrics & RPC | `test_f17_multisource_lyrics_rpc.py` (6 tests) | `test_f17_limits.py` (6 tests) | `test_xf_lyrics_translation_ui.py` | `test_sc06_word_synced_lyrics_story_card_export.py` |
| **F18** | Compose iPadOS UI & Shaders | `test_f18_compose_ipados_ui.py` (6 tests) | `test_f18_limits.py` (6 tests) | `test_xf_stage_manager_shaders_promotion.py` | `test_sc07_stage_manager_rotation_promotion.py` |
| **F19** | Automated `.ipa` Packaging | `test_f19_ipa_packaging.py` (6 tests) | `test_f19_limits.py` (6 tests) | `test_xf_ipa_packaging_entitlements_signing.py` | `test_sc10_reproducible_ipa_release_lifecycle.py` |
| **F20** | E2E Testing Suite Verification | `test_f20_e2e_verification.py` (6 tests) | `test_f20_limits.py` (6 tests) | `test_xf_ipa_packaging_entitlements_signing.py` | `test_sc10_reproducible_ipa_release_lifecycle.py` |

**Total Test Count**:
- **Tier 1 (Happy Paths)**: 120 test cases
- **Tier 2 (Boundaries & Limits)**: 120 test cases
- **Tier 3 (Cross-Feature)**: 29 test cases
- **Tier 4 (Scenarios)**: 10 test cases
- **Grand Total**: **279 test cases**

---

## 4. Expected Output Derivation & Authoritative Oracles

Every test case derives its assertions from documented mathematical specifications, standard protocols, or authoritative upstream implementations:

1. **Equal-Power Crossfade**:
   $$\text{Gain}_A(t) = \cos\left(\frac{\pi}{2} t\right), \quad \text{Gain}_B(t) = \sin\left(\frac{\pi}{2} t\right), \quad \forall t \in [0, 1]$$
   Invariant enforced: $\text{Gain}_A(t)^2 + \text{Gain}_B(t)^2 \equiv 1.0 \pm 10^{-4}$ (preserving 0 dB acoustic power across transitions).
2. **Cryptographic Signatures & Tokens**:
   - `PlatformCrypto.sha1`: RFC 3174 compliant 40-character lowercase hexadecimal hash.
   - `PlatformCrypto.md5`: RFC 1321 compliant 32-character lowercase hexadecimal hash.
   - Google `SAPISIDHASH`: `${timestamp}_${sha1("$timestamp $sapisid $origin")}`.
3. **KuGou Synchronized Lyrics Decryption**:
   - Fixed XOR cipher key: `@Gaw^2tGQ61-ÎÒni` (16 bytes).
   - Decompression: RFC 1950 zlib inflate stream following `"krc1"` magic header.
4. **C++ DSP Spectrogram Dimensions**:
   - Mel Spectrogram: $f_s = 22,050\text{ Hz}$, $n_{\text{fft}} = 1024$, $\text{hop} = 220$ (100 fps), 128 Slaney mel filterbank bins.
   - Vocal Spectrogram: $f_s = 44,100\text{ Hz}$, $n_{\text{fft}} = 4096$, $\text{hop} = 1024$, stereo (2 channels), 1487 STFT bins.
5. **Stage Manager Breakpoints**:
   - `COMPACT`: $\text{width} < 600\text{ dp}$ (Slide Over / Split 1/3)
   - `MEDIUM`: $600\text{ dp} \le \text{width} < 840\text{ dp}$ (Split 1/2)
   - `EXPANDED`: $\text{width} \ge 840\text{ dp}$ (Split 2/3 / Full Screen Landscape)

---

## 5. Non-Deterministic Output Handling
To guarantee 100% deterministic, repeatable test execution without network flakiness:
1. **Clock & Time Drift**: Simulated using `MockPlatformClock` which provides precise monotonic milliseconds and controlled advances, preventing timestamp jitter.
2. **BotGuard PoToken Minting**: Uses deterministic pseudo-random seed hashes derived from monotonically advancing clock timestamps, preventing nondeterministic network roundtrips.
3. **Stream Resolution Ranges**: Uses deterministic synthetic binary audio streams with known content lengths (e.g. 3,845,921 bytes for Starboy AAC).

---

## 6. Execution Manual & CLI Commands

### 6.1 Running the Full Test Suite
On Windows (PowerShell or Command Prompt):
```cmd
python tests\e2e\run_e2e_tests.py
```
Or via batch script:
```cmd
.\tests\e2e\run_all.bat
```

On macOS / Linux / POSIX Shell:
```bash
python3 tests/e2e/run_e2e_tests.py
```
Or via shell script:
```bash
./tests/e2e/run_all.sh
```

### 6.2 Running Individual Tiers
```cmd
python tests/e2e/run_e2e_tests.py --tier 1       # Run Tier 1 Feature Coverage (120 tests)
python tests/e2e/run_e2e_tests.py --tier 2       # Run Tier 2 Boundary & Limits (120 tests)
python tests/e2e/run_e2e_tests.py --tier 3       # Run Tier 3 Cross-Feature (29 tests)
python tests/e2e/run_e2e_tests.py --tier 4       # Run Tier 4 Application Scenarios (10 tests)
```

### 6.3 Filtering by Feature Number (1 through 20)
```cmd
python tests/e2e/run_e2e_tests.py --feature 8    # Run all tests covering Feature 8 (Innertube Waterfall)
python tests/e2e/run_e2e_tests.py --feature 14   # Run all tests covering Feature 14 (Dual-Deck Audio Engine)
```

### 6.4 Additional Flags
- `--verbose` / `-v`: Displays real-time test execution names and millisecond duration for every test case.
- `--failfast` / `-f`: Halts test execution immediately on the first assertion failure.
- `--report <path>`: Generates a machine-readable JSON execution report containing test-by-test metrics.

Example:
```cmd
python tests/e2e/run_e2e_tests.py --tier all --report build/e2e_report.json --verbose
```

---

## 7. Exit Code Protocol & CI Integration
- **Exit Code `0`**: All discovered test cases passed with 100% success.
- **Exit Code `1`**: One or more assertions failed, an unhandled exception occurred, or zero test cases matched the requested filter.

In CI/CD environments (GitHub Actions, Xcode Cloud), the runner integrates directly into pipeline steps to gate pull requests.

---

## 8. Verification Results

```
=======================================================
  BitChord iPadOS Port - E2E Test Suite Execution
  Tiers: ALL | Feature Filter: ALL
  Discovered: 279 test cases
=======================================================
  Execution Summary:
  Total:  279
  Passed: 279
  Failed: 0
  Errors: 0
  Time:   0.04s
-------------------------------------------------------
  Result: ALL TESTS PASSED - 100% SUCCESS
=======================================================
```
