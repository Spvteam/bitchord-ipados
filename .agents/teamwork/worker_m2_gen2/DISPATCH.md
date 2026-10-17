## 2026-10-16T12:26:42Z
You are worker_m2_gen2 for Milestone M2 (WP-02: JVM API Decoupling & Source Relocation).
Your working directory: D:\Bitchord\.agents\teamwork\worker_m2_gen2
Your dispatch file: D:\Bitchord\.agents\teamwork\worker_m2_gen2\DISPATCH.md

MANDATORY FIRST STEP: Read D:\Bitchord\.agents\teamwork\ORIGINAL_REQUEST.md before starting any work.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Key Inputs:
- D:\Bitchord\PROJECT.md
- D:\Bitchord\PORTING_PROGRESS.md
- D:\Bitchord\BitChord_iPadOS_Master_Porting_Plan.md
- D:\Bitchord\.agents\teamwork\explorer_m2_1\analysis.md & handoff.md (UI Relocation & iOS Actuals)
- D:\Bitchord\.agents\teamwork\explorer_m2_2\analysis.md & handoff.md (Shared Logic Relocation & Expect/Actual)
- D:\Bitchord\.agents\teamwork\explorer_m2_3\analysis.md & handoff.md (Platform Primitives & Clock Specs)

Your mission:
1. Implement multiplatform primitives in shared/src/commonMain/kotlin/com/music/bitchord/platform/ (PlatformCrypto, PlatformClock, PlatformUrl, PlatformNormalizer, PlatformLock), iOS actuals in shared/src/iosMain/kotlin/com/music/bitchord/platform/ (CommonCrypto, mach_absolute_time, NSString, CFStringNormalize, NSLock), and JVM actuals.
2. Refactor ServerClock.kt and MonotonicClock.kt in shared/src/commonMain/ using PlatformClock and PlatformLock.
3. Relocate the 75 pure KMP shared logic files from shared/src/jvmSharedMain to shared/src/commonMain, providing expect/actual where needed (keep StreamResolver, Http, NEWPIPE in jvmSharedMain).
4. Relocate the 49 UI files from sharedUi/src/jvmSharedMain to sharedUi/src/commonMain, eliminate JDK calls (Locale, TimeUnit, System.nanoTime), and implement the 5 iOS actuals in sharedUi/src/iosMain (PlatformHaptics, ContextClick, ArtworkPixels, PlayerPlatformUi, StatusBarIcons).
5. Compile and verify targets:
   ./gradlew :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :shared:compileKotlinJvm
   ./gradlew :sharedUi:compileCommonMainKotlinMetadata :sharedUi:compileKotlinIosSimulatorArm64 :sharedUi:compileKotlinIosArm64 :sharedUi:compileKotlinJvm
   ./gradlew :desktopApp:compileKotlin
   python tests\e2e\run_e2e_tests.py
6. Update D:\Bitchord\PORTING_PROGRESS.md marking WP-02 complete (17% progress).
7. Write your detailed handoff report to D:\Bitchord\.agents\teamwork\worker_m2_gen2\handoff.md and send a completion message back.
