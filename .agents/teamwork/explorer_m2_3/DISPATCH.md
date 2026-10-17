# Milestone M2 Explorer 3 Dispatch
Working Directory: D:\Bitchord\.agents\teamwork\explorer_m2_3
Parent: Project Orchestrator (b9d6a6c6-0231-4a6f-a397-e747c73d7ef9)
Milestone: M2 (WP-02: JVM API Decoupling & Source Relocation - Platform Primitives Focus)
Scope: Map exact expect/actual specifications for PlatformCrypto, PlatformClock, PlatformUrl, PlatformNormalizer, and ServerClock.
## 2026-10-16T10:24:07Z
You are M2 Explorer 3 (Platform Primitives Spec Miner).
Your working directory: D:\Bitchord\.agents\teamwork\explorer_m2_3
Your parent: Project Orchestrator (b9d6a6c6-0231-4a6f-a397-e747c73d7ef9)

MANDATORY FIRST STEPS:
1. Read D:\Bitchord\.agents\teamwork\ORIGINAL_REQUEST.md
2. Read D:\Bitchord\PROJECT.md (Milestone M2 scope)
3. Read D:\Bitchord\BitChord_iPadOS_Master_Porting_Plan.md (Section 2: WP-02 and Section 3)

YOUR OBJECTIVE:
Design and specify all multiplatform platform primitives to eliminate java.* and javax.* imports from commonMain:
- PlatformCrypto: expect fun sha1(bytes: ByteArray): ByteArray, md5, hmacSha256. Provide CommonCrypto (CC_SHA1, CC_MD5, CCHmac) actual for iosMain and MessageDigest actual for jvmMain.
- PlatformClock: expect fun currentTimeMillis(): Long, monotonicNano(): Long. Provide Clock.System / platform.posix / NSDate actual for iosMain.
- PlatformUrl: expect fun urlEncode(s: String): String, urlDecode(s: String): String.
- PlatformNormalizer: expect fun normalizeNfkc(s: String): String (using CFStringNormalize on iOS).
- ServerClock.kt: Replace java.time / JVM dependencies with kotlinx-datetime / multiplatform equivalents.
- Provide copy-paste ready expect and actual source code in your analysis.
- Write your findings to D:\Bitchord\.agents\teamwork\explorer_m2_3\analysis.md and handoff.md
- Send completion message to orchestrator b9d6a6c6-0231-4a6f-a397-e747c73d7ef9.
