package io.github.magalz.borealis.settings

import android.content.Context
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.preferences.GlPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.settings.device.DecoderLimits
import javax.inject.Inject

/** The [DecoderProbe] of the app: the decoder that [MediaCodecHelper] would pick for a stream. */
internal class MediaCodecDecoderProbe
@Inject
constructor(@ApplicationContext private val context: Context) : DecoderProbe {
    override fun limits(mimeType: String): DecoderLimits? {
        // Must run before the decoder lists are read; it does nothing the second time.
        MediaCodecHelper.initialize(context, GlPreferences.readPreferences(context).glRenderer)
        val decoder = MediaCodecHelper.findProbableSafeDecoder(mimeType, -1) ?: return null
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
