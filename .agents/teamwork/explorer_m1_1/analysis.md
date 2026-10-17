# Analysis: Gradle Target Definition, Binary Framework Configuration, & Linker Options (Milestone M1)

**Agent**: M1 Explorer 1 (Gradle Targets & Binary Linker Specialist)  
**Date**: 2026-10-16  
**Target Files**: `shared/build.gradle.kts`, `sharedUi/build.gradle.kts`  
**Kotlin Multiplatform Version**: `2.3.20`  
**Compose Multiplatform Version**: `1.10.3`

---

## 1. Executive Summary

To enable Apple-native compilation on iPadOS (`iosArm64` physical iPad and `iosSimulatorArm64` Apple Silicon simulator), `:shared` and `:sharedUi` must be upgraded from their current JVM/Android-only target configuration to declare both Apple targets. Furthermore:
1. Both modules must produce static frameworks (`isStatic = true`) with base names `"Shared"` and `"SharedUI"` respectively.
2. `:sharedUi` must configure an **umbrella framework architecture**, exporting `:shared` (`export(project(":shared"))`) to avoid duplicate Kotlin/Native runtime symbol conflicts when linked into `iosApp`.
3. Six mandatory system frameworks and C libraries must be linked via `linkerOpts`: `-framework AVFoundation`, `-framework AudioToolbox`, `-framework JavaScriptCore`, `-framework WebKit`, `-framework Security`, and `-lsqlite3`.
4. Source set hierarchy wiring under Kotlin 2.3.20 must respect `applyDefaultHierarchyTemplate()`: source sets `iosMain`, `iosArm64Main`, and `iosSimulatorArm64Main` are automatically instantiated by the template, so they must be accessed via `by getting` (never `by creating`, which triggers a duplicate name exception).

---

## 2. Deep-Dive Architectural Analysis

### 2.1 Target Matrix & Static Frameworks (`isStatic = true`)

#### Current Configuration:
Both `shared/build.gradle.kts` (lines 10-20) and `sharedUi/build.gradle.kts` (lines 41-51) define only:
```kotlin
androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
```
There are **zero iOS targets**.

#### Required iOS Targets:
- `iosArm64()`: Compiles 64-bit ARM Mach-O binaries for physical iPad devices (iPadOS 16.0+, A12 Bionic through M4).
- `iosSimulatorArm64()`: Compiles 64-bit ARM Mach-O binaries for Apple Silicon Mac Xcode simulators.

#### Rationale for `isStatic = true`:
In Kotlin Multiplatform, `binaries.framework` defaults to dynamic frameworks (`isStatic = false`). For BitChord on iPadOS, **static frameworks (`isStatic = true`) are strictly mandatory** for the following architectural reasons:
1. **C++ Static Archive Linking (Milestone M3)**: The audio DSP analyzer engine in `native/analyzer/*.cpp` is compiled into a static archive `libbitchord_analysis.a` and linked via `cinterop`. Embedding static C++ archives inside a dynamic framework frequently causes Mach-O duplicate symbol issues and relocation table errors.
2. **ONNX Runtime C Integration (Milestone M7)**: Linking `onnxruntime-c` static library into a static framework allows dead-code stripping and unified symbol resolution.
3. **Sideloading & Re-Signing Compatibility**: Sideloaded iPadOS apps (AltStore, SideStore, TrollStore, Sideloadly) require all embedded `.dylib` / `.framework` binaries to be re-signed with individual provisioning entitlements. Free Apple Developer accounts impose a strict 3-App-ID limit. When `isStatic = true`, kotlinc produces static Mach-O object archives that Xcode links directly into the main `iosApp` executable, eliminating `@rpath/SharedUI.framework/SharedUI` missing-library crashes and dynamic linking overhead on device boot.

### 2.2 Framework Umbrella Architecture & Symbol Deduplication

In a multi-module KMP application with `:shared` and `:sharedUi`:
- If both modules produce static frameworks linked independently into `iosApp`, Xcode's linker (`ld64` / `clang`) will fail with hundreds of duplicate symbol errors (e.g. `_Kotlin_Int_toString`, GC runtime functions, and stdlib symbols) because both static archives bundle the Kotlin/Native runtime.
- **The Solution**: `:sharedUi` acts as the **single umbrella framework**. In `sharedUi/build.gradle.kts`:
  ```kotlin
  target.binaries.framework {
      baseName = "SharedUI"
      isStatic = true
      export(project(":shared"))
      ...
  }
  ```
  Because `sharedUi` already declares `api(project(":shared"))` in `commonMain.dependencies` (line 69 of `sharedUi/build.gradle.kts`), kotlinc merges `:shared`'s public API into `SharedUI.framework/Headers/SharedUI.h`. The Xcode project then links only `SharedUI.framework`, providing Swift with complete access to both UI and business logic through a single `import SharedUI`.

- `:shared/build.gradle.kts` still defines `baseName = "Shared"` with `isStatic = true`, allowing independent testing, CLI verification (`linkDebugFrameworkIosSimulatorArm64`), and isolated compilation.

### 2.3 Linker Options Breakdown (`linkerOpts`)

The required linker options and their exact architectural necessity across the BitChord iPadOS port:

| Linker Flag | Target Technology | BitChord Milestone & Component | Linker Error if Omitted |
|---|---|---|---|
| `-framework AVFoundation` | Apple AVFoundation | **M8**: `AVAudioEngine`, `AVAudioPlayerNode` (Deck A & B), `AVAudioUnitEQ`, `AVAudioUnitTimePitch`, `AVAssetResourceLoaderDelegate` | `Undefined symbols: _OBJC_CLASS_$_AVAudioEngine`, `_OBJC_CLASS_$_AVAudioPlayerNode` |
| `-framework AudioToolbox` | Apple AudioToolbox | **M7 / M8**: `ExtAudioFileRead`, PCM format conversion, hardware audio decoding | `Undefined symbols: _ExtAudioFileOpenURL`, `_ExtAudioFileRead` |
| `-framework JavaScriptCore` | Apple JavaScriptCore | **M4**: `JSContext` synchronous cipher deciphering (`s` signature & `n` throttling transform) | `Undefined symbols: _OBJC_CLASS_$_JSContext` |
| `-framework WebKit` | Apple WebKit | **M4 / M5**: `WKWebView` headless BotGuard `poToken` generation and OAuth login sheet | `Undefined symbols: _OBJC_CLASS_$_WKWebView` |
| `-framework Security` | Apple Security / Keychain | **M5**: iOS Keychain Services (`SecItemAdd`, `SecItemCopyMatching`) for auth session storage | `Undefined symbols: _SecItemAdd`, `_SecItemCopyMatching` |
| `-lsqlite3` | System libsqlite3 | **M6**: KMP SQLite engine (`androidx.sqlite:sqlite-bundled:2.5.0` / native sqlite3 driver) | `Undefined symbols: _sqlite3_open_v2`, `_sqlite3_prepare_v2` |

#### Recommended Extended Linker Flags (for M3, M8, M10 Parity):
In addition to the 6 mandatory flags above, the following system frameworks and libraries from `BitChord_iPadOS_Master_Porting_Plan.md` line 329 are recommended:
- `-framework Accelerate`: Required by `vDSP` for FFT taps in `VisualizerDataSource`.
- `-framework MediaPlayer`: Required by `MPNowPlayingInfoCenter` and `MPRemoteCommandCenter` for lock screen controls.
- `-framework CoreImage`: Used by image filtering and palette extraction.
- `-lc++`: Standard C++ runtime, required when linking `libbitchord_analysis.a`.

#### Linker Flag Syntax Tokenization Warning:
In Kotlin/Native DSL, `linkerOpts` passes arguments directly to the Clang linker driver without shell whitespace splitting:
```kotlin
// INCORRECT (causes "clang: error: unknown argument: '-framework AVFoundation'"):
linkerOpts("-framework AVFoundation")

// CORRECT (passes distinct argv elements):
linkerOpts("-framework", "AVFoundation", "-framework", "AudioToolbox", "-framework", "JavaScriptCore", "-framework", "WebKit", "-framework", "Security", "-lsqlite3")
```

### 2.4 Kotlin 2.3.20 Source Set Hierarchy Mechanics

Kotlin Multiplatform 2.3.20 uses `applyDefaultHierarchyTemplate()`. When `iosArm64()` and `iosSimulatorArm64()` are registered:
1. The default hierarchy template automatically creates:
   - Intermediate source sets: `iosMain`, `appleMain`, `nativeMain`.
   - Target source sets: `iosArm64Main`, `iosSimulatorArm64Main`.
2. The template automatically constructs the dependency graph:
   - `iosArm64Main -> iosMain`
   - `iosSimulatorArm64Main -> iosMain`
   - `iosMain -> appleMain -> nativeMain -> commonMain`
3. **Critical DSL Pitfall**:
   - Calling `val iosMain by creating` will **crash the Gradle configuration** with:  
     `InvalidUserDataException: Cannot add a SourceSet with name 'iosMain' as a SourceSet with that name already exists.`
   - Implementers **must** use `val iosMain by getting` (or `iosMain.dependencies { ... }`).
   - Wiring `dependsOn(commonMain.get())` on `iosMain` and `dependsOn(iosMain)` on `iosArm64Main` / `iosSimulatorArm64Main` is idempotent and safe, ensuring the explicit hierarchy required by the project specifications is strictly affirmed.

---

## 3. Exact Implementation Strategy & Code Diffs

### 3.1 Changes to `shared/build.gradle.kts`

#### Location: `shared/build.gradle.kts`
1. Declare iOS targets and configure static framework with linker options after JVM targets.
2. In `sourceSets`: retrieve `iosMain` via `by getting`, wire dependencies (`ktor-client-darwin:3.5.2`), and affirm `iosArm64Main` & `iosSimulatorArm64Main` wiring.

```diff
--- a/shared/build.gradle.kts
+++ b/shared/build.gradle.kts
@@ -20,6 +20,24 @@
         }
     }
 
+    val iosTargets = listOf(
+        iosArm64(),
+        iosSimulatorArm64()
+    )
+    iosTargets.forEach { target ->
+        target.binaries.framework {
+            baseName = "Shared"
+            isStatic = true
+            linkerOpts(
+                "-framework", "AVFoundation",
+                "-framework", "AudioToolbox",
+                "-framework", "JavaScriptCore",
+                "-framework", "WebKit",
+                "-framework", "Security",
+                "-lsqlite3"
+            )
+        }
+    }
+
     // Android and desktop are both JVM, but the default hierarchy gives them no source set in
     // common.
     applyDefaultHierarchyTemplate()
@@ -52,6 +70,20 @@
         androidMain.get().dependsOn(jvmSharedMain)
         jvmMain.get().dependsOn(jvmSharedMain)
 
+        val iosMain by getting {
+            dependsOn(commonMain.get())
+            dependencies {
+                implementation("io.ktor:ktor-client-darwin:3.5.2")
+            }
+        }
+        val iosArm64Main by getting {
+            dependsOn(iosMain)
+        }
+        val iosSimulatorArm64Main by getting {
+            dependsOn(iosMain)
+        }
+
         commonMain.dependencies {
             // Models in this module intentionally have no Android or Compose dependency.
             implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

---

### 3.2 Changes to `sharedUi/build.gradle.kts`

#### Location: `sharedUi/build.gradle.kts`
1. Declare iOS targets and configure static framework with `baseName = "SharedUI"`, `export(project(":shared"))`, and linker options.
2. In `sourceSets`: retrieve `iosMain` via `by getting`, wire `coil-network-ktor3:3.0.4`, and affirm target source sets.

```diff
--- a/sharedUi/build.gradle.kts
+++ b/sharedUi/build.gradle.kts
@@ -51,6 +51,25 @@
         }
     }
 
+    val iosTargets = listOf(
+        iosArm64(),
+        iosSimulatorArm64()
+    )
+    iosTargets.forEach { target ->
+        target.binaries.framework {
+            baseName = "SharedUI"
+            isStatic = true
+            export(project(":shared"))
+            linkerOpts(
+                "-framework", "AVFoundation",
+                "-framework", "AudioToolbox",
+                "-framework", "JavaScriptCore",
+                "-framework", "WebKit",
+                "-framework", "Security",
+                "-lsqlite3"
+            )
+        }
+    }
+
     applyDefaultHierarchyTemplate()
 
     compilerOptions {
@@ -66,6 +85,20 @@
         androidMain.get().dependsOn(jvmSharedMain)
         jvmMain.get().dependsOn(jvmSharedMain)
 
+        val iosMain by getting {
+            dependsOn(commonMain.get())
+            dependencies {
+                api("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
+            }
+        }
+        val iosArm64Main by getting {
+            dependsOn(iosMain)
+        }
+        val iosSimulatorArm64Main by getting {
+            dependsOn(iosMain)
+        }
+
         commonMain.dependencies {
             api(project(":shared"))
             api("org.jetbrains.compose.runtime:runtime:$cmp")
```

---

## 4. Extended Full Linker Options (Recommended for Milestones M1–M8 Parity)

If configuring all system frameworks required across the complete port roadmap upfront:

```kotlin
linkerOpts(
    "-framework", "AVFoundation",
    "-framework", "AudioToolbox",
    "-framework", "JavaScriptCore",
    "-framework", "WebKit",
    "-framework", "Security",
    "-framework", "Accelerate",
    "-framework", "MediaPlayer",
    "-framework", "CoreImage",
    "-lsqlite3",
    "-lc++"
)
```

Both the minimal 6-item set specified in the prompt and this extended set are fully supported by Apple's Darwin linker on iOS 16.0+.
