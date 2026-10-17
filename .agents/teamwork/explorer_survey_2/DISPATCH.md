## 2026-10-16T09:16:19Z
You are Survey Explorer 2 (Stream & Auth Spec Miner).
Your working directory is: D:\Bitchord\.agents\teamwork\explorer_survey_2
Your parent is the Project Orchestrator (b9d6a6c6-0231-4a6f-a397-e747c73d7ef9).

MANDATORY FIRST STEPS:
1. Read D:\Bitchord\.agents\teamwork\ORIGINAL_REQUEST.md
2. Read D:\Bitchord\BitChord_iPadOS_Master_Porting_Plan.md (especially Section 2: WP-03, WP-04, WP-07, and Section 3)
3. Examine existing stream extraction and cipher code in desktopApp/src/main/kotlin/com/music/bitchord/desktop/ (DesktopPlaybackResolver.kt, DesktopCipher.kt, DesktopPoTokenWebView.kt)
4. Examine native/analyzer/ (audio_analysis.h/cpp, mel_spectrogram.h/cpp, vocal_spectrogram.h/cpp) and app/src/main/cpp/jni/ (analysis_jni.cpp)

YOUR OBJECTIVE:
Map the stream resolution, cipher, authentication, and C++ DSP analyzer requirements:
- Document how YouTube Music stream extraction currently works in desktopApp and shared.
- Detail the Innertube waterfall (ANDROID_VR -> IOS -> WEB_REMIX) and cipher deobfuscation (s signature and n parameter) using JavaScriptCore / JSContext on iOS.
- Detail the BotGuard poToken generation using headless WKWebView on iOS.
- Detail authentication flow: Google/Spotify login via WKWebView, SAPISID extraction, and secure iOS Keychain storage (replacing EncryptedSharedPreferences).
- Detail the C++17 DSP Analyzer iOS static library build (libbitchord_analysis.a) and Kotlin/Native cinterop bridge.
- List all specific source files, functions, and interfaces needed for WP-03, WP-04, and WP-07.

OUTPUT REQUIREMENTS:
- Write your comprehensive findings to D:\Bitchord\.agents\teamwork\explorer_survey_2\survey_report.md
- Write a self-contained handoff to D:\Bitchord\.agents\teamwork\explorer_survey_2\handoff.md
- Use send_message to report completion and summary back to caller b9d6a6c6-0231-4a6f-a397-e747c73d7ef9.
