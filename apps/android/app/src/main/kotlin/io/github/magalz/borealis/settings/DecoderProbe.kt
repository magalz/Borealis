package io.github.magalz.borealis.settings

import io.github.magalz.borealis.settings.device.DecoderLimits

/** Finds the decoder that the stream would use for a codec and what it can decode. */
internal fun interface DecoderProbe {
    /** The limits of the probable safe decoder of [mimeType], or `null` when none was found. */
    fun limits(mimeType: String): DecoderLimits?

    companion object {
        const val AVC = "video/avc"
        const val HEVC = "video/hevc"
    }
}
