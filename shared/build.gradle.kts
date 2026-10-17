import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64()
    )
    iosTargets.forEach { target ->
        val targetSubdir = target.name
        val libDir = rootProject.file("native/libs/$targetSubdir").absolutePath

        target.compilations.getByName("main") {
            cinterops {
                val bitchord_analysis by creating {
                    defFile(project.file("src/nativeInterop/cinterop/bitchord_analysis.def"))
                    includeDirs(
                        rootProject.file("native/analyzer"),
                        rootProject.file("native/analyzer/ios_c_bridge")
                    )
                    extraOpts("-libraryPath", libDir)
                }
            }
        }

        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            linkerOpts(
                "-framework", "AVFoundation",
                "-framework", "AudioToolbox",
                "-framework", "JavaScriptCore",
                "-framework", "WebKit",
                "-framework", "Security",
                "-lsqlite3",
                "-lc++",
                "-L$libDir",
                "-lbitchord_analysis"
            )
        }
    }

    // Android and desktop are both JVM, but the default hierarchy gives them no source set in
    // common.
    applyDefaultHierarchyTemplate()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // Compile-only: the two applications supply the runtime artifact themselves.
                compileOnly("com.microsoft.onnxruntime:onnxruntime:1.20.0")
                // The network stack the lyrics providers and the YouTube Music
                // client run on, at the phone's versions — both applications
                // now call the same code, so they resolve the same libraries.
                api("io.ktor:ktor-client-core:3.5.2")
                api("io.ktor:ktor-client-okhttp:3.5.2")
                api("io.ktor:ktor-client-websockets:3.5.2")
                api("io.ktor:ktor-client-content-negotiation:3.5.2")
                api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                api("org.jsoup:jsoup:1.22.2")
                // YouTube stream resolution, as the phone does it: InnerTubeX's
                // client catalog and cipher tiers, NewPipe as the fallback. NewPipe
                // is compile-only because the phone ships a trimmed jar of it.
                api(project(":innertubex"))
                compileOnly("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        val iosMain by getting {
            dependencies {
                api("io.ktor:ktor-client-darwin:3.5.2")
            }
        }

        commonMain.dependencies {
            api(project(":innertubex"))
            api("io.ktor:ktor-client-core:3.5.2")
            api("io.ktor:ktor-client-websockets:3.5.2")
            api("io.ktor:ktor-client-content-negotiation:3.5.2")
            api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("com.squareup.okhttp3:mockwebserver:5.3.2")
        }
    }
}

android {
    namespace = "com.music.bitchord.shared"
    compileSdk = 36
}
