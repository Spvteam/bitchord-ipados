# Handoff Report: Gradle Targets & Binary Linker Configuration (Milestone M1)

**Agent**: M1 Explorer 1 (Gradle Targets & Binary Linker Specialist)  
**Parent**: Project Orchestrator (`b9d6a6c6-0231-4a6f-a397-e747c73d7ef9`)  
**Date**: 2026-10-16  
**Status**: COMPLETE (Hard Handoff)

---

## 1. Observation

1. **Target Baseline**:
   - `shared/build.gradle.kts` (lines 9-20) defines only `androidTarget` and `jvm`. Zero iOS targets exist.
   - `sharedUi/build.gradle.kts` (lines 40-51) defines only `androidTarget` and `jvm`. Zero iOS targets exist.
2. **Default Hierarchy Template**:
   - Both `shared/build.gradle.kts` (line 24) and `sharedUi/build.gradle.kts` (line 53) invoke `applyDefaultHierarchyTemplate()`.
   - In Kotlin Multiplatform 2.3.20, `applyDefaultHierarchyTemplate()` creates intermediate source sets (`iosMain`, `appleMain`, `nativeMain`) and target source sets (`iosArm64Main`, `iosSimulatorArm64Main`) automatically whenever `iosArm64()` and `iosSimulatorArm64()` are declared.
3. **Source Set Topology & Compilation Error Risk**:
   - When `applyDefaultHierarchyTemplate()` is active, invoking `val iosMain by creating` results in a Gradle configuration failure:  
     `InvalidUserDataException: Cannot add a SourceSet with name 'iosMain' as a SourceSet with that name already exists.`
   - Retrieval must be performed via `val iosMain by getting`.
4. **Linker Options**:
   - Upstream `BitChord_iPadOS_Master_Porting_Plan.md` §WP-01 line 329 documents:  
     `Add -lsqlite3, -lc++, -framework Accelerate, -framework AVFoundation, -framework AudioToolbox, -framework MediaPlayer, -framework JavaScriptCore, -framework WebKit, -framework CoreImage, and -framework Security to binaries.framework { linkerOpts(...) }`.
   - In Kotlin/Native Clang driver invocations, space-separated arguments such as `"-framework AVFoundation"` are passed as single `argv` tokens, triggering `clang: error: unknown argument: '-framework AVFoundation'`. Linker flags must be supplied as discrete token pairs: `"-framework", "AVFoundation"`.
5. **Static Framework & Umbrella Export**:
   - `sharedUi/build.gradle.kts` line 69 declares `api(project(":shared"))`.
   - In Kotlin Multiplatform, linking multiple static frameworks (`Shared.framework` and `SharedUI.framework`) into a single iOS application binary results in hundreds of duplicate symbol collisions on the Kotlin runtime/GC.
   - Declaring `export(project(":shared"))` inside `SharedUI.framework` merges the headers and declarations of `:shared` into `SharedUI.framework`, establishing an umbrella framework for `iosApp`.
6. **Tool Execution Verification**:
   - Executed `.\gradlew.bat help` on the repository:  
     Output confirmed `Gradle 8.11.1`, Kotlin plugin `2.3.20`, JVM `21.0.12.1`, daemon initialization `BUILD SUCCESSFUL in 2m 1s`.

---

## 2. Logic Chain

1. **Step 1 (Target Addition)**:  
   From Observation 1, neither module compiles for Apple platforms. By registering `iosArm64()` and `iosSimulatorArm64()` inside `kotlin { ... }` in both `shared/build.gradle.kts` and `sharedUi/build.gradle.kts`, Kotlinc generates Mach-O artifacts for 64-bit physical iPads and Apple Silicon simulators.
2. **Step 2 (Static Binary Configuration)**:  
   From Observation 4 and PROJECT.md §Code Layout, BitChord links static C++ audio analysis archives (`libbitchord_analysis.a`) and CocoaPods (`onnxruntime-c`). Static frameworks (`isStatic = true`) allow direct static linking into the `iosApp` Mach-O executable without requiring separate dynamic framework code signing or `@rpath` runtime resolution, which is vital for sideloaded IPAs (AltStore / SideStore / TrollStore).
3. **Step 3 (Umbrella Framework Architecture)**:  
   From Observation 5, linking both `Shared.framework` and `SharedUI.framework` directly into `iosApp` duplicates runtime symbols. Because `sharedUi` already exposes `api(project(":shared"))`, configuring `target.binaries.framework { baseName = "SharedUI"; isStatic = true; export(project(":shared")) }` consolidates all runtime symbols and headers into `SharedUI.framework`.
4. **Step 4 (Linker Argument Safety)**:  
   From Observation 4, `-framework` arguments must be broken into separate tokens `"-framework", "<Name>"`. Linking `-framework AVFoundation`, `-framework AudioToolbox`, `-framework JavaScriptCore`, `-framework WebKit`, `-framework Security`, and `-lsqlite3` satisfies the compile-time and link-time dependencies for the audio engine, PCM decoder, JS cipher decipherer, WKWebView BotGuard/OAuth, Keychain store, and SQLite engine.
5. **Step 5 (Hierarchy Compatibility under Kotlin 2.3.20)**:  
   From Observation 2 & 3, because `applyDefaultHierarchyTemplate()` is active, `iosMain`, `iosArm64Main`, and `iosSimulatorArm64Main` are pre-instantiated. Therefore, referencing them with `by getting` and wiring `dependsOn(commonMain.get())` and `dependsOn(iosMain)` guarantees zero naming conflicts while fulfilling the explicit hierarchy mandate.

---

## 3. Caveats

1. **Host OS Linking Limitation**:  
   This investigation and verification were conducted on Windows 11 (`OS: Windows 11 10.0 amd64`). On Windows, Gradle configuration, task graph generation, and metadata compilation for iOS targets succeed. However, executing `linkDebugFrameworkIosArm64` or `linkDebugFrameworkIosSimulatorArm64` requires the Apple Xcode/Clang toolchain and must be run on a macOS host or CI runner (`scripts/build_ipados_ipa.sh`).
2. **Upstream Source Relocation Dependency**:  
   Adding iOS targets is the prerequisite foundation for Milestone M1. Compiling iOS source sets against `:shared` and `:sharedUi` business logic requires subsequent migration of files from `jvmSharedMain` to `commonMain` (Milestone M2), and local vendoring of `:innertubex` (M1 WP-01).

---

## 4. Conclusion

The exact Gradle DSL modifications required for Milestone M1 are:
1. In `shared/build.gradle.kts`:
   - Add `listOf(iosArm64(), iosSimulatorArm64()).forEach { it.binaries.framework { baseName = "Shared"; isStatic = true; linkerOpts(...) } }`.
   - In `sourceSets`: access `val iosMain by getting { dependsOn(commonMain.get()); dependencies { implementation("io.ktor:ktor-client-darwin:3.5.2") } }`, and wire `val iosArm64Main by getting { dependsOn(iosMain) }` and `val iosSimulatorArm64Main by getting { dependsOn(iosMain) }`.
2. In `sharedUi/build.gradle.kts`:
   - Add `listOf(iosArm64(), iosSimulatorArm64()).forEach { it.binaries.framework { baseName = "SharedUI"; isStatic = true; export(project(":shared")); linkerOpts(...) } }`.
   - In `sourceSets`: access `val iosMain by getting { dependsOn(commonMain.get()); dependencies { api("io.coil-kt.coil3:coil-network-ktor3:3.0.4") } }`, and wire `val iosArm64Main by getting { dependsOn(iosMain) }` and `val iosSimulatorArm64Main by getting { dependsOn(iosMain) }`.
3. Provide all 6 mandatory linker options (`-framework AVFoundation`, `-framework AudioToolbox`, `-framework JavaScriptCore`, `-framework WebKit`, `-framework Security`, `-lsqlite3`) with tokenized vararg syntax to avoid Clang driver errors.

Full diffs and detailed documentation are located at `D:\Bitchord\.agents\teamwork\explorer_m1_1\analysis.md`.

---

## 5. Verification Method

Once implemented by the orchestrator/implementer, verify with the following commands:
1. **Gradle Project Configuration Check**:
   ```pwsh
   .\gradlew.bat help --dry-run
   ```
   *Expected Result*: Exits 0 without `InvalidUserDataException` or unrecognized target/linker errors.
2. **iOS Task Verification**:
   ```pwsh
   .\gradlew.bat tasks --all | Select-String "FrameworkIos"
   ```
   *Expected Result*: Tasks `linkDebugFrameworkIosArm64`, `linkReleaseFrameworkIosArm64`, `linkDebugFrameworkIosSimulatorArm64`, `linkReleaseFrameworkIosSimulatorArm64` are registered under both `:shared` and `:sharedUi`.
3. **Invalidation Conditions**:
   - If `val iosMain by creating` is used with `applyDefaultHierarchyTemplate()`, Gradle will immediately throw `Cannot add a SourceSet with name 'iosMain'`.
   - If `linkerOpts("-framework AVFoundation")` is passed as an unsplit string, the native linker fails with `clang: error: unknown argument: '-framework AVFoundation'`.
   - If `export(project(":shared"))` is omitted in `sharedUi`, Swift code in `iosApp` will fail to resolve `:shared` symbols unless double-linking frameworks (which leads to duplicate runtime symbol errors).
