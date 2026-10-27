package com.limelight.binding.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import com.limelight.nvstream.jni.MoonBridge
import java.util.Locale
import org.robolectric.util.ReflectionHelpers

/** The `bufferType` of picture data: what is neither a parameter set nor a start code. */
internal const val BUFFER_TYPE_PICDATA = 0

/** The `frameType` of a predicted frame; the native layer calls an IDR frame type 1. */
internal const val FRAME_TYPE_P = 0

internal const val CODEC_CONFIG = MediaCodec.BUFFER_FLAG_CODEC_CONFIG
internal const val SYNC_FRAME = MediaCodec.BUFFER_FLAG_KEY_FRAME

/** One decode unit as the native layer hands it to `submitDecodeUnit`. */
internal class DecodeUnit(
    val data: ByteArray,
    val type: Int = BUFFER_TYPE_PICDATA,
    val frameNumber: Int = 1,
    val frameType: Int = FRAME_TYPE_P,
    val times: Times = Times(),
) {
    /** The host processing latency, in tenths of a millisecond, and the two times of the unit. */
    class Times(val hostLatency: Int = 0, val receiveUs: Long = 0, val enqueueUs: Long = 0)
}

internal fun MediaCodecDecoderRenderer.submit(unit: DecodeUnit): Int =
    submitDecodeUnit(
        unit.data,
        unit.data.size,
        unit.type,
        unit.frameNumber,
        unit.frameType,
        unit.times.hostLatency.toChar(),
        unit.times.receiveUs,
        unit.times.enqueueUs,
    )

/** The bytes of [text], which is hexadecimal with or without spaces. */
internal fun bytes(text: String): ByteArray =
    text.filterNot { it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun hex(data: ByteArray): String = data.joinToString("") { "%02x".format(Locale.ROOT, it) }

/** A P frame: a start code, a slice header and some payload that the renderer never reads. */
internal fun pFrame(number: Int, times: DecodeUnit.Times = DecodeUnit.Times()) =
    DecodeUnit(bytes("00000001 419a 0011 22"), frameNumber = number, times = times)

/** The slice of an IDR frame, which follows its parameter sets. */
internal fun idrSlice(number: Int = 1, times: DecodeUnit.Times = DecodeUnit.Times()) =
    DecodeUnit(
        bytes("00000001 6588 8400 33ff"),
        frameNumber = number,
        frameType = MoonBridge.FRAME_TYPE_IDR,
        times = times,
    )

/** The PPS of an H.264 IDR frame. */
internal fun h264Pps(number: Int = 1) =
    DecodeUnit(
        bytes("00000001 68ce3c80"),
        type = MoonBridge.BUFFER_TYPE_PPS,
        frameNumber = number,
        frameType = MoonBridge.FRAME_TYPE_IDR,
    )

internal fun parameterSet(type: Int, data: String, number: Int = 1) =
    DecodeUnit(
        bytes(data),
        type = type,
        frameNumber = number,
        frameType = MoonBridge.FRAME_TYPE_IDR,
    )

/**
 * A decoder of the framework like `codec(...)` makes it, with the things that
 * `decoderCanMeetPerformancePoint` reads and that `codec(...)` leaves alone: a limit on the
 * macroblock rate (`areSizeAndRateSupported`), the ranges of sizes and blocks, performance points
 * and measured frame rates. They are set through the `formatHook` of the builder.
 */
internal class LimitedCodec(private val name: String, private val mime: String) {
    var blocksPerSecond = "1-10000000"
    var sizeRange = "64x64-4096x2304"
    var blockCount = "1-100000"

    /**
     * The performance points as the framework reads them: a size, and the frame rates it covers.
     */
    var performancePoints: Map<String, String> = emptyMap()

    /**
     * The frame rates that the codec was measured to reach, by size, as the framework reads them.
     */
    var measuredRates: Map<String, String> = emptyMap()

    /** The profiles of the decoder, or null for the one that the builder gives its mime type. */
    var profiles: List<Int>? = null

    fun build(): MediaCodecInfo =
        codec(name) {
            mime = this@LimitedCodec.mime
            profiles = this@LimitedCodec.profiles
            formatHook = {
                setString("size-range", sizeRange)
                setString("block-count-range", blockCount)
                setString("blocks-per-second-range", blocksPerSecond)
                performancePoints.forEach { (size, range) ->
                    setString("performance-point-$size-range", range)
                }
                measuredRates.forEach { (size, range) ->
                    setString("measured-frame-rate-$size-range", range)
                }
            }
        }
}

internal fun limitedCodec(
    name: String,
    mime: String,
    configure: LimitedCodec.() -> Unit = {},
): MediaCodecInfo = LimitedCodec(name, mime).apply(configure).build()

/** The `CodecException` of the framework, which only the framework can make: its parts as given. */
internal fun codecException(
    errorCode: Int,
    actionCode: Int,
    message: String = "codec failed",
): MediaCodec.CodecException =
    ReflectionHelpers.callConstructor(
        MediaCodec.CodecException::class.java,
        ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, errorCode),
        ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, actionCode),
        ReflectionHelpers.ClassParameter.from(String::class.java, message),
    )

/** What the codec's `isTransient` and `isRecoverable` are made from. */
internal const val ACTION_NONE = 0
internal const val ACTION_TRANSIENT = 1
internal const val ACTION_RECOVERABLE = 2
