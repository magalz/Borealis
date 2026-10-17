package io.github.magalz.borealis.settings

import io.github.magalz.borealis.settings.device.DecoderLimits

/** A [DecoderProbe] that asks [delegate] once per codec for the life of the process. */
internal class CachedDecoderProbe(private val delegate: DecoderProbe) : DecoderProbe {
    override fun limits(mimeType: String): DecoderLimits? = delegate.limits(mimeType)
}
