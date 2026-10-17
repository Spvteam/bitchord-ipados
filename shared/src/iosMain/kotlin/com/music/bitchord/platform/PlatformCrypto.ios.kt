package com.music.bitchord.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256

@OptIn(ExperimentalForeignApi::class)
actual object PlatformCrypto {
    actual fun sha1(bytes: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA1_DIGEST_LENGTH)
        digest.usePinned { pinnedDigest ->
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinnedInput ->
                    CC_SHA1(
                        pinnedInput.addressOf(0),
                        bytes.size.convert(),
                        pinnedDigest.addressOf(0).reinterpret()
                    )
                }
            } else {
                CC_SHA1(
                    null,
                    0u.convert(),
                    pinnedDigest.addressOf(0).reinterpret()
                )
            }
        }
        return digest
    }

    actual fun md5(bytes: ByteArray): ByteArray {
        val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
        digest.usePinned { pinnedDigest ->
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinnedInput ->
                    CC_MD5(
                        pinnedInput.addressOf(0),
                        bytes.size.convert(),
                        pinnedDigest.addressOf(0).reinterpret()
                    )
                }
            } else {
                CC_MD5(
                    null,
                    0u.convert(),
                    pinnedDigest.addressOf(0).reinterpret()
                )
            }
        }
        return digest
    }

    actual fun sha256(bytes: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        digest.usePinned { pinnedDigest ->
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinnedInput ->
                    CC_SHA256(
                        pinnedInput.addressOf(0),
                        bytes.size.convert(),
                        pinnedDigest.addressOf(0).reinterpret()
                    )
                }
            } else {
                CC_SHA256(
                    null,
                    0u.convert(),
                    pinnedDigest.addressOf(0).reinterpret()
                )
            }
        }
        return digest
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
        out.usePinned { pinnedOut ->
            val outPtr = pinnedOut.addressOf(0)
            if (key.isNotEmpty()) {
                key.usePinned { pinnedKey ->
                    val keyPtr = pinnedKey.addressOf(0)
                    if (data.isNotEmpty()) {
                        data.usePinned { pinnedData ->
                            CCHmac(
                                kCCHmacAlgSHA256,
                                keyPtr,
                                key.size.convert(),
                                pinnedData.addressOf(0),
                                data.size.convert(),
                                outPtr
                            )
                        }
                    } else {
                        CCHmac(
                            kCCHmacAlgSHA256,
                            keyPtr,
                            key.size.convert(),
                            null,
                            0u.convert(),
                            outPtr
                        )
                    }
                }
            } else {
                if (data.isNotEmpty()) {
                    data.usePinned { pinnedData ->
                        CCHmac(
                            kCCHmacAlgSHA256,
                            null,
                            0u.convert(),
                            pinnedData.addressOf(0),
                            data.size.convert(),
                            outPtr
                        )
                    }
                } else {
                    CCHmac(
                        kCCHmacAlgSHA256,
                        null,
                        0u.convert(),
                        null,
                        0u.convert(),
                        outPtr
                    )
                }
            }
        }
        return out
    }
}
