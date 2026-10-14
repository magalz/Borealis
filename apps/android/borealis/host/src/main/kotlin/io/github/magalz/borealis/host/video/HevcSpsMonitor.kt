package io.github.magalz.borealis.host.video

import android.media.MediaCodecInfo
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag

/**
 * Logs what the HEVC SPS of a stream says, once per change, and warns when the stream needs a
 * profile the decoder does not declare. Diagnostics only: it never throws and never touches the
 * decoder. One instance serves one stream and is called from the decoder's receive thread; an SPS
 * only arrives with IDR frames, so it costs nothing measurable per frame.
 *
 * @param requestedHdr whether the session asked for HDR.
 * @param decoderDeclaresMain10 asked only when the stream is more than 8-bit.
 * @param debugBuild enables the hex dump of the first SPS.
 */
class HevcSpsMonitor(
    private val requestedHdr: Boolean,
    private val decoderDeclaresMain10: () -> Boolean,
    private val debugBuild: Boolean,
) {
    private var seenFirst = false
    private var unreadableLogged = false
    private var last: HevcSpsInfo? = null

    fun onSps(nal: ByteArray, offset: Int = 0, length: Int = nal.size) {
        runCatching { inspect(nal, offset, length) }
    }

    private fun inspect(nal: ByteArray, offset: Int, length: Int) {
        if (!seenFirst) {
            seenFirst = true
            if (debugBuild) dump(nal, offset, length)
        }
        val info = HevcSps.parse(nal, offset, length)
        if (info == null) {
            if (!unreadableLogged) {
                unreadableLogged = true
                BorealisLog.d(LogTag.VIDEO, "HEVC SPS could not be read", "length" to length)
            }
            return
        }
        val previous = last
        if (info == previous) return
        last = info
        BorealisLog.i(
            LogTag.VIDEO,
            (if (previous == null) "HEVC SPS: " else "HEVC SPS changed: ") + info.describe(),
        )
        if (info.needsMain10) checkMain10(info)
        if (
            previous != null &&
                previous.chromaFormatIdc == CHROMA_444 &&
                info.chromaFormatIdc == CHROMA_420
        ) {
            BorealisLog.w(
                LogTag.VIDEO,
                "the host changed the stream from 4:4:4 to 4:2:0 (a 4:4:4 downgrade by the host)",
            )
        }
    }

    private fun checkMain10(info: HevcSpsInfo) {
        // A failed capability lookup is not evidence of a missing profile: stay quiet.
        if (!runCatching { decoderDeclaresMain10() }.getOrDefault(true)) {
            BorealisLog.w(
                LogTag.VIDEO,
                "the stream is ${info.bitDepthLuma}-bit but the decoder does not declare HEVC Main10: " +
                    "expect a black screen or wrong colours",
            )
        }
        if (!requestedHdr) {
            BorealisLog.i(
                LogTag.VIDEO,
                "the session did not request HDR but the HEVC stream is ${info.bitDepthLuma}-bit " +
                    "(the host may be sending 10-bit SDR)",
            )
        }
    }

    private fun dump(nal: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset.toLong() + length > nal.size) return
        val count = minOf(length, MAX_DUMP_BYTES)
        val hex = (0 until count).joinToString(" ") { "%02x".format(nal[offset + it]) }
        BorealisLog.d(LogTag.VIDEO, "first HEVC SPS hex=$hex")
    }

    companion object {
        private const val MAX_DUMP_BYTES = 128
        private const val CHROMA_420 = 1
        private const val CHROMA_444 = 3

        // android.media.MediaCodecInfo.CodecProfileLevel HEVCProfileMain10 and its HDR variants.
        private val MAIN10_PROFILES = intArrayOf(0x2, 0x1000, 0x2000)

        /** True when [profiles] (`CodecProfileLevel.profile` values) include Main 10. */
        fun declaresMain10(profiles: IntArray): Boolean = profiles.any { it in MAIN10_PROFILES }

        /** The monitor for a session that uses [decoder] (null when no HEVC decoder was found). */
        @JvmStatic
        fun forDecoder(
            decoder: MediaCodecInfo?,
            requestedHdr: Boolean,
            debugBuild: Boolean,
        ): HevcSpsMonitor =
            HevcSpsMonitor(requestedHdr, { declaresMain10(hevcProfiles(decoder)) }, debugBuild)

        private fun hevcProfiles(decoder: MediaCodecInfo?): IntArray {
            val levels = decoder?.getCapabilitiesForType("video/hevc")?.profileLevels
            return IntArray(levels?.size ?: 0) { levels!![it].profile }
        }
    }
}
