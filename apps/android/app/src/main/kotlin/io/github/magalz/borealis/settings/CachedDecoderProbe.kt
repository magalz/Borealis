package io.github.magalz.borealis.settings

import io.github.magalz.borealis.settings.device.DecoderLimits
import java.util.concurrent.ConcurrentHashMap

/**
 * A [DecoderProbe] that asks [delegate] once per codec for the life of the process. The codecs of a
 * device do not change while the app runs, and probing builds a codec list, so a caller on the main
 * thread pays for it only the first time.
 */
internal class CachedDecoderProbe(private val delegate: DecoderProbe) : DecoderProbe {
    private val limits = ConcurrentHashMap<String, Lazy<DecoderLimits?>>()

    override fun limits(mimeType: String): DecoderLimits? =
        limits.getOrPut(mimeType) { lazy { delegate.limits(mimeType) } }.value
}
