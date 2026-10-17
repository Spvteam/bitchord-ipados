package com.music.bitchord.platform

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object PlatformCrypto {
    actual fun sha1(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-1").digest(bytes)
    }

    actual fun md5(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("MD5").digest(bytes)
    }

    actual fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
