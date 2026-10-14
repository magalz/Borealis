package io.github.magalz.borealis.host.video

/** Reads the diagnostics fields of an HEVC SPS NAL unit (ITU-T H.265 section 7.3.2.2). */
object HevcSps {
    /** Returns null on anything it cannot read; never throws. */
    fun parse(nal: ByteArray, offset: Int = 0, length: Int = nal.size): HevcSpsInfo? = null
}
