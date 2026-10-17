# BitChord iPadOS Port — Stream Resolution, Cipher, Auth & C++ DSP Analyzer Survey Report

**Author**: Survey Explorer 2 (Stream & Auth Spec Miner)  
**Date**: 2026-10-16  
**Status**: Comprehensive Specification Survey Complete  
**Target Milestone**: Phase 0 Architectural Survey -> Phase 1/2 Execution (WP-03, WP-04, WP-07)  

---

## 1. Executive Summary & Architectural Scope

This report establishes the technical specification, algorithmic contracts, platform-specific adaptations, and source-level inventory required to port BitChord's core stream extraction, cipher deobfuscation, BotGuard `poToken` generation, multi-account authentication, and C++17 DSP audio analyzer to iPadOS 16.0+ (`iosArm64` and `iosSimulatorArm64`).

The analysis encompasses four key operational pillars:
1. **YouTube Music Stream Resolution (WP-03)**: Transitioning from JVM/OkHttp/NewPipe-dependent resolution to a pure Kotlin Multiplatform (KMP) architecture backed by a vendored `:innertubex` KMP module, executing multi-client waterfall extraction (`ANDROID_VR -> IOS -> WEB_REMIX`) with native hardware-friendly format prioritization (AAC-in-MP4 `itag 140/141` for iOS hardware decoders).
2. **Cipher Deobfuscation via Apple `JavaScriptCore` (WP-03)**: Replacing Desktop/Android JVM QuickJS bindings (`com.dokar.quickjs.QuickJs`) with Apple's in-process native `JavaScriptCore.framework` (`JSContext`) for sub-millisecond synchronous deciphering of signature (`s`) and throttling (`n`) parameters with zero main-thread UI lag and zero background execution suspension issues.
3. **Headless BotGuard `poToken` Generation (WP-03)**: Adapting JavaFX/Android WebView minters into an iOS headless `WKWebView` minter driving `PO_TOKEN_HTML`, orchestrating the 3-stage challenge-response handshake (`jnn/v1/Create` -> BotGuard VM -> `GenerateIT` -> `obtainPoToken`), providing `playerRequestPoToken` and `streamingDataPoToken` with 6-hour caching.
4. **Multi-Account Authentication & iOS Keychain Storage (WP-07)**: Modal `WKWebView` capture for Google/YouTube Music and Spotify logins, algorithmic derivation of `SAPISIDHASH` using platform SHA-1 (`CommonCrypto` `CC_SHA1`), and replacing Android `EncryptedSharedPreferences` / Linux DBus `DesktopSecretStore` with hardware-backed iOS Keychain (`Security.framework`).
5. **Native C++17 DSP Analyzer & `cinterop` Bridge (WP-04)**: Verbatim compilation of the 5 upstream C++17 DSP source files (`audio_analysis.cpp`, `tempo_analysis.cpp`, `resampler.cpp`, `mel_spectrogram.cpp`, `vocal_spectrogram.cpp`) into `libbitchord_analysis.a` via Apple Clang, exposed to Kotlin/Native via a C ABI bridge (`bitchord_ios_bridge.h`) matching the exact JSON schema required by `TrackFeatures.kt`.

---

## 2. YouTube Music Stream Extraction Architecture

### 2.1 Upstream Workflow (`StreamResolver.kt` & `InnerTubeXResolver.kt`)

In upstream BitChord, stream extraction is handled primarily by `StreamResolver.kt` and `InnerTubeXResolver.kt` in `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/`.

The lifecycle of resolving a stream for `videoId` proceeds as follows:
1. **Recent Cache Check**: `StreamResolver.recent[videoId]` holds resolved URLs for up to 20 minutes (`URL_TTL_MS = 20 * 60 * 1000L`). If valid and non-expired, the URL is returned immediately.
2. **Unplayable Verdict Check**: `StreamResolver.unplayable[videoId]` remembers permanent failures (age restriction without account, geographic blocks, paid content, takedowns) for 10 minutes (`UNPLAYABLE_TTL_MS = 10 * 60 * 1000L`) to prevent request storms.
3. **In-Flight Coalescing**: `coalescedResolve(videoId)` ensures that if multiple calls request the same `videoId` simultaneously (e.g. read-ahead cache warm-up racing with user playback tap), only one network walk executes; secondary callers wait on the same `Deferred<Stream>`.
4. **InnerTubeX Primary Resolution**: `InnerTubeXResolver.extract(videoId, maxKbps, skipClients, requireM4a)`:
   - Sets session cookies (`Innertube.cookie`), visitor data (`Innertube.ensureVisitorData()`), and locale.
   - Invokes `extractor.extract(...)` using `InnerTubeExtractor` with client fallback catalog.
   - Attaches client playback nonce (`generateClientPlaybackNonce()`).
   - Retrieves audio stream, bitrate, codecs, loudness normalization figure (`loudnessDb`), and client headers.
5. **URL Probing (`probe(url)`)**:
   - Google Video CDN URLs frequently return HTTP 403 Forbidden or throttle after partial reads if client headers mismatch or tokens are rejected.
   - `probe(url)` issues a bounded HTTP Range request via OkHttp/Ktor with headers:
     - `Range: bytes=1048576-<end>` (starting at `AUTH_BOUNDARY_BYTES = 1MB` to test past initial CDN trickle).
     - Client-specific headers: `User-Agent`, `Origin`, `Referer`.
   - Validates response:
     - HTTP code must be 200..299 (or 206 Partial Content). Codes 403, 404, 410 flag client refusal.
     - `Content-Type` must begin with `audio/` (rejecting HTML error/captcha challenge pages).
     - Must successfully read `PROBE_READ_BYTES` (16 KB) from response body to verify connection isn't stalled.
   - If probe fails, `skipClients += found.profileId` and extraction retries up to 3 times (`INNERTUBEX_ATTEMPTS = 3`).
6. **NewPipe Failsafe (JVM-only in Upstream)**:
   - On Android/Desktop, if InnerTubeX exhausts all clients, it falls back to `NewPipe.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")`.
   - **Crucial Architectural Difference for iOS**: NewPipe relies on `org.schabi.newpipe.extractor:NewPipeExtractor` and Rhino JS, which are JVM-only and cannot compile to iOS. On iOS, stream extraction relies entirely on the KMP-vendored `:innertubex` catalog with JavaScriptCore cipher deciphering and headless WKWebView BotGuard minting.

### 2.2 Client Waterfall & Fallback Ordering

InnerTubeX manages a catalog of Innertube clients (`PlaybackClientCatalog`). Each client has distinct authentication, cipher, and anti-bot requirements:

```
┌────────────────────────────────────────────────────────┐
│                   STREAM REQUEST                       │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
         [Tier 1: ANDROID_VR Client Family]
         - ANDROID_VR_1_65_10 / ANDROID_VR_NO_AUTH
         - Advantage: Often serves raw URLs without complex cipher
         - Header: User-Agent Oculus/Android 12L (No Origin/Referer)
         - Narrow range chunks (512 KiB - 1 MiB)
                           │
                 Refused / 403 / Missing?
                           │
                           ▼
         [Tier 2: IOS Native Client Family]
         - IOS (21.26.4 / 21.29.1) / IPADOS / IOS_MUSIC
         - Advantage: Native Apple hardware format compatibility (AAC/M4A)
         - Header: com.google.ios.youtube/21.26.4 (iPhone16,2; iOS 18_3_2)
         - May require signature deciphering / n-param transform
                           │
                 Refused / 403 / Missing?
                           │
                           ▼
         [Tier 3: WEB_REMIX Client Family (YouTube Music Web)]
         - WEB_REMIX (1.20260707.12.00)
         - Requires: BotGuard PoToken (player + streaming) + JS Cipher
         - Headers: Origin: https://music.youtube.com, Referer: https://music.youtube.com/
         - Unlocks: Full catalog including music tracks, high-quality audio
                           │
                 Refused / 403 / Missing?
                           │
                           ▼
         [Tier 4: TVHTML5 / Embedded Fallback]
         - TVHTML5_SIMPLY_EMBEDDED_PLAYER / WEB_EMBEDDED_PLAYER
         - Failsafe fallback before declaring unplayable
```

### 2.3 iPadOS Audio Format Prioritization: AAC/M4A vs WebM/Opus

On Desktop (`DesktopPlayback.kt`), BitChord preferred WebM/Opus (`itag 251` ~160kbps, `itag 250` ~70kbps) because the desktop engine decoded audio via FFmpeg / Concentus.

On iPadOS:
- **`AVAudioEngine` and `AVAudioFile` / `ExtAudioFile` natively hardware-decode MP4/M4A AAC (`itag 140` 128kbps AAC-LC, `itag 141` 256kbps AAC-LC) with zero CPU overhead and maximum battery life**.
- Apple's CoreAudio does NOT natively decode WebM container (`audio/webm`) without a demuxer!
- **Specification Mandate for iPadOS**:
  1. `InnerTubeXResolver.extract(..., requireM4a = true)` must prioritize **`audio/mp4` (`mp4a.40.2` / `itag 140` / `itag 141`)** first.
  2. For tracks where only WebM/Opus (`itag 251`) is available, include a lightweight WebM/EBML demuxer and pass the raw Opus frames to Apple's native `AudioConverterRef` (`kAudioFormatOpus` -> `kAudioFormatLinearPCM`, supported in iOS `AudioToolbox` since iOS 11).

---

## 3. Cipher Deobfuscation via Apple JavaScriptCore (`JSContext`)

### 3.1 Background & Threat Model

YouTube obfuscates playback streams using two primary mechanisms inside its HTML5 player script (`base.js`):
1. **Signature Deciphering (`s` parameter)**: A scrambled signature string passed via `s=` or `sp=sig` in ciphered streaming format URLs that must be passed through a dynamic permutation (swap, reverse, slice) function before appending as `&sig=`.
2. **`n` Parameter Throttling Transform**: A throttling parameter `n=` present in format URLs. If not transformed by the player's JavaScript algorithm, YouTube's CDN throttles download bandwidth to ~40-60 KB/s, causing constant buffering and audio dropouts.

In `innertubex`:
- Remote player configs are fetched from `player_configs.json` (`ZemerTeam/zemer-cipher`).
- If a hash is unknown or stale, `YouTubeCipherService` downloads the player `base.js`, parses the function bodies using regex/AST scanners (`PlayerScriptParser.kt`), and executes the solver in a JavaScript engine.
- Upstream uses `QuickJsEngine.kt` backed by `com.dokar.quickjs.QuickJs` (a JNI C++ wrapper around QuickJS).

### 3.2 Native iOS Implementation via `JavaScriptCore`

On iOS, QuickJS JNI cannot be used. Instead, Apple provides **`JavaScriptCore.framework`**, built into every iOS release.

#### Advantages of JavaScriptCore on iPadOS:
- **Zero Dependencies**: Linked with `-framework JavaScriptCore` in Gradle linker options.
- **Synchronous & Fast**: Executes in < 1 ms in-process on the current coroutine thread without thread jumping.
- **Background Immunity**: Unlike `WKWebView`, `JSContext` is an in-process JavaScript virtual machine that does **not** get frozen or terminated by the iOS WebKit daemon when BitChord is running in the background or locked.
- **Full ES6+ Support**: Accurately executes modern YouTube player transformations.

#### Architecture Specification: `JsEvaluator.ios.kt`

```kotlin
package com.music.bitchord.playback

import platform.JavaScriptCore.*

actual class JsEvaluator actual constructor() {
    private val context: JSContext = JSContext().apply {
        // Suppress window/document errors if player script inspects DOM
        evaluateScript("var window = globalThis; var self = globalThis;")
    }

    actual fun evaluateScript(script: String, functionName: String, arg: String): String? {
        context.evaluateScript(script)
        val function = context.objectForKeyedSubscript(functionName) ?: return null
        val result = function.callWithArguments(listOf(arg))
        return if (result != null && !result.isUndefined() && !result.isNull()) {
            result.toString()
        } else {
            null
        }
    }

    actual fun execute(script: String) {
        context.evaluateScript(script)
    }

    actual fun close() {
        // JSContext is ARC-managed in Kotlin/Native
    }
}
```

---

## 4. Headless BotGuard `poToken` Generation (`WKWebView`)

### 4.1 BotGuard Protocol Architecture

For web clients (`WEB_REMIX`, `WEB`), YouTube requires a **Proof of Origin Token (`poToken`)** generated by Google's BotGuard VM inside a real browser DOM. A valid `poToken` binds the client's session (`visitorData` or `dataSyncId`) and the requested `videoId`, preventing HTTP 403 Forbidden and CAPTCHA challenges.

The architecture comprises:
- `PoTokenGenerator.kt` (orchestrator, manages session lifecycle, timeouts, and fallback).
- `PoTokenMinter` (interface for platform browser instances).
- `PO_TOKEN_HTML` (HTML page containing `loadBotGuard`, `runBotGuard`, `createPoTokenMinter`, and `obtainPoToken`).

### 4.2 The 3-Stage BotGuard Handshake

```
┌────────────────────────────────────────────────────────────────────────┐
│                        IOSPoTokenWebView (WKWebView)                   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
    Stage 1: Challenge Fetch        ▼
    HTTP POST https://www.youtube.com/api/jnn/v1/Create
    Payload: [ "$REQUEST_KEY" ]
    Headers: x-goog-api-key, x-user-agent: grpc-web-javascript/0.1
    Response: { challengeData: { globalName, program } }
                                    │
    Stage 2: BotGuard VM Execution  ▼
    WKWebView.evaluateJavaScript("runBotGuard(data)...")
    Yields: botguardResponse + webPoSignalOutput
                                    │
    Stage 3: Integrity Token Mint   ▼
    HTTP POST https://www.youtube.com/api/jnn/v1/GenerateIT
    Payload: [ "$REQUEST_KEY", "$botguardResponse" ]
    Response: { integrityToken, expirationTimeInSeconds }
                                    │
    Stage 4: Minter Creation        ▼
    WKWebView.evaluateJavaScript("createPoTokenMinter(webPoSignalOutput, integrityToken)...")
    Callback to Kotlin: onMinterCreated() (Initialization Complete!)
                                    │
    Runtime: Token Minting          ▼
    1. Streaming PoToken: obtainPoToken(stringToU8(visitorData)) -> Base64
    2. Player PoToken:    obtainPoToken(stringToU8(videoId))     -> Base64
```

### 4.3 iOS Headless `WKWebView` Implementation Details

1. **Instantiation**:
   - Must be instantiated on the main UI thread (`Dispatchers.Main`):
     ```kotlin
     val config = WKWebViewConfiguration().apply {
         preferences.javaScriptEnabled = true
     }
     val webView = WKWebView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0), configuration = config)
     ```
2. **Bridge Communication (`WKScriptMessageHandler`)**:
   - Register a script message handler named `"bitchordPoToken"`:
     ```kotlin
     config.userContentController.addScriptMessageHandler(messageHandler, name = "bitchordPoToken")
     ```
   - Injected routing script maps `console.log` and JS callbacks to `window.webkit.messageHandlers.bitchordPoToken.postMessage({ type: '...', ... })`.
3. **Caching & Re-minting Rules**:
   - Token validity: The integrity token carries an expiration time (typically 6 hours).
   - `expirationInstant = Instant.now() + expirationTimeInSeconds - 10 minutes`.
   - Streaming token (`visitorData`) is minted once per session; track tokens (`videoId`) are minted on demand.
   - If `obtainPoToken` throws or times out (8 seconds ceiling), `IOSPoTokenWebView` marks `isDead = true`, closes the web view, and recreates a fresh instance.

---

## 5. Authentication Flow & Secure iOS Keychain Storage

### 5.1 Account Identity Model

BitChord supports multi-account YouTube Music and Spotify sessions:
- **`GoogleAccountSession`**: Represents a logged-in Google account (`accountId`, `name`, `email`, `avatar`, `cookie`, `sourceProfile`).
- **`YouTubeProfile`**: Sub-channel under the account:
  - Personal channel: `pageId = null`, uses account's default channel.
  - Brand accounts: carries specific `pageId` (`X-Goog-PageId`), `dataSyncId` (`context.user.onBehalfOfUser`), and `authUser` (`X-Goog-AuthUser: "0"`).
- **`SpotifySession`**: Stores `sp_dc` cookie for Spotify library scraping and Canvas query matching.

### 5.2 Login Flows via Modal `WKWebView`

#### Google / YouTube Music Login:
- URL: `https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F`
- Intercept navigation in `WKNavigationDelegate`. When host equals `music.youtube.com`:
  - Access `WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { cookies -> ... }`.
  - Extract essential cookies: `SAPISID`, `__Secure-3PAPISID`, `__Secure-1PAPISID`, `SID`, `HSID`, `SSID`, `APISID`, `LOGIN_INFO`, `VISITOR_INFO1_LIVE`.
  - Validate presence of API SID using `AuthStore.hasApiSid(cookieString)`.
  - Fetch account details and channel switcher list from `https://music.youtube.com/youtubei/v1/account/account_menu`.

#### Spotify Login:
- URL: `https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F`
- As soon as `sp_dc` cookie appears in `httpCookieStore`, dismiss modal and save session.

### 5.3 `SAPISIDHASH` Authorization Generation

All authenticated YouTube Music API requests must carry the `Authorization: SAPISIDHASH <timestamp>_<sha1>` header:
- Formula:
  $$\text{timestamp} = \left\lfloor \frac{\text{System.currentTimeMillis()}}{1000} \right\rfloor$$
  $$\text{hash} = \text{SHA1}\left(\text{timestamp} + \text{" "} + \text{SAPISID} + \text{" "} + \text{origin}\right)$$
  $$\text{header} = \text{"SAPISIDHASH "} + \text{timestamp} + \text{"\_"} + \text{hex(hash)}$$
- Where `origin` is `https://music.youtube.com` (or `https://www.youtube.com` for account menus).
- On iOS, the SHA-1 digest is computed using `platform.CoreCrypto.CC_SHA1`.

### 5.4 Secure iOS Keychain Storage (`Security.framework`)

On Android, credentials were saved using `EncryptedSharedPreferences`. On Desktop, BitChord used Linux SecretService or fallback preferences.

On iOS, all credentials (`GoogleAccountSession`, `cookie`, `sp_dc`, Discord tokens, WebDAV passwords) must be stored in the **iOS Keychain**:
- **Framework**: `platform.Security.*`
- **Class**: `kSecClassGenericPassword`
- **Service Name**: `com.music.bitchord.auth`
- **Access Policy**: `kSecAttrAccessibleAfterFirstUnlock` (accessible when device is unlocked; remains accessible in background while playing audio).

#### Keychain API Mapping in Kotlin/Native:

| Operation | Keychain Function | Attributes Dictionary |
| :--- | :--- | :--- |
| **Save / Add** | `SecItemAdd` | `kSecClass: kSecClassGenericPassword`, `kSecAttrService: "com.music.bitchord"`, `kSecAttrAccount: key`, `kSecValueData: data`, `kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlock` |
| **Read / Lookup** | `SecItemCopyMatching` | `kSecClass: kSecClassGenericPassword`, `kSecAttrService: "com.music.bitchord"`, `kSecAttrAccount: key`, `kSecReturnData: kCFBooleanTrue`, `kSecMatchLimit: kSecMatchLimitOne` |
| **Update** | `SecItemUpdate` | Matches by `kSecAttrService` + `kSecAttrAccount`, updates `kSecValueData` |
| **Delete** | `SecItemDelete` | Matches by `kSecAttrService` + `kSecAttrAccount` |

---

## 6. Native C++17 DSP Analyzer & Kotlin/Native `cinterop` Bridge

### 6.1 Upstream C++ Engine Audit

The BitChord native analyzer sits in `native/analyzer/` and consists of 5 files:
1. `audio_analysis.h` / `audio_analysis.cpp` (650 lines):
   - Computes RMS envelope, low-frequency energy curve, onset strength envelope, spectral centroid/flux, 12-bin chromagram, key detection (Krumhansl-Schmuckler profiles) -> Camelot key string (e.g. `"8B"`), phrase boundary segmentation, and mix-in/mix-out cue candidates (`main_drop`, `intro`, `outro`, `break`).
2. `tempo_analysis.cpp` (550 lines):
   - Comb-filter / autocorrelation tempo estimator, phase-locked loop (PLL) beat tracker, first-beat detector.
3. `resampler.h` / `resampler.cpp` (150 lines):
   - Polyphase windowed-sinc anti-aliased resampler (32 zero-crossings). Converts input PCM to `11,025 Hz` (general analysis), `22,050 Hz` (mel spectrogram), or `44,100 Hz` (vocal spectrogram).
4. `mel_spectrogram.h` / `mel_spectrogram.cpp` (200 lines):
   - Computes STFT (`n_fft = 1024`, `hop = 441` [50 fps], periodic Hann window), 128 Slaney mel filterbank bins (30 Hz–11,025 Hz), and `log1p(1000 * mag)` compression for `CPJKU/beat_this.onnx`.
5. `vocal_spectrogram.h` / `vocal_spectrogram.cpp` (170 lines):
   - Computes stereo planar STFT (`n_fft = 4096`, `hop = 1024`, periodic Hann window), 2049 linear bins, for `open-unmix` `vocal_activity.onnx`.

### 6.2 Parity Guarantee: Why C++ Must NOT Be Rewritten in Swift / vDSP

As explicitly stated in `BitChord_iPadOS_Master_Porting_Plan.md` (Ground Rule 3):
- The STFT, periodic Hann window (`periodic = true`), Slaney mel filterbank, and logarithmic scaling are mathematically calibrated to match `torchaudio` and `open-unmix`.
- Rewriting in `Accelerate` / `vDSP` will shift bin boundaries, alter floating-point rounding, shift neural network logits, and break Smart Automix beat phase alignment.
- **The C++17 code is 100% portable, standard-library-only C++ with zero external dependencies (no Boost, no Eigen). It compiles directly for iOS ARM64.**

### 6.3 C ABI Bridge Specification (`bitchord_ios_bridge.h` & `.cpp`)

Kotlin/Native `cinterop` binds cleanly to C symbols (`extern "C"`). We define `native/analyzer/ios_c_bridge/bitchord_ios_bridge.h`:

```c
#ifndef BITCHORD_IOS_BRIDGE_H
#define BITCHORD_IOS_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Analyzes mono float PCM and returns JSON string matching TrackFeatures.kt
char* bitchord_analyze_pcm_json(
    const float* samples,
    int32_t sample_count,
    double sample_rate,
    double duration_seconds
);

// Resamples mono float PCM with windowed-sinc anti-aliasing
float* bitchord_resample_pcm(
    const float* samples,
    int32_t sample_count,
    double input_rate,
    double output_rate,
    int32_t* out_sample_count
);

// Computes 128-bin log-mel spectrogram (22,050 Hz, hop 441)
float* bitchord_compute_mel_spectrogram(
    const float* samples,
    int32_t sample_count,
    double sample_rate,
    int32_t* out_frames
);

// Computes stereo STFT for vocal separation (44,100 Hz, hop 1024, 2049 bins)
float* bitchord_compute_vocal_spectrogram(
    const float* left_samples,
    const float* right_samples,
    int32_t sample_count,
    double sample_rate,
    int32_t* out_frames
);

void bitchord_free_string(char* str);
void bitchord_free_floats(float* ptr);

#ifdef __cplusplus
}
#endif

#endif // BITCHORD_IOS_BRIDGE_H
```

### 6.4 Static Library Compilation Script (`build_ios_static.sh`)

```bash
#!/bin/bash
set -e

IPHONEOS_SYSROOT=$(xcrun --sdk iphoneos --show-sdk-path)
IPHONESIM_SYSROOT=$(xcrun --sdk iphonesimulator --show-sdk-path)

mkdir -p build/ios-arm64 build/ios-sim-arm64

SRCS="../audio_analysis.cpp ../tempo_analysis.cpp ../resampler.cpp ../mel_spectrogram.cpp ../vocal_spectrogram.cpp bitchord_ios_bridge.cpp"
CFLAGS="-std=c++17 -O3 -ffast-math -fPIC -I.."

# 1. Device: arm64
xcrun clang++ $CFLAGS -arch arm64 -isysroot $IPHONEOS_SYSROOT -miphoneos-version-min=16.0 -c $SRCS
ar rcs build/ios-arm64/libbitchord_analysis.a *.o
rm *.o

# 2. Simulator: arm64
xcrun clang++ $CFLAGS -arch arm64 -isysroot $IPHONESIM_SYSROOT -mios-simulator-version-min=16.0 -c $SRCS
ar rcs build/ios-sim-arm64/libbitchord_analysis.a *.o
rm *.o
```

### 6.5 Kotlin/Native `cinterop` Definition (`bitchord_analysis.def`)

```ini
package = com.music.bitchord.native.analysis
headers = bitchord_ios_bridge.h
headerFilter = bitchord_ios_bridge.h
staticLibraries = libbitchord_analysis.a
linkerOpts = -lc++
```

---

## 7. Features Discovered & Edge Cases

### 7.1 Features Discovered
| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Stream Resolution | Multi-Client Waterfall | Falls back across Innertube identities when URLs are refused | `videoId: String`, `maxKbps: Int` | `Stream` (URL, kbps, mime, loudness) | Throws `PermanentlyUnplayableException` after attempts fail | `StreamResolver.kt:425` |
| 2 | Stream Resolution | URL Prober | Pre-verifies CDN byte delivery with Range requests before player scheduling | `url: String`, client headers | `Probe.OK`, `REFUSED`, `UNREACHABLE` | Benches failing client for 10 minutes | `StreamResolver.kt:771` |
| 3 | Stream Resolution | Format Prioritization | Selects AAC/M4A first on iOS for hardware decoding, Opus second | `AudioQuality.MP4` / `AUTO` | `Extracted` audio format | Falls back to next available bitrate | `InnerTubeXResolver.kt:231` |
| 4 | Cipher | Signature Decipher | Permutes scrambled `s` parameter in YouTube stream URLs | `s: String`, player script | Deciphered `sig` string | Throws `CipherException`; re-downloads `player_configs.json` | `YouTubeCipherService.kt` |
| 5 | Cipher | `n` Parameter Transform | Evaluates player JS throttling transform to uncap streaming rate | `n: String`, player script | Uncapped `n` value | Degrades to throttled stream (40 KB/s) if failed | `QuickJsEngine.kt` / `DesktopPlayback.kt` |
| 6 | Cipher | JSContext Evaluator | In-process native ES6+ JavaScript execution on iOS | Script body, function name, arg | String result | Returns `null` on script error; logs error | `JsEvaluator.ios.kt` |
| 7 | BotGuard PoToken | BotGuard Challenge Handshake | Obtains challenge, executes VM, generates integrity token | Request key, Google API key | Initialized `PoTokenMinter` | Re-attempts up to timeout (8s), throws `PoTokenException` | `DesktopPoTokenWebView.kt:126` |
| 8 | BotGuard PoToken | Token Minting | Mints visitorData streaming token and videoId player token | Identifier string | Base64 PoToken string | Re-creates minter if dead or expired | `PoTokenGenerator.kt:106` |
| 9 | Auth | Google In-App Sign-In | In-app login modal capturing cookies from `music.youtube.com` | Google login URL | Cookie string containing `SAPISID` | Retains anonymous session if dismissed | `DesktopWebSignIn.kt:52` |
| 10 | Auth | SAPISIDHASH Generator | Computes SHA-1 authorization signature over cookie and timestamp | `SAPISID`, origin URL | `SAPISIDHASH <t>_<sha1>` string | Returns empty string if cookie lacks SAPISID | `Innertube.kt:1251` |
| 11 | Auth | Multi-Account Management | Stores and switches multiple personal & brand YouTube identities | `AccountIdentity`, channels list | Active `Session` in force | Falls back to personal channel | `DesktopAccounts.kt:230` |
| 12 | Auth | iOS Keychain Store | Encrypted-at-rest credential storage via iOS Security framework | Key, value (bytes) | Boolean success / retrieved bytes | Returns null if item not found | `AuthStore.kt` / `IOSKeychainStore` |
| 13 | Audio DSP | Whole-Track Analysis | Computes BPM, Camelot key, cue points, and energy curve | Float32 PCM (11,025 Hz), duration | `TrackFeatures.Features` (JSON) | Returns null on invalid input or rate mismatch | `audio_analysis.cpp:586` |
| 14 | Audio DSP | Mel Spectrogram | Computes 128-bin log-mel spectrogram for `beat_this.onnx` | Float32 PCM (22,050 Hz) | Flattened FloatArray `[frames * 128]` | Returns empty array if rate != 22,050 Hz | `mel_spectrogram.cpp:160` |
| 15 | Audio DSP | Vocal Spectrogram | Computes stereo STFT magnitude for `vocal_activity.onnx` | Planar Stereo PCM (44,100 Hz) | Flattened FloatArray `[2 * 2049 * frames]` | Returns empty array if rate != 44,100 Hz | `vocal_spectrogram.cpp:76` |
| 16 | Audio DSP | Sinc Resampler | Windowed-sinc anti-aliased sample-rate converter (32 taps) | Float32 PCM, input rate, output rate | Resampled FloatArray | Returns unchanged if rates match, empty if invalid | `resampler.cpp:57` |

### 7.2 Edge Cases
| # | Feature | Input | Observed Behavior |
|---|---------|-------|-------------------|
| 1 | URL Probe | `clen` (content length) < 1 MiB | Probe starts at byte 0 instead of `AUTH_BOUNDARY_BYTES` (1048576) to avoid 416 Range Not Satisfiable. |
| 2 | URL Probe | HTTP 429 Too Many Requests | Throws `ReCaptchaException` and marks track unplayable for 10 minutes. |
| 3 | Stream Resolution | Age-Restricted Track (Anonymous) | Throws `AgeRestrictedContentException` with prompt: "Sign in to YouTube to play it." |
| 4 | Stream Resolution | Age-Restricted Track (Signed In) | Uses authenticated session with `SAPISIDHASH` + `WEB_REMIX` client with BotGuard PoToken. |
| 5 | PoToken Minting | App backgrounded during BotGuard load | WebKit process suspension can delay response. Managed with 8s `withTimeout`; fails gracefully to non-PoToken client. |
| 6 | PoToken Minting | Token lifetime expiration (6 hours) | `isExpired` becomes true; next request seamlessly spawns new minter without failing track. |
| 7 | Keychain | Device reboot before first unlock | `kSecAttrAccessibleAfterFirstUnlock` prevents reads until first unlock; background audio won't crash if already cached. |
| 8 | DSP Analyzer | Zero-length audio or silent file | Returns default struct with `duration = 0`, `bpm = 0`, `key = ""` without crash or division by zero. |
| 9 | DSP Analyzer | High-sample-rate audio (96 kHz / 192 kHz FLAC) | Sinc resampler cleanly converts to 11,025 Hz / 22,050 Hz / 44,100 Hz with anti-aliasing stopband suppression > 90 dB. |
| 10 | JavaScriptCore | Obfuscated player script using `window` / `document` | Polyfilled global object `var window = globalThis; var self = globalThis;` prevents `ReferenceError`. |

---

## 8. File Inventory & Interface Mapping for WP-03, WP-04, and WP-07

### 8.1 Work Package 03: Stream Resolution & Anti-Bot Architecture
* **Upstream Source Files**:
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/StreamResolver.kt`
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/InnerTubeXResolver.kt`
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/PlayerClient.kt`
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/potoken/PoTokenGenerator.kt`
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/potoken/PoTokenPage.kt`
  * `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopPoTokenWebView.kt`
* **Target Files to Create/Migrate**:
  * `shared/src/commonMain/kotlin/com/music/bitchord/data/innertube/StreamResolver.kt` (purge OkHttp/NewPipe)
  * `shared/src/commonMain/kotlin/com/music/bitchord/data/innertube/InnerTubeXResolver.kt`
  * `shared/src/commonMain/kotlin/com/music/bitchord/data/innertube/PlayerClient.kt`
  * `shared/src/commonMain/kotlin/com/music/bitchord/data/innertube/potoken/PoTokenGenerator.kt`
  * `shared/src/commonMain/kotlin/com/music/bitchord/data/innertube/potoken/PoTokenPage.kt`
  * `shared/src/commonMain/kotlin/com/music/bitchord/playback/JsEvaluator.kt` (`expect class JsEvaluator`)
  * `shared/src/iosMain/kotlin/com/music/bitchord/playback/JsEvaluator.ios.kt` (`actual class JsEvaluator` via `JSContext`)
  * `shared/src/iosMain/kotlin/com/music/bitchord/data/innertube/potoken/IOSPoTokenWebView.kt` (`WKWebView` minter)

### 8.2 Work Package 04: Native C++17 DSP Engine & cinterop Bridge
* **Upstream Source Files (Verbatim C++17 Compilation)**:
  * `native/analyzer/audio_analysis.h` / `audio_analysis.cpp`
  * `native/analyzer/tempo_analysis.cpp`
  * `native/analyzer/resampler.h` / `resampler.cpp`
  * `native/analyzer/mel_spectrogram.h` / `mel_spectrogram.cpp`
  * `native/analyzer/vocal_spectrogram.h` / `vocal_spectrogram.cpp`
  * `app/src/main/cpp/jni/analysis_jni.cpp` (Reference for JSON serialization)
  * `app/src/main/cpp/jni/mel_jni.cpp` (Reference for mel float packing)
  * `app/src/main/cpp/jni/vocal_jni.cpp` (Reference for vocal float packing)
* **Target Files to Create**:
  * `native/analyzer/ios_c_bridge/bitchord_ios_bridge.h`
  * `native/analyzer/ios_c_bridge/bitchord_ios_bridge.cpp`
  * `native/analyzer/ios_c_bridge/build_ios_static.sh`
  * `shared/src/nativeInterop/cinterop/bitchord_analysis.def`
  * `shared/src/iosMain/kotlin/com/music/bitchord/playback/smart/NativeTrackAnalyzer.ios.kt`
  * `shared/src/iosMain/kotlin/com/music/bitchord/playback/smart/MelSpectrogram.ios.kt`
  * `shared/src/iosMain/kotlin/com/music/bitchord/playback/smart/VocalSpectrogram.ios.kt`
  * `shared/src/commonMain/kotlin/com/music/bitchord/playback/smart/TrackFeatures.kt` (move from `jvmSharedMain`)

### 8.3 Work Package 07: Authentication & Secure Keychain Storage
* **Upstream Source Files**:
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/Innertube.kt` (`sapisidHash`)
  * `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopYouTubeAuth.kt`
  * `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopAccounts.kt`
  * `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopWebSignIn.kt`
  * `app/src/main/java/com/music/bitchord/auth/AuthStore.kt`
* **Target Files to Create/Migrate**:
  * `shared/src/commonMain/kotlin/com/music/bitchord/auth/AccountIdentity.kt`
  * `shared/src/commonMain/kotlin/com/music/bitchord/auth/YouTubeAuthSession.kt`
  * `shared/src/commonMain/kotlin/com/music/bitchord/auth/YouTubeCookieAuth.kt` (pure KMP SHA-1)
  * `shared/src/commonMain/kotlin/com/music/bitchord/auth/KeychainStore.kt` (`expect` interface)
  * `shared/src/iosMain/kotlin/com/music/bitchord/auth/IOSKeychainStore.kt` (`actual` via `Security.framework`)
  * `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/auth/IOSWebSignIn.kt` (modal `WKWebView` login)
  * `shared/src/iosMain/kotlin/com/music/bitchord/playback/IOSNowPlayingManager.kt` (`MPNowPlayingInfoCenter`)
