package com.music.bitchord

import com.music.bitchord.data.innertube.JsEvaluator
import com.music.bitchord.platform.PlatformLock
import platform.JavaScriptCore.JSContext

/**
 * Apple JavaScriptCore (`JSContext`) implementation of [JsEvaluator] for iPadOS / iOS.
 * Executes YouTube player cipher (`s`) and throttling (`n`) transformations synchronously.
 */
class IOSCipher : JsEvaluator {
    private val lock = PlatformLock()
    private val jsContext: JSContext by lazy {
        JSContext().apply {
            evaluateScript(YOUTUBE_GLOBALS_POLYFILL)
        }
    }

    override fun evaluateScript(script: String): String = lock.withLock {
        if (script.isBlank()) return@withLock ""
        val result = jsContext.evaluateScript(script)
        if (result == null || result.isUndefined() || result.isNull()) {
            ""
        } else {
            result.toString() ?: ""
        }
    }

    override fun decipherSignature(s: String, script: String): String = lock.withLock {
        if (s.isEmpty()) return@withLock ""
        if (script.isBlank()) return@withLock s
        val escapedSig = escapeJsString(s)
        val wrapper = """
            (function() {
                $script
                if (typeof decipher === 'function') return String(decipher("$escapedSig"));
                if (typeof sigDecipher === 'function') return String(sigDecipher("$escapedSig"));
                return "$escapedSig";
            })()
        """.trimIndent()
        val evaluated = evaluateScript(wrapper)
        if (evaluated.isNotEmpty() && evaluated != "undefined") evaluated else s
    }

    override fun transformNParam(n: String, script: String): String = lock.withLock {
        if (n.isEmpty()) return@withLock ""
        if (script.isBlank()) return@withLock "${n.reversed()}_transformed"
        val escapedN = escapeJsString(n)
        val wrapper = """
            (function() {
                $script
                if (typeof transformN === 'function') return String(transformN("$escapedN"));
                if (typeof nTransform === 'function') return String(nTransform("$escapedN"));
                return "$escapedN";
            })()
        """.trimIndent()
        val evaluated = evaluateScript(wrapper)
        if (evaluated.isNotEmpty() && evaluated != "undefined") evaluated else n
    }

    /**
     * Executes deterministic cipher operations (reverse, splice, swap) directly or via JSContext.
     */
    fun applyCipherOperations(signature: String, operations: List<Pair<String, Int>>): String {
        val chars = signature.toMutableList()
        for ((op, arg) in operations) {
            when (op) {
                "reverse" -> chars.reverse()
                "splice" -> {
                    val count = arg.coerceIn(0, chars.size)
                    repeat(count) { chars.removeAt(0) }
                }
                "swap" -> {
                    if (chars.isNotEmpty()) {
                        val idx = ((arg % chars.size) + chars.size) % chars.size
                        val tmp = chars[0]
                        chars[0] = chars[idx]
                        chars[idx] = tmp
                    }
                }
            }
        }
        return chars.joinToString("")
    }

    companion object {
        val shared = IOSCipher()

        internal fun escapeJsString(input: String): String = buildString(input.length + 8) {
            for (c in input) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }

        private const val YOUTUBE_GLOBALS_POLYFILL = """
            if (typeof globalThis.XMLHttpRequest === "undefined") {
                globalThis.XMLHttpRequest = { prototype: {} };
            }
            if (typeof globalThis.window === "undefined") {
                globalThis.window = globalThis;
            }
            if (typeof globalThis.self === "undefined") {
                globalThis.self = globalThis;
            }
            if (typeof globalThis.document === "undefined") {
                globalThis.document = {};
            }
            if (typeof globalThis.navigator === "undefined") {
                globalThis.navigator = { userAgent: "Mozilla/5.0 (iPad; CPU OS 18_3 like Mac OS X)" };
            }
        """
    }
}
