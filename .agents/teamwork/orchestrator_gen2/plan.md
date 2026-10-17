# Orchestrator Execution Plan (Generation 2)

## Objective
Lead BitChord iPadOS / iOS porting through all remaining work packages (WP-02 through WP-12) to produce the sideload-ready `.ipa` artifact satisfying all acceptance criteria.

## Execution Topology

### Phase 1: Core Foundation & Platform Decoupling
- **Milestone 2 (WP-02: JVM API Decoupling & Source Relocation)**:
  - Exploration: COMPLETED (reports in `explorer_m2_1`, `explorer_m2_2`, `explorer_m2_3`).
  - Worker: Implement `PlatformCrypto`, `PlatformClock`, `PlatformUrl`, `PlatformNormalizer`, `PlatformLock`, relocate 75 shared logic files to `shared/src/commonMain`, relocate 49 UI files to `sharedUi/src/commonMain`, implement 5 iOS UI actuals, verify compilation and tests.
  - Review: 2 Reviewers.
  - Verification: 2 Challengers.
  - Integrity: 1 Forensic Auditor.
  - Gate check.
- **Milestone 3 (WP-04: C++17 DSP Analyzer iOS Bridge)**:
  - Worker: Compile `native/analyzer/*.cpp` to `libbitchord_analysis.a` / bridge C ABI via `cinterop`.
  - Gate check.

### Phase 2: Stream Extraction & Cloud Storage
- **Milestone 4 (WP-03: YouTube Music Stream Resolution & JavaScriptCore Cipher)**:
  - Innertube waterfall, `JSContext` cipher deobfuscation, headless `WKWebView` BotGuard `poToken`.
  - Gate check.
- **Milestone 5 (WP-07: Authentication & Secure Keychain Storage)**:
  - `WKWebView` Google login, `SAPISIDHASH`, hardware Keychain storage.
  - Gate check.
- **Milestone 6 (WP-08: KMP Database & Sandbox Storage)**:
  - Port to `androidx.sqlite:sqlite-bundled`, configure iOS sandbox directories.
  - Gate check.

### Phase 3: Native iPadOS Audio Engine
- **Milestone 7 (WP-05: ONNX Runtime Neural Tracker & Audio Decoding)**:
  - `onnxruntime-c:1.20.0` bridge, `AudioToolbox.ExtAudioFileRead` PCM decoder.
  - Gate check.
- **Milestone 8 (WP-06: Dual-Deck AVAudioEngine & Background Playback)**:
  - Dual `AVAudioPlayerNode`, equal-power crossfade, `bitchord-chunked://` loader, background audio & lock screen controls.
  - Gate check.

### Phase 4: Full UI, Lyrics & Integrations
- **Milestone 9 (WP-09: Multi-Source Lyrics & Services Integration)**:
  - 12+ lyrics sources, word/syllable sync, translation, Spotify, Discord, Last.fm.
  - Gate check.
- **Milestone 10 (WP-10: Compose Multiplatform UI & iPadOS Adaptive Layout)**:
  - `ComposeUIViewController`, Stage Manager / Split View, Skia shaders, 120Hz ProMotion, 9:16 story export.
  - Gate check.

### Phase 5: Packaging & Final Acceptance
- **Milestone 11 (WP-12: Automated .ipa Packaging & Release Pipeline)**:
  - `scripts/build_ipados_ipa.sh`, Xcode project, entitlements, `ldid` signing, generate `.ipa`.
  - Gate check.
- **Milestone 12 (WP-11: E2E Integration & Final Test Verification)**:
  - Phase 1: 100% pass of Tiers 1–4 E2E tests (279/279).
  - Phase 2: Tier 5 adversarial coverage hardening.
  - Gate check.
- Final completion report to Sentinel.
