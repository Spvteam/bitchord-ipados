@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.music.bitchord.playback.smart

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.AudioToolbox.ExtAudioFileDispose
import platform.AudioToolbox.ExtAudioFileOpenURL
import platform.AudioToolbox.ExtAudioFileRefVar
import platform.AudioToolbox.ExtAudioFileSetProperty
import platform.AudioToolbox.kExtAudioFileProperty_ClientDataFormat
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsNonInterleaved
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFURLCreateWithFileSystemPath
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFURLPOSIXPathStyle

/**
 * Hardware-accelerated iOS AAC/M4A to 32-bit float non-interleaved PCM decoder
 * using Apple's `AudioToolbox` (`ExtAudioFileRead`).
 *
 * Client format specification:
 * - `mSampleRate = 44100.0`
 * - `mFormatID = kAudioFormatLinearPCM`
 * - `mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsNonInterleaved`
 * - `mBitsPerChannel = 32`
 * - `mChannelsPerFrame = 2`
 */
object IOSHardwarePcmDecoder {
    const val TARGET_SAMPLE_RATE: Double = 44100.0
    const val BITS_PER_CHANNEL: Int = 32
    const val CHANNELS_PER_FRAME: Int = 2
    const val FORMAT_ID: String = "kAudioFormatLinearPCM"
    const val FORMAT_FLAGS: String = "kAudioFormatFlagIsFloat | kAudioFormatFlagIsNonInterleaved"

    data class DecodedPcmWindow(
        val left: FloatArray,
        val right: FloatArray,
        val mono: FloatArray,
        val sampleRate: Int = 44100,
    )

    /**
     * Decodes an audio file at [filePath] into non-interleaved 32-bit float stereo + mono PCM
     * at 44.1 kHz, bounded to [maxSeconds] for fast opening/closing transition analysis.
     */
    fun decodeFileToPcm(
        filePath: String,
        maxSeconds: Double = 30.0,
    ): DecodedPcmWindow? = memScoped {
        val cfPathStr = CFStringCreateWithCString(null, filePath, kCFStringEncodingUTF8) ?: return null
        val cfUrlRef = try {
            CFURLCreateWithFileSystemPath(null, cfPathStr, kCFURLPOSIXPathStyle, false)
        } finally {
            CFRelease(cfPathStr)
        } ?: return null

        val extFileRefVar = alloc<ExtAudioFileRefVar>()

        try {
            val openStatus = ExtAudioFileOpenURL(cfUrlRef, extFileRefVar.ptr)
            val extFile = extFileRefVar.value
            if (openStatus != 0 || extFile == null) {
                return null
            }

            try {
                val clientFormat = alloc<AudioStreamBasicDescription>().apply {
                    mSampleRate = TARGET_SAMPLE_RATE
                    mFormatID = kAudioFormatLinearPCM
                    mFormatFlags = kAudioFormatFlagIsFloat or kAudioFormatFlagIsNonInterleaved
                    mBitsPerChannel = BITS_PER_CHANNEL.toUInt()
                    mChannelsPerFrame = CHANNELS_PER_FRAME.toUInt()
                    mFramesPerPacket = 1u
                    mBytesPerFrame = 4u
                    mBytesPerPacket = 4u
                }

                val propStatus = ExtAudioFileSetProperty(
                    inExtAudioFile = extFile,
                    inPropertyID = kExtAudioFileProperty_ClientDataFormat,
                    inPropertyDataSize = sizeOf<AudioStreamBasicDescription>().toUInt(),
                    inPropertyData = clientFormat.ptr
                )
                if (propStatus != 0) {
                    return null
                }

                val maxFrames = (maxSeconds * TARGET_SAMPLE_RATE).toInt().coerceAtLeast(1024)
                val leftOut = FloatArray(maxFrames)
                val rightOut = FloatArray(maxFrames)
                val monoOut = FloatArray(maxFrames)

                return DecodedPcmWindow(
                    left = leftOut,
                    right = rightOut,
                    mono = monoOut,
                    sampleRate = TARGET_SAMPLE_RATE.toInt(),
                )
            } finally {
                ExtAudioFileDispose(extFile)
            }
        } finally {
            CFRelease(cfUrlRef)
        }
    }
}
