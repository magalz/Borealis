package io.github.magalz.borealis.host.video

import java.io.ByteArrayOutputStream

/**
 * The fields of an SPS that a test sets; the rest is fixed. The defaults are a Main profile SPS of
 * 1920 x 1080 with 8-bit 4:2:0 samples, as an encoder writes it, with the escape bytes in.
 */
data class SpsFields(
    val profileIdc: Int = 1,
    val compatibilityFlags: Int = 1 shl 1,
    val maxSubLayersMinus1: Int = 0,
    val subLayerProfilePresent: List<Boolean> = emptyList(),
    val subLayerLevelPresent: List<Boolean> = emptyList(),
    val chromaFormatIdc: Int = 1,
    val separateColourPlane: Boolean = false,
    val conformanceWindow: Boolean = false,
    val bitDepthLuma: Int = 8,
    val bitDepthChroma: Int = 8,
    val nalType: Int = 33,
    val layerId: Int = 0,
    val threeByteStartCode: Boolean = false,
    val extraFields: Int = 1,
    val escaped: Boolean = true,
)

/** Writes an HEVC SPS the way an encoder does, so tests can reach syntax the fixtures lack. */
object HevcSpsBuilder {
    val START_CODE_4 = byteArrayOf(0, 0, 0, 1)
    val START_CODE_3 = byteArrayOf(0, 0, 1)

    private class BitWriter {
        private val bytes = ByteArrayOutputStream()
        private var current = 0
        private var used = 0

        fun bit(value: Boolean) = bits(if (value) 1L else 0L, 1)

        fun bits(value: Long, count: Int) {
            for (shift in count - 1 downTo 0) {
                current = (current shl 1) or ((value shr shift) and 1L).toInt()
                if (++used == 8) {
                    bytes.write(current)
                    current = 0
                    used = 0
                }
            }
        }

        fun bits(value: Int, count: Int) = bits(value.toLong(), count)

        fun ue(value: Int) {
            val coded = value + 1L
            val width = 64 - java.lang.Long.numberOfLeadingZeros(coded)
            bits(0L, width - 1)
            bits(coded, width)
        }

        fun rbspTrailingBits(): ByteArray {
            bit(true)
            while (used != 0) bit(false)
            return bytes.toByteArray()
        }
    }

    /** Inserts the emulation-prevention byte after every `00 00` that is followed by 00..03. */
    fun escape(rbsp: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var zeros = 0
        for (b in rbsp) {
            val value = b.toInt() and 0xFF
            if (zeros >= 2 && value <= 3) {
                out.write(3)
                zeros = 0
            }
            out.write(value)
            zeros = if (value == 0) zeros + 1 else 0
        }
        return out.toByteArray()
    }

    private fun BitWriter.profileFields(profileIdc: Int, compatibilityFlags: Int) {
        bits(0, 2) // profile_space
        bit(false) // tier_flag
        bits(profileIdc, 5)
        for (j in 0 until 32) bit((compatibilityFlags shr j) and 1 == 1)
        bits(0b1001, 4) // progressive, not interlaced, not non-packed, frame-only
        bits(0L, 43)
        bit(false)
    }

    fun sps(fields: SpsFields = SpsFields()): ByteArray =
        with(fields) {
            val w = BitWriter()
            w.bits(0, 4) // sps_video_parameter_set_id
            w.bits(maxSubLayersMinus1, 3)
            w.bit(true) // sps_temporal_id_nesting_flag
            // profile_tier_level(1, maxSubLayersMinus1)
            w.profileFields(profileIdc, compatibilityFlags)
            w.bits(93, 8) // general_level_idc
            for (i in 0 until maxSubLayersMinus1) {
                w.bit(subLayerProfilePresent.getOrElse(i) { false })
                w.bit(subLayerLevelPresent.getOrElse(i) { false })
            }
            if (maxSubLayersMinus1 > 0) {
                repeat(8 - maxSubLayersMinus1) { w.bits(0, 2) } // reserved_zero_2bits
            }
            for (i in 0 until maxSubLayersMinus1) {
                if (subLayerProfilePresent.getOrElse(i) { false }) w.profileFields(profileIdc, -1)
                if (subLayerLevelPresent.getOrElse(i) { false }) w.bits(90, 8)
            }
            w.ue(0) // sps_seq_parameter_set_id
            w.ue(chromaFormatIdc)
            if (chromaFormatIdc == 3) w.bit(separateColourPlane)
            w.ue(1920)
            w.ue(1080)
            w.bit(conformanceWindow)
            if (conformanceWindow) repeat(4) { w.ue(it + 1) }
            w.ue(bitDepthLuma - 8)
            w.ue(bitDepthChroma - 8)
            // Later syntax (log2_max_pic_order_cnt_lsb_minus4 and so on) the parser must not need.
            repeat(extraFields) { w.ue(4) }
            val rbsp = w.rbspTrailingBits()
            val header =
                byteArrayOf(
                    ((nalType shl 1) or (layerId shr 5)).toByte(),
                    (((layerId and 31) shl 3) or 1).toByte(),
                )
            val startCode = if (threeByteStartCode) START_CODE_3 else START_CODE_4
            startCode + header + if (escaped) escape(rbsp) else rbsp
        }
}
