package com.music.bitchord.data.innertube

import kotlinx.serialization.Serializable

/**
 * Result of resolving an audio stream through the Innertube multi-client waterfall.
 * Conforms to PROJECT.md §1 contract.
 */
@Serializable
data class ResolvedStreamResult(
    val streamUrl: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long,
    val poTokenUsed: Boolean,
    val clientName: String = "ANDROID_VR",
    val headers: Map<String, String> = emptyMap(),
    val loudnessDb: Double? = null,
)

/**
 * Synchronous JavaScript evaluation contract for YouTube signature (`s`) deciphering
 * and throttling parameter (`n`) transformation.
 * Conforms to PROJECT.md §2 contract.
 */
interface JsEvaluator {
    fun evaluateScript(script: String): String
    fun decipherSignature(s: String, script: String): String
    fun transformNParam(n: String, script: String): String
}
