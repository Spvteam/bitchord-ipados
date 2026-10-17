# Handoff Report: InnerTubeX Local KMP Vendoring (M1 Explorer 2)

## 1. Observation

1. **InnerTubeX in Project Tree**:
   - `D:\Bitchord\innertubex\` does not exist.
   - Command: `Get-ChildItem -Path D:\Bitchord -Filter "*innertube*"` returned only `app/src/main/java/com/music/bitchord/data/innertube` and `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube`.

2. **Current Gradle Dependencies**:
   - `shared/build.gradle.kts` line 46:
     ```kotlin
     api("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0")
     ```
   - `app/build.gradle.kts` line 354:
     ```kotlin
     implementation("com.github.MetrolistGroup.innertubex:innertubex-android:v0.7.0")
     ```
   - `settings.gradle.kts` lines 18-21:
     ```kotlin
     rootProject.name = "BitChord"
     include(":shared")
     include(":sharedUi")
     include(":desktopApp")
     ```
   - `build.gradle.kts` line 39:
     ```kotlin
     id("org.jetbrains.kotlin.multiplatform") version "2.3.20" apply false
     ```

3. **Consumers in Codebase**:
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/InnerTubeXResolver.kt` lines 4-20:
     Imports `com.metrolist.innertubex.InnerTube`, `InnerTubeLogger`, `cipher.*`, `extraction.*`, `models.YouTubeLocale`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/potoken/PoTokenGenerator.kt` lines 3-5:
     Imports `com.metrolist.innertubex.extraction.TokenProvider`, `TokenProviderCapabilities`, `strategy.PoTokenProviderKind`.

4. **Upstream Published Artifacts & Compiler Version**:
   - `C:\Users\supha\.gradle\caches\modules-2\files-2.1\com.github.MetrolistGroup.innertubex\innertubex\v0.7.0\7125b90090b42cc3a2769761c42af6375e65574c\innertubex-v0.7.0.module`:
     Lines 43-48 indicate upstream was compiled with Kotlin `2.4.10`:
     ```json
     {
       "group" : "org.jetbrains.kotlin",
       "module" : "kotlin-stdlib",
       "version" : { "requires" : "2.4.10" }
     }
     ```
   - BitChord's build uses Kotlin `2.3.20`.

5. **Upstream Source Jars & Java-Free Purity**:
   - All 5 source and resource jars were resolved and downloaded into the local Gradle cache:
     - `innertubex-v0.7.0-sources.jar` (`commonMain` + `nativeMain`)
     - `innertubex-desktop-v0.7.0-sources.jar` (`desktopMain`)
     - `innertubex-desktop-v0.7.0.jar` (contains `yt_ejs/yt.solver.core.min.js`, `yt.solver.lib.min.js`)
     - `innertubex-android-v0.7.0-sources.jar` (`androidMain`)
     - `innertubex-iosarm64-v0.7.0-sources.jar` (`nativeMain`)
   - Automated scan across all 48 `commonMain` files for `import java.` and `import javax.` returned **0 matches**.
   - `nativeMain/com/metrolist/innertubex/utils/Sha1.native.kt`:
     Uses `platform.CoreCrypto.CC_SHA1` (pure Apple C-interop).
   - `nativeMain/com/metrolist/innertubex/models/DefaultYouTubeLocale.native.kt`:
     Uses `platform.Foundation.NSLocale` (pure Apple Foundation).
   - `nativeMain/com/metrolist/innertubex/cipher/YtEjsScriptLoader.native.kt`:
     Embeds base64 encoded strings in Kotlin code (`kotlin.io.encoding.Base64`).

---

## 2. Logic Chain

1. **Premise**: BitChord is porting `:shared` and `:sharedUi` to iPadOS / iOS (`iosArm64`, `iosSimulatorArm64`). (Observation 1, 2)
2. **Issue**: Upstream InnerTubeX `v0.7.0` on JitPack was compiled with Kotlin `2.4.10` (Observation 4). BitChord uses Kotlin `2.3.20` (Observation 2). Kotlin/Native klib metadata cannot be consumed across differing Kotlin compiler releases without failing compilation (`Incompatible klib version`).
3. **Solution**: Vendoring InnerTubeX as a project module (`:innertubex`) in `D:\Bitchord\innertubex` ensures it is compiled by BitChord's own root Kotlin `2.3.20` plugin, aligning compiler versions, ABI, and stdlib.
4. **Viability**: Upstream `v0.7.0` source code is already 100% pure KMP with zero `java.*` or `javax.*` imports in `commonMain`, with ready-made Apple actuals in `nativeMain` using `platform.CoreCrypto` and `platform.Foundation` (Observation 5).
5. **Integration**:
   - Adding `include(":innertubex")` in `settings.gradle.kts` brings it into the project graph.
   - Configuring `innertubex/build.gradle.kts` with `androidTarget`, `jvm`, `iosArm64`, and `iosSimulatorArm64` provides matching targets.
   - Replacing `api("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0")` with `api(project(":innertubex"))` in `shared/build.gradle.kts` switches the dependency graph from external JitPack to the local module.

---

## 3. Caveats

1. Android compilation is guarded in `settings.gradle.kts` by `local.properties` / `ANDROID_HOME`. In the current environment, no Android SDK is configured (`No Android SDK found — building the desktop targets only`). However, `innertubex/build.gradle.kts` includes `androidTarget()` and `com.android.library` with `compileSdk = 36` to ensure full parity whenever the Android SDK is present.
2. In Milestone M4, YouTube stream cipher deciphering on iOS will introduce a native `JSContext` (`JavaScriptCore`) solver alongside `QuickJsEngine`. The vendored `:innertubex` module provides the foundational extractor contracts and catalog models needed for that step.

---

## 4. Conclusion

Vendoring `:innertubex` in `D:\Bitchord\innertubex` is completely specified, fully feasible, and immediately executable.

The implementation steps for the coding agent are:
1. Create `D:\Bitchord\innertubex\build.gradle.kts` with `androidTarget`, `jvm`, `iosArm64()`, `iosSimulatorArm64()`.
2. Extract the 59 source and resource files from the verified local Gradle cache jars using the provided PowerShell extraction script in `analysis.md`.
3. Add `include(":innertubex")` to `settings.gradle.kts`.
4. Substitute `api(project(":innertubex"))` in `shared/build.gradle.kts` (and `commonMain.dependencies`).
5. Substitute `implementation(project(":innertubex"))` in `app/build.gradle.kts`.

---

## 5. Verification Method

To independently verify the implementation:
1. **Gradle Build Graph & Tasks**:
   ```pwsh
   .\gradlew.bat :innertubex:tasks
   ```
   *Expected*: Succeeds and lists KMP tasks including `compileKotlinJvm`, `compileKotlinIosArm64`, etc.
2. **Compile Module**:
   ```pwsh
   .\gradlew.bat :innertubex:compileKotlinJvm
   ```
   *Expected*: `BUILD SUCCESSFUL`.
3. **Compile Dependent Module (`:shared`)**:
   ```pwsh
   .\gradlew.bat :shared:compileKotlinJvm
   ```
   *Expected*: `BUILD SUCCESSFUL` using the local `:innertubex` module instead of JitPack.
4. **Desktop App Linking**:
   ```pwsh
   .\gradlew.bat :desktopApp:classes
   ```
   *Expected*: `BUILD SUCCESSFUL`.
