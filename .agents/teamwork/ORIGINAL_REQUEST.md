# Original User Request

## 2026-10-16T09:13:35Z

Port BitChord (open-source Kotlin Multiplatform / Compose Multiplatform YouTube Music client) to iPadOS / iOS, producing a functional, sideload-ready `.ipa` file based on the master blueprint in `D:\Bitchord\BitChord_iPadOS_Master_Porting_Plan.md`.

Working directory: `D:\Bitchord`
Integrity mode: development

## Requirements

### R1. KMP iOS Target Configuration & Module Decoupling
Migrate `:shared` and `:sharedUi` logic out of `jvmSharedMain` into `commonMain`. Configure `iosArm64` and `iosSimulatorArm64` targets in Gradle, swap JVM dependencies (`OkHttp`, `java.*`) for multiplatform equivalents (`ktor-client-darwin`, `PlatformCrypto`, `PlatformUrl`).

### R2. Native iOS YouTube Stream Resolution & Auth
Implement multi-client Innertube stream extraction with native `JavaScriptCore` (`JSContext`) cipher deciphering (`s` signature and `n` throttling parameter) and headless `WKWebView` BotGuard `poToken` generation. Provide Google/Spotify authentication via `WKWebView` with secure iOS Keychain storage.

### R3. Dual-Deck Audio Engine & Background Playback
Implement `AVAudioEngine` dual-deck player (`primaryPlayer` + `ghostPlayer`) supporting gapless playback, equal-power `sin`/`cos` crossfade (0–12s), `AVAudioSession` background playback continuity, and `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter` controls.

### R4. Compose Multiplatform UI & iPadOS Adaptive Layout
Render the full BitChord interface using Compose Multiplatform with Liquid Glass / Haze shaders, word-synced lyrics with Apple-like syllable animation, 9:16 Instagram Story lyrics card export, and Stage Manager / Split View responsive layouts.

### R5. Automated `.ipa` Packaging & Progress Tracking
Set up the Xcode archive and reproducible `.ipa` packaging pipeline (with ad-hoc/sideload entitlements for AltStore / SideStore / TrollStore). Maintain an active `PORTING_PROGRESS.md` log tracking status across all 12 Work Packages.

## Acceptance Criteria

### Build & Platform Target
- [ ] Gradle project configures `iosArm64` and `iosSimulatorArm64` without build errors.
- [ ] No `java.*` or `javax.*` imports remain in `commonMain`.

### Audio & Stream Resolution
- [ ] Streams audio from YouTube Music at up to 256kbps without HTTP 403 or throttling caps.
- [ ] Crossfade transitions between consecutive songs seamlessly with dual audio nodes.
- [ ] Audio continues playing smoothly when the app is backgrounded or iPad is locked.
- [ ] Lock Screen and Control Center show accurate track metadata, artwork, and scrub controls.

### Packaging & Artifact
- [ ] Generates an unsigned/sideloadable `.ipa` bundle in `build/` ready for installation.
- [ ] `PORTING_PROGRESS.md` is updated after every work package milestone.

## Follow-up — 2026-10-16T12:37:19Z

ก่อนหน้านี้tokenหมด พวกsub agentปิดไปแล้ว ทำงานต่อได้มั้ยตอนนี้ Token รีละ
