# BitChord iPadOS Port — Audio Engine, UI Adaptation & Packaging Survey Report

**Author**: Survey Explorer 3 (Audio & UI Survey Explorer)  
**Date**: 2026-10-16  
**Status**: COMPLETE  
**Target Platform**: iPadOS 16.0+ (`iosArm64` devices & `iosSimulatorArm64` simulators)  
**Target Artifact**: Sideload-ready `.ipa` bundle for AltStore / SideStore / TrollStore / Sideloadly  

---

## 1. Executive Summary

BitChord v1.8-beta1 is an open-source Kotlin Multiplatform (KMP) YouTube Music client featuring advanced DJ Smart Automix, real-time neural beat and vocal activity tracking, 10-band parametric equalisation, animated Apple Music-style fluid mesh gradients, and word-synchronised lyrics.

On Desktop (`:desktopApp`), audio playback is driven by a custom software mixing pipeline (`DesktopPlaybackEngine.kt`, 57 KB) utilizing `xt.audio` (PortAudio/WASAPI/CoreAudio), `JavaSound`, `DesktopRangeStream.kt` (512 KB chunked HTTP streaming), and subprocess-based `ffmpeg` audio decoding.

For **iPadOS**, where background execution is strictly regulated, subprocess invocation is forbidden by the sandbox, and hardware audio acceleration is essential to prevent thermal throttling and battery drain, the audio architecture must be ported to **Apple's native `AVAudioEngine` and `AVFoundation` frameworks**. Concurrently, the UI must be adapted from desktop windowing to **iPadOS Stage Manager, Split View, 120Hz ProMotion, and Skia Metal shaders**, while packaging is unified into a standalone `.ipa` build pipeline.

This survey establishes the complete blueprint for **WP-05, WP-06, WP-09, WP-10, WP-11, and WP-12**.

---

## 2. Dual-Deck AVAudioEngine Architecture (WP-06 & WP-07)

### 2.1 Audio Graph Topology

The iPadOS playback engine (`IOSDualAudioEngine`) replaces `DesktopPlaybackEngine.kt` by hosting two completely symmetrical playback decks (`Deck A` and `Deck B`) coupled with transition DSP units, feeding a shared master equalizer, mixer, and hardware output.

```
┌──────────────────────────────────────────────┐
│                   DECK A                     │
│  [AVAudioPlayerNode (Deck A)]                │
│                     │                        │
│                     ▼                        │
│  [AVAudioUnitTimePitch (Deck A)]             │  <-- Independent rate (0.5x..2.0x), pitch = 0
│                     │                        │
│                     ▼                        │
│  [AVAudioUnitEQ (Deck A Transition Filter)]  │  <-- High-Pass / Low-Pass / Vocal Notch
│                     │                        │
│                     ▼                        │
│  [AVAudioMixerNode (Deck A Mixer)]           │  <-- Equal-Power Cosine Gain + ReplayGain
└─────────────────────┬────────────────────────┘
                      │
                      │   ┌──────────────────────────────────────────────┐
                      │   │                   DECK B                     │
                      │   │  [AVAudioPlayerNode (Deck B)]                │
                      │   │                     │                        │
                      │   │                     ▼                        │
                      │   │  [AVAudioUnitTimePitch (Deck B)]             │
                      │   │                     │                        │
                      │   │                     ▼                        │
                      │   │  [AVAudioUnitEQ (Deck B Transition Filter)]  │
                      │   │                     │                        │
                      │   │                     ▼                        │
                      │   │  [AVAudioMixerNode (Deck B Mixer)]           │
                      │   └─────────────────────┬────────────────────────┘
                      │                         │
                      ▼                         ▼
┌────────────────────────────────────────────────────────┐
│  [AVAudioUnitEQ (Master 10-Band Parametric EQ)]        │  <-- 31Hz..16kHz Presets + Preamp
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│  [AVAudioEngine.mainMixerNode]                         │
│    └── Audio Tap (1024 frames @ 44.1/48kHz)           │  <-- vDSP FFT -> VisualizerDataSource
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│  [AVAudioEngine.outputNode]                            │  <-- Speakers / AirPods / USB-C DAC
└────────────────────────────────────────────────────────┘
```

### 2.2 Dual Decks & Sample-Accurate Playhead Tracking

- **Deck Nodes**: Each deck consists of an `AVAudioPlayerNode`.
  - For cached/downloaded files (stored in `<Application Support>/BitChord/downloads/` or `<Caches>/BitChord/audio_cache/`), audio is loaded via `AVAudioFile` and scheduled with sample accuracy:
    ```swift
    playerNode.scheduleSegment(
        audioFile,
        startingFrame: seekFrame,
        frameCount: remainingFrames,
        atTime: nil,
        completionHandler: { ... }
    )
    ```
  - For live streams undergoing progressive buffering, buffers are scheduled dynamically using `playerNode.scheduleBuffer(pcmBuffer)`.
- **Sample-Accurate Position Calculation**:
  Because `AVAudioPlayerNode.sampleTime` resets upon `stop()` or `scheduleSegment()`, the engine maintains a `baseSeekFrameOffset: AVAudioFramePosition` per deck:
  ```kotlin
  val nodeTime = playerNode.lastRenderTime ?: return lastKnownPositionMs
  val playerTime = playerNode.playerTimeForNodeTime(nodeTime) ?: return lastKnownPositionMs
  val currentFrame = baseSeekFrameOffset + playerTime.sampleTime
  val currentPositionMs = ((currentFrame.toDouble() / playerTime.sampleRate) * 1000.0).toLong()
  ```

### 2.3 Equal-Power Crossfade Curves (0–12s)

In upstream `DesktopPlaybackEngine.kt`, crossfades are governed by `fadeSeconds` (configurable between 0.0 and 12.0 seconds, or dynamically resolved from `plan.fadeSeconds` based on `transitionBeats * beatSeconds`).

To prevent the 3 dB power drop at the midpoint of linear crossfades, `IOSDualAudioEngine` implements equal-power trigonometric volume envelopes:
$$V_{\text{out}}(t) = \cos\left(t \cdot \frac{\pi}{2}\right) \cdot G_{\text{replayGain, out}}$$
$$V_{\text{in}}(t) = \sin\left(t \cdot \frac{\pi}{2}\right) \cdot G_{\text{replayGain, in}}$$
Where $t \in [0.0, 1.0]$ represents normalised transition progress. The total acoustic power is preserved:
$$V_{\text{out}}^2(t) + V_{\text{in}}^2(t) = \cos^2\left(t \cdot \frac{\pi}{2}\right) + \sin^2\left(t \cdot \frac{\pi}{2}\right) = 1.0$$

These curves are applied by updating `deckAMixer.outputVolume` and `deckBMixer.outputVolume` in a 60 Hz coroutine loop (`delay(16L)`).

### 2.4 Tempo Ramping & Pitch Preservation (`AVAudioUnitTimePitch`)

Upstream BitChord's Smart Automix engine synchronises tempos across adjacent tracks when `TimeTransferMode` is enabled.
- Each deck routes through its own `AVAudioUnitTimePitch` node.
- `timePitch.pitch = 0.0` (cents) ensures zero pitch distortion while altering tempo.
- During transition:
  1. `outgoingRate` begins at `1.0` and ramps towards the target tempo ratio.
  2. `incomingRate` begins at `plan.initialNextRate` (clamped between `0.85` and `1.15`) and smoothly eases back to `1.0` as $t \to 1.0$:
     $$\text{rate}_{\text{in}}(t) = \text{initialNextRate} + (1.0 - \text{initialNextRate}) \cdot \text{smoothStep}(t)$$

### 2.5 Per-Deck Transition EQ & Master 10-Band EQ

1. **Per-Deck Transition EQ (`TransitionFilter.kt` integration)**:
   - Each deck has a dedicated `AVAudioUnitEQ(numberOfBands = 3)`.
   - Band 0: High-pass filter (`AVAudioUnitEQFilterTypeHighPass`) gliding up to `1,200 Hz`.
   - Band 1: Parametric notch (`AVAudioUnitEQFilterTypeParametric`, center `2,500 Hz`, $Q=2.0$) active when `VocalTracker` detects overlapping vocals to prevent vocal clutter.
   - Band 2: Low-pass filter (`AVAudioUnitEQFilterTypeLowPass`) gliding down from `20,000 Hz` to `400 Hz`.
   - **Bass Swap**: At $t = \text{plan.bassSwapFraction}$ (the drop boundary), incoming low frequencies are unmuted while outgoing low frequencies are abruptly cut, matching DJ club mixers.

2. **Master 10-Band Equalizer**:
   - Implemented using a 10-band `AVAudioUnitEQ` matching `EqualizerPreset.kt`:
     | Band | Center Freq | Filter Type | Bandwidth / Q |
     | :--- | :--- | :--- | :--- |
     | 0 | 31 Hz | Low Shelf | 1.0 Octave |
     | 1 | 62 Hz | Parametric Peaking | 1.0 Octave ($Q \approx 1.414$) |
     | 2 | 125 Hz | Parametric Peaking | 1.0 Octave |
     | 3 | 250 Hz | Parametric Peaking | 1.0 Octave |
     | 4 | 500 Hz | Parametric Peaking | 1.0 Octave |
     | 5 | 1,000 Hz | Parametric Peaking | 1.0 Octave |
     | 6 | 2,000 Hz | Parametric Peaking | 1.0 Octave |
     | 7 | 4,000 Hz | Parametric Peaking | 1.0 Octave |
     | 8 | 8,000 Hz | Parametric Peaking | 1.0 Octave |
     | 9 | 16,000 Hz | High Shelf | 1.0 Octave |
   - Preamp: `masterEqNode.globalGain` adjusts from `-12.0 dB` to `+12.0 dB`.
   - Supports all 17 upstream presets (`FLAT`, `ACOUSTIC`, `BASS_BOOST`, `BASS_CUT`, `VOCAL`, `TREBLE_BOOST`, `TREBLE_CUT`, `LOUDNESS`, `SPOKEN_WORD`, `ELECTRONIC`, `ROCK`, `HIP_HOP`, `JAZZ`, `CLASSICAL`, `SMALL_SPEAKERS`, `LATE_NIGHT`, `CUSTOM`).

### 2.6 Real-Time FFT Spectrum & Waveform Tap

An audio tap is installed on `engine.mainMixerNode`:
```swift
engine.mainMixerNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, time in
    // Real-time processing
}
```
- Apple's `Accelerate.framework` (`vDSP_dft_zrop`) computes a 64-band log-frequency magnitude spectrum.
- The spectrum and RMS energy frames are pushed to Kotlin's `VisualizerDataSource` `StateFlow`.
- Drives `WaveformSeekBar.kt`, `PlayerWavyControls.kt`, and the audio spectrum visualizer at 60/120 FPS.

---

## 3. Anti-Throttling & Stream Protection: Custom `AVAssetResourceLoaderDelegate` (`bitchord-chunked://`)

### 3.1 The Problem: YouTube CDN Restrictions

YouTube's audio CDNs (`*.googlevideo.com/videoplayback?...`) actively thwart non-browser and non-official players:
1. **HTTP 403 Forbidden**: Stream URLs are bound to client signatures, `poToken` (BotGuard Proof of Origin), and cookies (`SAPISIDHASH`). Continuous requests without proper headers or expired URLs are immediately refused with HTTP 403.
2. **Bandwidth Throttling**: The YouTube CDN uses adaptive rate limiting. If an application attempts to download the full media file in a single unbounded connection, transfer speeds are throttled to below real-time playback rates.
3. **AVPlayer URL Scheme Restrictions**: iOS `AVPlayer` will not allow arbitrary header manipulation on standard `https://` URLs, making it impossible to inject session cookies or handle custom retry waterfalls natively.

### 3.2 The Architecture: `bitchord-chunked://` Virtual Scheme

BitChord intercepts stream requests using a custom URL scheme:
`https://rr---.googlevideo.com/videoplayback?...` $\to$ `bitchord-chunked://rr---.googlevideo.com/videoplayback?...`

An instance of `AVAssetResourceLoaderDelegate` (`IOSChunkedResourceLoaderDelegate`) is registered on the asset's `resourceLoader`:

```swift
let url = URL(string: streamUrl.replacingOccurrences(of: "https://", with: "bitchord-chunked://"))!
let asset = AVURLAsset(url: url)
asset.resourceLoader.setDelegate(chunkedDelegate, queue: resourceLoaderQueue)
```

```
┌────────────────────────────────────────────────────────┐
│  AVPlayer / AVAudioFile Request                        │
└───────────────────────────┬────────────────────────────┘
                            │ requests byte range [offset, offset + length]
                            ▼
┌────────────────────────────────────────────────────────┐
│  IOSChunkedResourceLoaderDelegate                      │
│  (intercepts 'bitchord-chunked://')                    │
└─────────────┬────────────────────────────┬─────────────┘
              │                            │
   Cache Hit? │                            │ Cache Miss
              ▼                            ▼
┌─────────────────────────┐   ┌──────────────────────────────────────────────┐
│  Read from CacheStore   │   │  Slice into 512 KB Windows                   │
│  (<Caches>/audio_cache) │   │  (DEFAULT_WINDOW = 524288 bytes)             │
└─────────────┬───────────┘   └──────────────────────┬───────────────────────┘
              │                                      │
              │                       HTTP GET Range │ bytes=start-end
              │                       + Custom Headers (UA, Cookie, PoToken)
              │                                      ▼
              │                       ┌──────────────────────────────────────┐
              │                       │  YouTube CDN (googlevideo.com)       │
              │                       └──────────────┬───────────────────────┘
              │                                      │
              │                                      │ Chunk received
              │                                      ├───────────────────────┐
              │                                      ▼                       ▼
              │                       ┌─────────────────────────┐ ┌──────────┴────────┐
              │                       │ Write to CacheStore     │ │ dataRequest.respond│
              │                       │ (Progressive caching)   │ │ (with: chunkData)  │
              │                       └─────────────────────────┘ └──────────┬────────┘
              │                                                              │
              ▼                                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  loadingRequest.finishLoading() -> Audio Continues Seamlessly                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Request Handling Details

1. **Content Information Request (`contentInformationRequest`)**:
   - `contentType`: Sets `"com.apple.m4a-audio"` (`audio/mp4`) or `"audio/aac"`.
   - `contentLength`: Populated with total stream size (queried from cache metadata or initial HEAD/Range probe).
   - `isByteRangeAccessSupported`: Always set to `true`.

2. **Data Request (`dataRequest`) & 512 KB Windowing**:
   - Upstream `DesktopRangeStream.kt` establishes:
     `public static final int DEFAULT_WINDOW = 524288;` (512 KB).
   - When `dataRequest.requestedLength` spans multiple megabytes, the delegate slices the request into 512 KB windows:
     - `Range: bytes=${start}-${start + 524287}`
     - Headers:
       - `User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)...`
       - `Referer: https://music.youtube.com/`
       - `Origin: https://music.youtube.com`
       - `Cookie: SAPISID=...; __Secure-3PAPISID=...;`
   - Each window is fetched asynchronously via `NSURLSession` / `ktor-client-darwin` using persistent HTTP/2 connections.
   - Received bytes are delivered immediately to `dataRequest.respond(with: data)`.
   - Upon completing all ranges, `loadingRequest.finishLoading()` is invoked.

3. **Dual-Sink Progressive Caching**:
   - Every received chunk is concurrently written to the file cache (`<Caches>/BitChord/audio_cache/<videoId>.m4a`).
   - When the track finishes, the record in `audio_cache` SQLite table is committed. Subsequent plays of the same track bypass the network entirely.

4. **HTTP 403 Forbidden Auto-Recovery**:
   - If YouTube responds with HTTP 403 on any chunk, the delegate triggers `StreamResolver.onPlaybackRefused(url, 403)`.
   - `StreamResolver` refreshes the `poToken` and deciphered signature, re-extracts an updated stream URL via the Innertube waterfall, and the delegate resumes fetching from the exact required byte offset without stalling the audio engine.

---

## 4. Background Audio & System Media Integration

### 4.1 AVAudioSession Configuration

To ensure audio playback survives screen locks, app switching, and Stage Manager minimizing:
```kotlin
val session = AVAudioSession.sharedInstance()
session.setCategory(
    category = AVAudioSessionCategoryPlayback,
    mode = AVAudioSessionModeDefault,
    options = AVAudioSessionCategoryOptionAllowAirPlay or AVAudioSessionCategoryOptionAllowBluetoothA2DP,
    error = null
)
session.setActive(true, error = null)
```
- **`UIBackgroundModes`**: `Info.plist` includes `audio`, `fetch`, and `processing`.

### 4.2 Interruption & Route Change Protocols

1. **`AVAudioSessionInterruptionNotification`**:
   - `AVAudioSessionInterruptionTypeBegan` (phone/FaceTime call, Siri, alarm): Pauses playback decks, saves playhead timestamp.
   - `AVAudioSessionInterruptionTypeEnded` with `AVAudioSessionInterruptionOptionShouldResume`: Re-activates `session`, restarts `AVAudioEngine`, and resumes playback smoothly.
2. **`AVAudioSessionRouteChangeNotification`**:
   - When AirPods or headphones are disconnected (`AVAudioSessionRouteChangeReasonOldDeviceUnavailable`), playback is immediately paused in compliance with Apple Human Interface Guidelines (HIG).

### 4.3 Lock Screen & Control Center (`MPNowPlayingInfoCenter`)

`IOSNowPlayingManager` synchronises track metadata with the system media player:
- **Properties**:
  - `MPMediaItemPropertyTitle`: Song title
  - `MPMediaItemPropertyArtist`: Artist name(s)
  - `MPMediaItemPropertyAlbumTitle`: Album name
  - `MPMediaItemPropertyPlaybackDuration`: Duration in seconds
  - `MPNowPlayingInfoPropertyElapsedPlaybackTime`: Current position in seconds
  - `MPNowPlayingInfoPropertyPlaybackRate`: `1.0` (playing) or `0.0` (paused)
  - `MPNowPlayingInfoPropertyDefaultPlaybackRate`: `1.0`
- **High-Resolution Artwork Pipeline**:
  - Asynchronously loads album art via Coil 3.
  - Constructs `MPMediaItemArtwork(boundsSize: CGSize(width: 600, height: 600))` providing crisp, unpixelated lock screen artwork.

### 4.4 Remote Command Center (`MPRemoteCommandCenter`)

Enables hardware buttons, Magic Keyboard media keys, Control Center, and Lock Screen scrubbing:
- `playCommand` / `pauseCommand` / `togglePlayPauseCommand`: Controls engine playback.
- `nextTrackCommand`: Triggers Smart Automix crossfade or immediate track skip.
- `previousTrackCommand`: If position > 3 seconds, seeks to beginning; otherwise plays previous track.
- `changePlaybackPositionCommand`: Scrub slider on lock screen. Receives `event.positionTime`, performs frame-accurate seek via `engine.seekTo(positionSeconds)`, and updates `nowPlayingInfo`.

---

## 5. Compose Multiplatform UI Adaptation for iPadOS (WP-10 & WP-11)

### 5.1 Root Architecture & SwiftUI Host

The iPadOS application is hosted inside `iosApp/iosApp/iOSApp.swift` wrapping a `ComposeUIViewController`:
```swift
import SwiftUI
import SharedUI

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```
In `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/MainViewController.kt`:
```kotlin
fun MainViewController(): UIViewController = ComposeUIViewController {
    AppUi()
}
```

### 5.2 Responsive Layout: Stage Manager, Split View & Breakpoints

iPadOS supports dynamic resizing across full screen, Stage Manager, Split View (1/3, 1/2, 2/3), and Slide Over.
BitChord adapts fluidly across three window width classes:

| Width Class | Screen Width | iPad Scenarios | UI Layout |
| :--- | :--- | :--- | :--- |
| **Compact** | `< 600 dp` | Slide Over, 1/3 Split View (11" iPad) | Bottom Navigation Bar + Bottom `PlayerDock` + Expandable full-screen `NowPlayingScreen` modal drawer |
| **Medium** | `600 dp .. 839 dp` | 1/2 Split View, Portrait 11"/13" iPad | Left Navigation Rail + Bottom `PlayerDock` with fluid slide-over panels |
| **Expanded** | `$\ge$ 840 dp` | Full-screen Landscape, Stage Manager Large | Left Navigation Rail + Split `LandscapePlayerLayout`: Left pane shows `LandscapeArtwork` with animated canvas; Right pane shows `LandscapeLyricsPane` or `PlayerQueue` |

- **State Preservation**: Because layouts use reactive `BoxWithConstraints` without unmounting `PlayerHost`, resizing Stage Manager windows preserves playhead, active scroll positions, and lyrics sync state seamlessly.

### 5.3 Liquid Glass & Optimized Haze Skia Shaders

Upstream BitChord utilizes `dev.chrisbanes.haze:haze` and `io.github.fletchmckee.liquid:liquid:1.1.1` for glassmorphic blur and material effects:
- On iOS, Compose Multiplatform 1.10.3 uses the **Skiko Metal backend**.
- `OptimizedHaze.kt`: Compiles to Skia `RuntimeEffect.makeForShader`, evaluating SkSL shaders on the GPU to blur behind navigation bars, player docks, and dialogs.
- `MeshGradientShader.kt`: Renders fluid, 4-color animated mesh gradients derived from track artwork (`PaletteQuantizer.kt` / `ArtworkPalette.kt`), executed completely in GPU hardware via Metal shaders.

### 5.4 120Hz ProMotion Optimization

On iPad Pro (M1, M2, M4):
- Add `<key>CADisableMinimumFrameDurationOnPhone</key><true/>` in `iosApp/Info.plist`.
- Configures Skiko's `CADisplayLink` to sync at 120 FPS.
- Enables butter-smooth lyrics kinetic scrolling, wavy slider oscillations, and mesh gradient drifts.

### 5.5 Word-Synced Lyrics with Apple-Like Syllable Reveal

- `PlayerLyrics.kt` and `LyricClock.kt` support syllable-level timing from Kugou, NetEase, and LrcLib.
- Syllables highlight in real time with progressive font weight, glow, and spring animations.
- Screen dimming is suppressed during lyrics display via `PlatformKeepScreenOn.ios.kt` (`UIApplication.sharedApplication.idleTimerDisabled = true`).

### 5.6 9:16 Instagram Story Lyrics Card Export Engine

`LyricsShareKt.kt` enables users to export lyrics cards formatted for social media stories:
1. **Selection UI**: User taps lines in `LyricsPickBar` (character budget managed by `fitsOnCard`).
2. **Skia 1080x1920 Offscreen Canvas**:
   - Allocates an offscreen Skia surface: `Surface.makeRasterN32Premul(1080, 1920)`.
   - Draws blurred artwork backdrop with dark gradient vignette.
   - Draws song title, artist, album art thumbnail, and styled lyrics text.
   - Encodes to PNG byte array.
3. **Native iOS Share Sheet (`UIActivityViewController`)**:
   - Converts PNG bytes to `UIImage`.
   - Presents `UIActivityViewController` anchored to the iPad popover source view, enabling direct sharing to Instagram Stories, Save Image, or Messages.

### 5.7 iPad Hardware Keyboard Shortcuts (`UIKeyCommand`)

Ported from `desktopApp/Main.kt` to the root `UIViewController`:
- `Space`: Toggle Play/Pause
- `Cmd + Right`: Next Track
- `Cmd + Left`: Previous Track
- `Right / Left`: Seek $\pm 5$ seconds
- `Up / Down`: Volume $\pm 5\%$
- `Cmd + F`: Focus Search
- `Cmd + L`: Toggle Lyrics Panel
- `Cmd + Q`: Toggle Queue Panel
- `Cmd + M`: Toggle Mute

---

## 6. Neural Track Analysis: ONNX Runtime C-API Integration on iOS (WP-05)

### 6.1 Bundled Neural Models

Two quantized INT8 ONNX models extracted from BitChord's production artifacts are bundled in `iosApp/Resources/`:
1. `models/beat_this_int8.onnx` (**4,533,252 bytes / 4.53 MB**): Quantized model based on `CPJKU/beat_this`, predicting beat and downbeat probabilities.
2. `models/vocals_umxhq_int8.onnx` (**9,054,206 bytes / 9.05 MB**): Quantized model based on `open-unmix`, predicting vocal energy presence.

### 6.2 Linking `onnxruntime-c` (`1.20.0`) via Kotlin/Native `cinterop`

- Integrated via CocoaPods in `iosApp/Podfile`:
  `pod 'onnxruntime-c', '1.20.0'`
- Configured in `shared/src/nativeInterop/cinterop/onnxruntime.def`:
  ```ini
  headers = onnxruntime_c_api.h
  headerFilter = onnxruntime_c_api.h
  linkerOpts = -framework Foundation
  ```

### 6.3 Hardware PCM Decoding via Apple's `AudioToolbox.ExtAudioFile`

Because spawning `ffmpeg` processes is forbidden on iOS, `IOSAudioFileDecoder` uses Apple's hardware-accelerated **`AudioToolbox.ExtAudioFile`**:
1. Opens cached audio file via `ExtAudioFileOpenURL`.
2. Sets `kExtAudioFileProperty_ClientDataFormat` to 32-bit floating-point linear PCM.
3. Decodes into:
   - **Mono 22,050 Hz `FloatArray`**: Passed to `bitchord_analyze_pcm_json` and `BeatTracker`.
   - **Stereo 44,100 Hz `Pair<FloatArray, FloatArray>`**: Passed to `VocalTracker`.

### 6.4 Inference Execution Pipeline

1. **`BeatTracker.kt` (`IOSBeatModel`)**:
   - Computes 128-bin mel spectrogram via C++ bridge (`bitchord_compute_mel_spectrogram`).
   - Slices into 1500-frame chunks with 1400-frame hop (50-frame overlap border).
   - Creates input tensor `"mel"` `[1, frames, 128]`.
   - Runs session requesting outputs `"beat"` and `"downbeat"`.
   - Peak picking extracts beat timestamps, downbeats, and Camelot BPM grid.
2. **`VocalTracker.kt` (`IOSVocalModel`)**:
   - Computes stereo STFT magnitude tensor via C++ bridge (`bitchord_compute_vocal_spectrogram`).
   - Slices into 6-second stereo chunks at 44,100 Hz (`[1, 2, 1487, frames]`).
   - Runs session requesting output `"vocal_energy"` `[1, frames]`.
   - Generates vocal presence probability envelope, marking vocal onset/offset boundaries.
3. **Memory Safety & Thermal Guard**:
   - All `OrtValue*` references are released inside `finally` blocks via `ortApi.ReleaseValue(tensor)`.
   - Background track analysis is strictly throttled using a `Semaphore(1)` on a single background worker to prevent audio dropouts or thermal throttling during playback.

---

## 7. Packaging & Distribution Pipeline (WP-12)

### 7.1 Xcode Project Structure (`iosApp/`)

```
iosApp/
├── iosApp.xcodeproj/
├── Podfile                          # pod 'onnxruntime-c', '1.20.0'
├── iosApp.entitlements              # Keychain access group & App group
├── Info.plist                       # Audio background modes, ProMotion, Stage Manager
├── Resources/
│   ├── beat_this_int8.onnx          # 4.53 MB
│   ├── vocals_umxhq_int8.onnx       # 9.05 MB
│   └── Assets.xcassets/             # App icons & Launch screen
└── iosApp/
    ├── iOSApp.swift                 # SwiftUI entry point + UIKeyCommand
    ├── ContentView.swift            # ComposeUIViewController host
    ├── Audio/
    │   ├── IOSDualAudioEngine.swift
    │   ├── IOSChunkedResourceLoaderDelegate.swift
    │   └── IOSNowPlayingManager.swift
    └── Video/
        └── IOSCanvasVideoPool.swift
```

### 7.2 Entitlements & Capabilities

`iosApp/iosApp.entitlements`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>keychain-access-groups</key>
    <array>
        <string>$(AppIdentifierPrefix)com.music.bitchord</string>
    </array>
    <key>com.apple.security.application-groups</key>
    <array>
        <string>group.com.music.bitchord</string>
    </array>
</dict>
</plist>
```

### 7.3 Reproducible Build Script (`scripts/build_ipados_ipa.sh`)

A single-command shell script automates the complete compilation and packaging:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== [1/4] Compiling Native C++17 DSP Analyzer (libbitchord_analysis.a) ==="
bash native/analyzer/ios_c_bridge/build_ios_static.sh

echo "=== [2/4] Linking KMP SharedUI Static Framework (iosArm64) ==="
./gradlew :sharedUi:linkReleaseFrameworkIosArm64 --no-daemon

echo "=== [3/4] Building Xcode Project for iOS Device (iosApp) ==="
cd "$ROOT_DIR/iosApp"
pod install

xcodebuild build \
  -workspace iosApp.xcworkspace \
  -scheme iosApp \
  -configuration Release \
  -sdk iphoneos \
  -destination "generic/platform=iOS" \
  -derivedDataPath "$ROOT_DIR/build/ios_derived" \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO

echo "=== [4/4] Assembling Sideloadable IPA Bundle ==="
APP_PATH="$ROOT_DIR/build/ios_derived/Build/Products/Release-iphoneos/BitChord.app"
IPA_STAGE="$ROOT_DIR/build/ipa_stage"
rm -rf "$IPA_STAGE"
mkdir -p "$IPA_STAGE/Payload"
cp -R "$APP_PATH" "$IPA_STAGE/Payload/BitChord.app"

# Apply ad-hoc pseudo-signature with entitlements for AltStore / TrollStore
ldid -S"$ROOT_DIR/iosApp/iosApp.entitlements" "$IPA_STAGE/Payload/BitChord.app/BitChord" || \
codesign --force --deep --sign - "$IPA_STAGE/Payload/BitChord.app"

cd "$IPA_STAGE"
mkdir -p "$ROOT_DIR/build"
zip -r -9 "$ROOT_DIR/build/BitChord-v1.8-beta1-iPadOS.ipa" Payload

echo "SUCCESS: Generated $ROOT_DIR/build/BitChord-v1.8-beta1-iPadOS.ipa"
```

### 7.4 Sideload Installer Compatibility Matrix

| Installer | Requirement | Compatibility Notes |
| :--- | :--- | :--- |
| **AltStore / SideStore** | Free or Paid Apple ID | Re-signs the unsigned `.ipa` with user's certificate; background refresh supported via `UIBackgroundModes` |
| **TrollStore** | CoreTrust bug (iOS $\le$ 17.0) | Installs permanently without 7-day expiration; requires entitlements embedded via `ldid -S` |
| **Sideloadly** | Mac / Windows USB | Injects provisioning profile and resigns ad-hoc |
| **TestFlight / App Store** | Apple Developer Account ($99/yr) | Use `xcodebuild -exportArchive` with Distribution Provisioning Profile |

---

## 8. Summary of Upstream Artifacts & Deliverables

| Area | Upstream Desktop / Android Asset | iPadOS Native Target | Disposition |
| :--- | :--- | :--- | :--- |
| **Audio Engine** | `DesktopPlaybackEngine.kt` (PortAudio/JavaSound) | `IOSDualAudioEngine` (`AVAudioEngine`) | 2x `AVAudioPlayerNode` + 2x `AVAudioUnitTimePitch` + 2x Transition EQ + Master 10-Band EQ |
| **Stream Loader** | `DesktopRangeStream.kt` (512KB HTTP) | `IOSChunkedResourceLoaderDelegate` | `bitchord-chunked://` virtual scheme + 512KB chunking + concurrent `CacheStore` write |
| **Media Controls** | `DesktopWindowsMedia.kt` (SMTC JNI) | `IOSNowPlayingManager` | `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` + `AVAudioSession` |
| **UI Framework** | Compose Desktop / Swing (`AppUi.kt`) | Compose Multiplatform iOS | `ComposeUIViewController` + Stage Manager + 120Hz ProMotion + Skia Shaders |
| **Neural Models** | `beat_this_int8.onnx` / `vocals_umxhq_int8.onnx` | Bundled in `iosApp/Resources/` | `onnxruntime-c:1.20.0` C-API + CoreML Execution Provider |
| **Audio Decoder** | Subprocess `ffmpeg` | `AudioToolbox.ExtAudioFile` | In-process hardware float PCM extraction |
| **Packaging** | JPackage MSI/EXE setup | `scripts/build_ipados_ipa.sh` | Unsigned `.ipa` with `ldid` entitlements |
