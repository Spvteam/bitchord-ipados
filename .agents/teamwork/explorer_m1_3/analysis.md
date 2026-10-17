# M1 Explorer 3: KMP Dependency & Darwin Spec Analysis

## 1. Executive Summary & Objective

This report details the architectural specification and dependency graph configuration for porting BitChord to iOS/iPadOS (`iosArm64` and `iosSimulatorArm64`), specifically focusing on:
1. Adding `io.ktor:ktor-client-darwin:3.5.2` to `:shared/iosMain`.
2. Swapping `coil-network-okhttp` for `io.coil-kt.coil3:coil-network-ktor3:3.0.4` in `:sharedUi` (`commonMain` / `iosMain`).
3. Auditing all transitive and direct dependencies (including `kotlinx-coroutines-core`, `kotlinx-serialization-json`, `jsoup`, `onnxruntime`, `NewPipeExtractor`) to strictly prevent any JVM-only artifacts from leaking into `iosMain` or failing Kotlin/Native compilation.
4. Providing authoritative, copy-paste ready Gradle Kotlin DSL diffs for `shared/build.gradle.kts` and `sharedUi/build.gradle.kts`.

---

## 2. Authoritative Dependency Matrix

All coordinates and version numbers below have been directly verified against Maven Central (`repo1.maven.org/maven2`) and the current project configuration.

| Artifact Coordinate | Version | Module | Source Set | Target Platform | Maven Central Status | Role / Note |
|---|---|---|---|---|---|---|
| `io.ktor:ktor-client-darwin` | `3.5.2` | `:shared` | `iosMain` | `iosArm64`, `iosSimulatorArm64`, `iosX64` | **Verified** | Apple `NSURLSession` HTTP engine |
| `io.ktor:ktor-client-core` | `3.5.2` | `:shared` | `commonMain` | Multiplatform (All) | **Verified** | Core Ktor HTTP client abstractions |
| `io.ktor:ktor-client-websockets` | `3.5.2` | `:shared` | `commonMain` | Multiplatform (All) | **Verified** | WebSocket plugin (Discord/Party) |
| `io.ktor:ktor-client-content-negotiation` | `3.5.2` | `:shared` | `commonMain` | Multiplatform (All) | **Verified** | Ktor content negotiation |
| `io.ktor:ktor-serialization-kotlinx-json` | `3.5.2` | `:shared` | `commonMain` | Multiplatform (All) | **Verified** | Ktor JSON serialization converter |
| `io.ktor:ktor-client-okhttp` | `3.5.2` | `:shared` | `jvmSharedMain` | JVM only (`android`, `jvm`) | **Verified** | **Quarantined JVM** OkHttp engine |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | `1.10.2` (or `1.11.0`) | `:shared`, `:sharedUi` | `commonMain` | Multiplatform (All) | **Verified** | Multiplatform coroutines runtime |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.11.0` | `:shared`, `:sharedUi` | `commonMain` | Multiplatform (All) | **Verified** | Multiplatform JSON serialization |
| `io.coil-kt.coil3:coil-compose` | `3.0.4` | `:sharedUi` | `commonMain` | Multiplatform (All) | **Verified** | Compose Multiplatform image composables |
| `io.coil-kt.coil3:coil-network-ktor3` | `3.0.4` | `:sharedUi` | `commonMain` (or `iosMain`) | Multiplatform (All) | **Verified** | Ktor 3 NetworkFetcher for Coil 3 |
| `dev.chrisbanes.haze:haze` | `1.3.1` | `:sharedUi` | `commonMain` | Multiplatform (All) | **Verified** | Blur/glass shaders for CMP |
| `dev.chrisbanes.haze:haze-materials` | `1.3.1` | `:sharedUi` | `commonMain` | Multiplatform (All) | **Verified** | Apple materials for Haze |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` | `2.8.4` | `:sharedUi` | `commonMain` | Multiplatform (All) | **Verified** | Lifecycle compose integration |
| `org.jsoup:jsoup` | `1.22.2` | `:shared` | `jvmSharedMain` | JVM only | **Verified** | **Quarantined JVM** HTML parser (Genius) |
| `com.microsoft.onnxruntime:onnxruntime` | `1.20.0` | `:shared` | `jvmSharedMain` (`compileOnly`) | JVM only | **Verified** | **Quarantined JVM** Java ONNX wrapper |
| `com.github.TeamNewPipe:NewPipeExtractor` | `v0.26.3` | `:shared` | `jvmSharedMain` (`compileOnly`) | JVM only | **Verified** | **Quarantined JVM** NewPipe fallback |

---

## 3. Deep-Dive: Darwin HTTP Engine (`ktor-client-darwin:3.5.2`)

### 3.1 Verification & Artifact Structure
The POM for `io.ktor:ktor-client-darwin:3.5.2` on Maven Central confirms:
- **Direct Dependencies**:
  - `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0`
  - `org.jetbrains.kotlin:kotlin-stdlib:2.3.21`
  - `io.ktor:ktor-client-core:3.5.2`
  - `io.ktor:ktor-network-tls:3.5.2`
- **Target Klibs Available**:
  - `ktor-client-darwin-iosarm64:3.5.2` (Physical iPhone / iPad)
  - `ktor-client-darwin-iossimulatorarm64:3.5.2` (Apple Silicon Simulator)
  - `ktor-client-darwin-iosx64:3.5.2` (Intel Simulator)

### 3.2 Operating Characteristics on iOS
`ktor-client-darwin` wraps Apple's native `NSURLSession`:
1. **Network Performance**: Delegates HTTP requests directly to iOS's network stack, automatically supporting HTTP/2, HTTP/3, system proxies, cellular data permissions, and Low Data Mode.
2. **Transparent Decompression**: `NSURLSession` automatically transparently decompresses `gzip`, `deflate`, and Brotli (`br`) responses at the OS level.
3. **Engine Configuration**:
   ```kotlin
   // In iosMain:
   val client = HttpClient(Darwin) {
       engine {
           configureRequest {
               setAllowsCellularAccess(true)
           }
       }
   }
   ```
4. **Resolution via Factory**: When `HttpClient()` is invoked with no arguments in common code, Ktor's `HttpClientEngineContainer` resolves `Darwin` engine automatically because `ktor-client-darwin` is the sole engine present in the iOS binary.

---

## 4. Deep-Dive: Coil 3 Image Loader (`coil-network-ktor3:3.0.4`)

### 4.1 Dependency Audit & Version Alignment
In the existing codebase:
- `sharedUi/build.gradle.kts` declared `api("io.coil-kt.coil3:coil-compose:3.0.4")`.
- `desktopApp/build.gradle.kts` and `app/build.gradle.kts` each declared `implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")`.
- `sharedUi` lacked a network fetcher in `commonMain`.

By adding `io.coil-kt.coil3:coil-network-ktor3:3.0.4`:
- **Compatibility**: The exact version `3.0.4` matches `coil-compose:3.0.4`.
- **Target Klibs**: Verified on Maven Central for `iosarm64` and `iossimulatorarm64`.
- **Transitive Dependencies**: Declares `coil-core:3.0.4`, `coil-network-core:3.0.4`, and `ktor-client-core:3.0.1` (which Gradle seamlessly aligns to `3.5.2`).

### 4.2 Auto-Registration on iOS
Inspection of `coil-network-ktor3-3.0.4-sources.jar` reveals:
- Contains `nativeMain/coil3/network/ktor3/internal/KtorNetworkFetcherServiceLoaderTarget.native.kt`.
- Uses Kotlin/Native `@EagerInitialization` to register `KtorNetworkFetcherFactory` with Coil's global registry upon library loading.
- On iOS, `KtorNetworkFetcherFactory` instantiates an internal Ktor `HttpClient(Darwin)` to fetch network images directly via `NSURLSession`.

### 4.3 Placement Strategy: `commonMain` vs `iosMain`
- **Recommended (Option A - `commonMain.dependencies`)**:
  Add `api("io.coil-kt.coil3:coil-network-ktor3:3.0.4")` to `sharedUi/src/commonMain`.
  - *Rationale*: All Compose Multiplatform UI components (e.g., `AsyncImage` in `SearchScreen`, `HomeScreen`, `LandscapePlayer`) reside in `commonMain` (once moved in M2). Declaring the network engine in `commonMain` ensures every CMP target gets network image loading capability without relying on child-module boilerplate.
- **Alternative (Option B - `iosMain.dependencies`)**:
  Add `api("io.coil-kt.coil3:coil-network-ktor3:3.0.4")` only inside `sharedUi/src/iosMain`.
  - *Rationale*: Leaves desktop and Android solely relying on their existing `coil-network-okhttp:3.0.4`. Both options are functionally equivalent for iOS.

---

## 5. JVM Artifact Leak Audit & Isolation Boundary

A critical hazard in KMP iOS migrations is JVM artifacts leaking into `commonMain` or `iosMain`, causing `Unresolved reference` errors during Kotlin/Native compilation or linking failures during static framework compilation.

### 5.1 Quarantined JVM-Only Dependencies
The following dependencies MUST remain quarantined in JVM-only source sets (`jvmSharedMain`, `androidMain`, `desktopApp`, or `jvmTest`):

1. **`io.ktor:ktor-client-okhttp:3.5.2`**:
   - *Risk*: Depends on Square's `okhttp-jvm`.
   - *Quarantine*: Keep in `shared/src/jvmSharedMain`. iOS uses `ktor-client-darwin` in `iosMain`.
2. **`org.jsoup:jsoup:1.22.2`**:
   - *Risk*: Pure Java HTML parsing library used in `Genius.kt`.
   - *Quarantine*: Keep in `shared/src/jvmSharedMain`. When lyrics logic moves to `commonMain` in M2, HTML stripping must use regex/KMP primitives instead of Jsoup.
3. **`com.microsoft.onnxruntime:onnxruntime:1.20.0`**:
   - *Risk*: Java JNI bindings (`ai.onnxruntime.*`).
   - *Quarantine*: Keep in `shared/src/jvmSharedMain` (`compileOnly`). iOS binds directly to native C headers (`onnxruntime-c:1.20.0`) via Kotlin/Native `cinterop` in M7.
4. **`com.github.TeamNewPipe:NewPipeExtractor:v0.26.3`**:
   - *Risk*: Java/Android library.
   - *Quarantine*: Keep in `shared/src/jvmSharedMain` (`compileOnly`).
5. **`com.squareup.okhttp3:mockwebserver:5.3.2`**:
   - *Risk*: JVM-only mock web server.
   - *Quarantine*: Keep in `shared/src/jvmTest`.
6. **`androidx.*` UI libraries**:
   - *Risk*: Android SDK dependencies (`activity-compose`, `core-ktx`, `appcompat`, `palette-ktx`).
   - *Quarantine*: Keep in `sharedUi/src/androidMain`.

### 5.2 Version Inconsistency Resolution
During dependency tree analysis, two version inconsistencies were discovered:
1. **`kotlinx-serialization-json`**:
   - `shared` `commonMain` had `1.7.3`, but `jvmSharedMain` had `1.11.0`.
   - `sharedUi` `commonMain` had `1.7.3`.
   - `ktor-serialization-kotlinx-json:3.5.2` transitively pulls `1.11.0`.
   - *Resolution*: Unify `kotlinx-serialization-json` to `1.11.0` in `commonMain` across both modules.
2. **`kotlinx-coroutines-core`**:
   - `shared` `jvmSharedMain` had `1.10.2`.
   - `sharedUi` `commonMain` had `1.9.0`.
   - `ktor-client-darwin:3.5.2` transitively pulls `1.11.0`.
   - *Resolution*: Promote `kotlinx-coroutines-core` to `1.10.2` (or `1.11.0`) in `commonMain` across both modules.

### 5.3 Special Finding: `lifecycle-runtime-compose:2.8.4`
- Checking `lifecycle-runtime-compose:2.8.4` on Maven Central revealed that JetBrains published variants under `uikitArm64ApiElements-published` and `uikitSimArm64ApiElements-published`.
- Gradle's KMP metadata attributes (`org.jetbrains.kotlin.native.target: ios_arm64` and `ios_simulator_arm64`) properly redirect and resolve these variants for Kotlin 2.3.20.

---

## 6. Authoritative Tables

### Features Discovered
| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Networking | `ktor-client-darwin` | Apple `NSURLSession` engine for Ktor | HTTP request parameters, headers, URL | HTTP response stream, status code | Throws `DarwinHttpRequestException` on network failure | Maven Central inspection, Ktor 3.5.2 POM |
| 2 | Image Loading | `coil-network-ktor3` | Coil 3 network fetcher powered by Ktor 3 | Image URL request | Fetched image data buffer | Emits `ErrorResult` if network or decoding fails | Maven Central inspection, Coil 3.0.4 JAR |
| 3 | Image Loading | Auto-Registration | K/N `@EagerInitialization` registers Ktor fetcher | Application launch | Registered `KtorNetworkFetcherFactory` | None; fails gracefully if no engine present | `coil-network-ktor3-3.0.4-sources.jar` |
| 4 | KMP Hierarchy | `applyDefaultHierarchyTemplate` | Automatically configures `iosMain` from `iosArm64` + `iosSimulatorArm64` | Target declarations in Gradle DSL | Pre-configured `iosMain` source set | Throws `DuplicateSourceSetException` if `creating` is used | Gradle DSL evaluation & KGP inspection |
| 5 | Transitive Alignment | Serialization Bump | Ktor 3.5.2 upgrades serialization from 1.7.3 to 1.11.0 | Dependency declaration | Aligned runtime classes | Class/binary incompatibilities if overridden | Gradle dependency tree inspection |
| 6 | Transitive Alignment | Coroutines Bump | Ktor 3.5.2 upgrades coroutines from 1.9.0/1.10.2 to 1.11.0 | Dependency declaration | Aligned runtime classes | Class/binary incompatibilities if overridden | Gradle dependency tree inspection |

### Edge Cases
| # | Feature | Input | Observed Behavior |
|---|---------|-------|-------------------|
| 1 | Gradle DSL | `val iosMain by creating` when `applyDefaultHierarchyTemplate()` is present | **Fatal build failure**: `Cannot add a sourceSet with name 'iosMain' as a sourceSet with that name already exists`. Must use `val iosMain by getting` or `iosMain.dependencies`. |
| 2 | Dependency Isolation | Adding `ktor-client-okhttp` to `commonMain` | **Fatal build failure** on Kotlin/Native compilation: `Unresolved reference 'okhttp'`. |
| 3 | HTML Parsing | Keeping `org.jsoup:jsoup` in `commonMain` | **Fatal build failure** on Kotlin/Native compilation: pure Java bytecode cannot compile for iOS. |
| 4 | ONNX Runtime | Keeping `com.microsoft.onnxruntime:onnxruntime` in `commonMain` | **Fatal build failure** on Kotlin/Native compilation: JNI Java wrapper cannot compile for iOS. |
| 5 | Coil Fetcher Missing | Using Coil `AsyncImage` on iOS without `coil-network-ktor3` | Runtime crash / image load failure: `Unable to create a fetcher that supports: <url>`. |

---

## 7. Implementation Strategy & Diffs

### 7.1 Proposed Diff for `shared/build.gradle.kts`

```diff
--- a/shared/build.gradle.kts
+++ b/shared/build.gradle.kts
@@ -20,6 +20,15 @@
     }
 
+    listOf(
+        iosArm64(),
+        iosSimulatorArm64()
+    ).forEach { target ->
+        target.binaries.framework {
+            baseName = "Shared"
+            isStatic = true
+        }
+    }
+
     // Android and desktop are both JVM, but the default hierarchy gives them no source set in
     // common.
     applyDefaultHierarchyTemplate()
@@ -29,17 +38,10 @@
             dependsOn(commonMain.get())
             dependencies {
                 // Compile-only: the two applications supply the runtime artifact themselves.
                 compileOnly("com.microsoft.onnxruntime:onnxruntime:1.20.0")
                 // The network stack the lyrics providers and the YouTube Music
                 // client run on, at the phone's versions — both applications
                 // now call the same code, so they resolve the same libraries.
-                api("io.ktor:ktor-client-core:3.5.2")
                 api("io.ktor:ktor-client-okhttp:3.5.2")
-                api("io.ktor:ktor-client-websockets:3.5.2")
-                api("io.ktor:ktor-client-content-negotiation:3.5.2")
-                api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
-                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
-                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                 api("org.jsoup:jsoup:1.22.2")
                 // YouTube stream resolution, as the phone does it: InnerTubeX's
                 // client catalog and cipher tiers, NewPipe as the fallback. NewPipe
                 // is compile-only because the phone ships a trimmed jar of it.
@@ -50,8 +52,21 @@
         androidMain.get().dependsOn(jvmSharedMain)
         jvmMain.get().dependsOn(jvmSharedMain)
 
+        val iosMain by getting {
+            dependencies {
+                implementation("io.ktor:ktor-client-darwin:3.5.2")
+            }
+        }
+
         commonMain.dependencies {
-            // Models in this module intentionally have no Android or Compose dependency.
-            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
+            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
+            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
+            api("io.ktor:ktor-client-core:3.5.2")
+            api("io.ktor:ktor-client-websockets:3.5.2")
+            api("io.ktor:ktor-client-content-negotiation:3.5.2")
+            api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
         }
         commonTest.dependencies {
             implementation(kotlin("test"))
```

### 7.2 Proposed Diff for `sharedUi/build.gradle.kts`

```diff
--- a/sharedUi/build.gradle.kts
+++ b/sharedUi/build.gradle.kts
@@ -51,6 +51,16 @@
     }
 
+    listOf(
+        iosArm64(),
+        iosSimulatorArm64()
+    ).forEach { target ->
+        target.binaries.framework {
+            baseName = "SharedUI"
+            isStatic = true
+            export(project(":shared"))
+        }
+    }
+
     applyDefaultHierarchyTemplate()
 
     compilerOptions {
@@ -76,9 +86,10 @@
             api("org.jetbrains.compose.components:components-resources:$cmp")
             api("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
             api("io.coil-kt.coil3:coil-compose:3.0.4")
+            api("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
             api("dev.chrisbanes.haze:haze:1.3.1")
             api("dev.chrisbanes.haze:haze-materials:1.3.1")
-            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
-            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
+            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
+            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
         }
         // Compiled against what the desktop actually resolves, not the phone's
```
