allprojects {
    tasks.register("fetchAllSources") {
        doLast {
            val modules = listOf("innertubex-desktop", "innertubex-android", "innertubex-iosarm64", "innertubex-iossimulatorarm64")
            for (mod in modules) {
                try {
                    val query = dependencies.createArtifactResolutionQuery()
                        .forModule("com.github.MetrolistGroup.innertubex", mod, "v0.7.0")
                        .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
                        .execute()
                    for (component in query.resolvedComponents) {
                        println("Component: " + component.id)
                        for (artifact in component.getArtifacts(SourcesArtifact::class.java)) {
                            if (artifact is org.gradle.api.artifacts.result.ResolvedArtifactResult) {
                                println("Source artifact for $mod: " + artifact.file.absolutePath)
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Failed for $mod: ${e.message}")
                }
            }
        }
    }
}
