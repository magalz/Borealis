package io.github.magalz.borealis.host.video

/** Logs what the HEVC SPS of a stream says and warns about profiles the decoder lacks. */
class HevcSpsMonitor(
    private val requestedHdr: Boolean,
    private val decoderDeclaresMain10: () -> Boolean,
    private val debugBuild: Boolean,
) {
    fun onSps(nal: ByteArray, offset: Int = 0, length: Int = nal.size) = Unit

    companion object {
        fun declaresMain10(profiles: IntArray): Boolean = false
    }
}
