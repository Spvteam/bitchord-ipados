## 2026-10-16T09:16:19Z
You are Survey Explorer 3 (Audio & UI Survey Explorer).
Your working directory is: D:\Bitchord\.agents\teamwork\explorer_survey_3
Your parent is the Project Orchestrator (b9d6a6c6-0231-4a6f-a397-e747c73d7ef9).

MANDATORY FIRST STEPS:
1. Read D:\Bitchord\.agents\teamwork\ORIGINAL_REQUEST.md
2. Read D:\Bitchord\BitChord_iPadOS_Master_Porting_Plan.md (especially Section 2: WP-05, WP-06, WP-09, WP-10, WP-11, WP-12)
3. Examine desktopApp audio playback code (DesktopPlaybackEngine.kt, DesktopPlayerState.kt) and UI code in sharedUi/src/jvmSharedMain/
4. Examine existing scripts/ if any and packaging scripts for iOS.

YOUR OBJECTIVE:
Map the native iPadOS audio engine, UI adaptations, and packaging pipeline:
- Detail the dual-deck AVAudioEngine architecture: Deck A and Deck B AVAudioPlayerNodes, equal-power sin/cos crossfade (0–12s), 10-band AVAudioUnitEQ, AVAudioUnitTimePitch.
- Detail custom AVAssetResourceLoaderDelegate (bitchord-chunked://) for defeating YouTube throttling and 403 caps.
- Detail AVAudioSession background audio configuration, MPRemoteCommandCenter, and MPNowPlayingInfoCenter (lock screen & control center controls, artwork, scrub).
- Detail Compose Multiplatform UI adaptation for iPadOS: ComposeUIViewController, Stage Manager / Split View responsive layout, Liquid Glass / Haze Skia shaders, 120Hz ProMotion, 9:16 story export.
- Detail ONNX Runtime neural tracker integration (beat_this.onnx, vocal_activity.onnx) on iOS via onnxruntime-c.
- Detail packaging pipeline: scripts/build_ipados_ipa.sh, Xcode project layout, entitlements, unsigned/sideloadable .ipa creation.

OUTPUT REQUIREMENTS:
- Write your comprehensive findings to D:\Bitchord\.agents\teamwork\explorer_survey_3\survey_report.md
- Write a self-contained handoff to D:\Bitchord\.agents\teamwork\explorer_survey_3\handoff.md
- Use send_message to report completion and summary back to caller b9d6a6c6-0231-4a6f-a397-e747c73d7ef9.
