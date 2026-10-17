# Orchestrator Porting Execution Plan

## Objective
Port BitChord to iPadOS / iOS, producing a sideload-ready `.ipa` file satisfying all requirements (R1–R5) and acceptance criteria outlined in `ORIGINAL_REQUEST.md` and `BitChord_iPadOS_Master_Porting_Plan.md`.

## Execution Topology
1. **Survey Stage (Phase 0)**
   - Dispatch 3 parallel Explorers:
     - Explorer 1: Build system, Gradle modules, dependency boundaries (`:shared`, `:sharedUi`, `:desktopApp`, `:app`, root `build.gradle.kts`).
     - Explorer 2: Core runtime extraction & audio engine (`YtMusicRepository`, stream resolution, cipher, `AVAudioEngine` plan, C++ DSP analyzer in `native/analyzer/`).
     - Explorer 3: UI, Compose Multiplatform, iPadOS responsive layout & packaging tooling (`scripts/build_ipados_ipa.sh`, Xcode project structure).
   - Merge findings into `PROJECT.md` at root.

2. **Phase 1: Core Foundation & Platform Decoupling**
   - WP-01: KMP Gradle & Source-Set Setup (`iosArm64`, `iosSimulatorArm64`, `:innertubex` vendoring, `ktor-client-darwin`).
   - WP-02: JVM API Decoupling (`jvmSharedMain` -> `commonMain`, `PlatformCrypto`, `PlatformUrl`, multiplatform primitives).
   - WP-04: C++17 DSP Analyzer iOS Bridge (`native/analyzer/*.cpp` -> static library / cinterop bridge).

3. **Phase 2: Stream Extraction & Cloud Storage**
   - WP-03: YouTube Music Stream Resolution & JavaScriptCore Cipher (Innertube waterfall, `JSContext` signature/n-param, BotGuard poToken).
   - WP-07: Authentication & Secure Keychain Storage (`WKWebView` Google login, iOS Keychain).
   - WP-08: KMP Database & Sandbox Storage (`androidx.sqlite`, iOS sandbox paths).

4. **Phase 3: Native iPadOS Audio Engine**
   - WP-05: ONNX Runtime Neural Tracker & Audio Decoding (`onnxruntime-c`, `ExtAudioFileRead`).
   - WP-06: Dual-Deck `AVAudioEngine` & Smart Automix (Dual `AVAudioPlayerNode`, equal-power crossfade, background audio, lock screen controls).

5. **Phase 4: Full UI, Lyrics & Integrations**
   - WP-09: Multi-Source Registry & Lyrics Engine (12+ lyrics sources, word/syllable sync, translation, Spotify/Discord/Last.fm).
   - WP-10: Compose Multiplatform UI & iPad Adaptive Layout (Adaptive layout, Liquid Glass/Haze, 120Hz ProMotion, 9:16 story export).

6. **Phase 5: Build, Package & Verify**
   - WP-11: E2E Integration & Automated Test Verification.
   - WP-12: Automated `.ipa` Packaging & Release (`scripts/build_ipados_ipa.sh` -> unsigned/sideloadable `.ipa`).

## Gating Rules (Strict)
Every Work Package passes through:
1. Explorer: Gap analysis and implementation strategy.
2. Worker: Genuine implementation and build/test verification (with anti-cheating warning).
3. Reviewers (2): Independent code quality, interface compliance, and test verification.
4. Challengers (2): Stress testing and edge case verification.
5. Forensic Auditor: Integrity verification (binary veto).
6. Gate status recorded in `GATE_STATUS.md`.
