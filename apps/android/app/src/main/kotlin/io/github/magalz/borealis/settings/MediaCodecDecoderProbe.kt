package io.github.magalz.borealis.settings

import android.content.Context
import android.media.MediaCodecInfo
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.preferences.GlPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.settings.device.DecoderLimits
import javax.inject.Inject

/** Finds the decoder that the stream would use for a codec. */
internal fun interface CodecFinder {
    /** The probable safe decoder of [mimeType], or `null` when the device has none. */
    fun find(mimeType: String): MediaCodecInfo?
}

/** The [CodecFinder] of the app: the choice of [MediaCodecHelper]. */
private class MediaCodecHelperFinder(private val context: Context) : CodecFinder {
    override fun find(mimeType: String): MediaCodecInfo? {
        // Must run before the decoder lists are read; it does nothing the second time.
        MediaCodecHelper.initialize(context, GlPreferences.readPreferences(context).glRenderer)
        return MediaCodecHelper.findProbableSafeDecoder(mimeType, -1)
    }
}

/**
 * The [DecoderProbe] of the app: what the decoder that [MediaCodecHelper] would pick for a stream
 * reports. It builds the codec list on every call; [CachedDecoderProbe] keeps the answers.
 */
internal class MediaCodecDecoderProbe(private val finder: CodecFinder) : DecoderProbe {
    @Inject
    constructor(@ApplicationContext context: Context) : this(MediaCodecHelperFinder(context))

    override fun limits(mimeType: String): DecoderLimits? {
        val decoder = finder.find(mimeType) ?: return null
        return try {
            val capabilities =
                decoder.getCapabilitiesForType(mimeType).videoCapabilities ?: return null
            val widths = capabilities.supportedWidths
            // The inherited check ignores a decoder whose width range does not include 1280.
            if (widths.lower > MIN_WIDTH) {
                null
            } else {
                DecoderLimits(widths.upper, capabilities.supportedHeights.upper)
            }
        } catch (e: IllegalArgumentException) {
            // Some bad decoders throw from getCapabilitiesForType.
            BorealisLog.w(
                LogTag.VIDEO,
                "Decoder capabilities unreadable",
                "mime" to mimeType,
                "error" to e.message,
            )
            null
        }
    }

    private companion object {
        const val MIN_WIDTH = 1280
    }
}
