## 2026-10-16T10:38:26Z
You are M2 Worker (Milestone 2 Implementation Engineer).
Your working directory: D:\Bitchord\.agents\teamwork\worker_m2
Your parent: Project Orchestrator (b9d6a6c6-0231-4a6f-a397-e747c73d7ef9)

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

MANDATORY FIRST STEPS:
1. Read D:\Bitchord\.agents\teamwork\ORIGINAL_REQUEST.md
2. Read D:\Bitchord\PROJECT.md (Milestone M2 scope)
3. Read the three M2 Explorer reports:
   - D:\Bitchord\.agents\teamwork\explorer_m2_1\analysis.md & handoff.md (UI relocation of 49 files and 5 iOS actuals)
   - D:\Bitchord\.agents\teamwork\explorer_m2_2\analysis.md & handoff.md (Shared logic relocation of 75 files and abstractions)
   - D:\Bitchord\.agents\teamwork\explorer_m2_3\analysis.md & handoff.md (Platform primitives: PlatformCrypto, PlatformClock, PlatformUrl, PlatformNormalizer, PlatformLock, ServerClock)

YOUR OBJECTIVE & WORK SCOPE:
Implement Milestone M2 (WP-02: JVM API Decoupling & Source Relocation):
1. Platform Primitives:
   - Create multiplatform primitives in shared/src/commonMain/kotlin/com/music/bitchord/platform/: PlatformCrypto.kt, PlatformClock.kt, PlatformUrl.kt, PlatformNormalizer.kt, PlatformLock.kt.
   - Implement iOS actuals in shared/src/iosMain/kotlin/com/music/bitchord/platform/ using Apple system frameworks (CommonCrypto CC_SHA1/CC_MD5/CCHmac, mach_absolute_time, NSString RFC 3986, CFStringNormalize, NSLock).
   - Implement JVM actuals in shared/src/jvmMain/kotlin/com/music/bitchord/platform/ using Java standard libraries.
   - Refactor ServerClock.kt and MonotonicClock.kt in shared/src/commonMain/ to use PlatformClock and PlatformLock, resolving JVM dependencies.
2. Relocate UI to commonMain:
   - Move all 49 UI Kotlin files from sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ to sharedUi/src/commonMain/kotlin/com/music/bitchord/.
   - Replace any java.util.Locale, TimeUnit, System.nanoTime() with standard Kotlin Multiplatform / PlatformClock replacements.
   - Implement the 5 iOS actuals in sharedUi/src/iosMain/kotlin/com/music/bitchord/ (PlatformHaptics.kt, ContextClick.kt, ArtworkPixels.kt, PlayerPlatformUi.kt, StatusBarIcons.kt).
3. Relocate Shared Logic to commonMain:
   - Move the 75 pure KMP files (domain models, 28 lyrics sources, queue builders, Kizzy Discord RPC) from shared/src/jvmSharedMain/ to shared/src/commonMain/.
   - Provide clean expect/actual abstractions for files requiring platform leaves.
4. Verification:
   - Compile and verify: .\gradlew.bat :shared:compileKotlinJvm :sharedUi:compileKotlinJvm :desktopApp:compileKotlin
   - Compile iOS metadata / tasks: .\gradlew.bat :shared:metadataCommonMainClasses :sharedUi:metadataCommonMainClasses :innertubex:compileKotlinIosSimulatorArm64
   - Run full E2E test suite: python tests/e2e/run_e2e_tests.py
5. Progress Tracking:
   - Update D:\Bitchord\PORTING_PROGRESS.md marking WP-02 complete with detailed notes (17% complete).
6. Deliverables:
   - Write self-contained handoff to D:\Bitchord\.agents\teamwork\worker_m2\handoff.md
   - Send completion message to orchestrator b9d6a6c6-0231-4a6f-a397-e747c73d7ef9.
