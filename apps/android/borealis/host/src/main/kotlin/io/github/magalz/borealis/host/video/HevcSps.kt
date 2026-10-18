@file:Suppress("MagicNumber") // bit widths and flag positions of the H.265 syntax

package io.github.magalz.borealis.host.video

/**
 * Reads the diagnostics fields of an HEVC SPS NAL unit (ITU-T H.265 sections 7.3.2.2 and 7.3.3).
 * Only the fields before `bit_depth_chroma_minus8` are read, so a bounded prefix of the NAL is
 * enough.
 */
object HevcSps {
    private const val NAL_TYPE_SPS = 33

    /** Raw bytes read after the NAL header: far more than the fields need, and a hard bound. */
    private const val MAX_SCANNED_BYTES = 256
    private const val NAL_HEADER_SIZE = 2
    private const val MAX_SUB_LAYERS_MINUS1 = 6
    private const val MAX_EXP_GOLOMB_ZEROS = 31
    private const val MAX_CHROMA_FORMAT_IDC = 3
    private const val MAX_BIT_DEPTH_MINUS8 = 8

    /** Returns null on anything it cannot read; never throws. */
    fun parse(nal: ByteArray, offset: Int = 0, length: Int = nal.size): HevcSpsInfo? {
        if (offset < 0 || length < 0 || offset.toLong() + length > nal.size) return null
        return parseNal(nal, offset, offset + length)
    }

    private fun parseNal(nal: ByteArray, from: Int, end: Int): HevcSpsInfo? {
        val start = from + startCodeLength(nal, from, end - from)
        if (
            end - start <= NAL_HEADER_SIZE ||
                !isSpsHeader(nal[start].toInt() and 0xFF, nal[start + 1].toInt() and 0xFF)
        ) {
            return null
        }
        val bits = BitReader(unescape(nal, start + NAL_HEADER_SIZE, end))
        val info = readFields(bits)
        return if (bits.overrun) null else info
    }

    /** forbidden_zero_bit 0, nal_unit_type 33 and nuh_layer_id 0 (the base layer). */
    private fun isSpsHeader(first: Int, second: Int): Boolean =
        (first and 0x80) == 0 &&
            (first shr 1) == NAL_TYPE_SPS &&
            (first and 1) == 0 &&
            (second shr 3) == 0

    private fun startCodeLength(nal: ByteArray, offset: Int, length: Int): Int =
        when {
            length >= 4 &&
                nal[offset].toInt() == 0 &&
                nal[offset + 1].toInt() == 0 &&
                nal[offset + 2].toInt() == 0 &&
                nal[offset + 3].toInt() == 1 -> 4
            length >= 3 &&
                nal[offset].toInt() == 0 &&
                nal[offset + 1].toInt() == 0 &&
                nal[offset + 2].toInt() == 1 -> 3
            else -> 0
        }

    /** Drops the emulation-prevention byte of every `00 00 03`, scanning a bounded prefix. */
    private fun unescape(nal: ByteArray, from: Int, end: Int): ByteArray {
        val out = ByteArray(minOf(end - from, MAX_SCANNED_BYTES))
        var size = 0
        var zeros = 0
        var index = from
        val limit = from + out.size
        while (index < limit) {
            val value = nal[index++].toInt() and 0xFF
            if (zeros >= 2 && value == 3) {
                zeros = 0
                continue
            }
            out[size++] = value.toByte()
            zeros = if (value == 0) zeros + 1 else 0
        }
        return out.copyOf(size)
    }

    private fun readFields(bits: BitReader): HevcSpsInfo? {
        bits.skip(4) // sps_video_parameter_set_id
        val maxSubLayersMinus1 = bits.read(3)
        bits.skip(1) // sps_temporal_id_nesting_flag
        if (maxSubLayersMinus1 > MAX_SUB_LAYERS_MINUS1) return null
        bits.skip(2 + 1) // general_profile_space, general_tier_flag
        val profileIdc = bits.read(5)
        var compatibilityFlags = 0
        for (j in 0 until 32) if (bits.read(1) == 1)
            compatibilityFlags = compatibilityFlags or (1 shl j)
        bits.skip(4 + 43 + 1) // source and constraint flags, reserved bits
        bits.skip(8) // general_level_idc
        skipSubLayers(bits, maxSubLayersMinus1)
        bits.exp() // sps_seq_parameter_set_id
        val chromaFormatIdc = bits.exp()
        val separateColourPlane = chromaFormatIdc == 3L && bits.read(1) == 1
        bits.exp() // pic_width_in_luma_samples
        bits.exp() // pic_height_in_luma_samples
        if (bits.read(1) == 1) repeat(4) { bits.exp() } // conformance window offsets
        val luma = bits.exp()
        val chroma = bits.exp()
        val valid =
            chromaFormatIdc <= MAX_CHROMA_FORMAT_IDC &&
                luma <= MAX_BIT_DEPTH_MINUS8 &&
                chroma <= MAX_BIT_DEPTH_MINUS8
        if (!valid) return null
        return HevcSpsInfo(
            profileIdc,
            compatibilityFlags,
            chromaFormatIdc.toInt(),
            separateColourPlane,
            luma.toInt() + 8,
            chroma.toInt() + 8,
        )
    }

    /** The sub-layer part of profile_tier_level; the values are not needed. */
    private fun skipSubLayers(bits: BitReader, maxSubLayersMinus1: Int) {
        var profilePresent = 0
        var levelPresent = 0
        repeat(maxSubLayersMinus1) {
            profilePresent += bits.read(1)
            levelPresent += bits.read(1)
        }
        if (maxSubLayersMinus1 > 0) bits.skip(2 * (8 - maxSubLayersMinus1)) // reserved_zero_2bits
        bits.skip(profilePresent * (2 + 1 + 5 + 32 + 4 + 43 + 1))
        bits.skip(levelPresent * 8)
    }

    /** Reads MSB first. Past the end every read returns 0 and sets [overrun]; nothing throws. */
    private class BitReader(private val data: ByteArray) {
        private var position = 0
        var overrun = false
            private set

        private val totalBits = data.size * 8

        fun skip(count: Int) {
            if (count > totalBits - position) overrun = true
            position = if (overrun) totalBits else position + count
        }

        /** [count] is at most 32. */
        fun read(count: Int): Int {
            if (overrun || count > totalBits - position) {
                overrun = true
                return 0
            }
            var value = 0L
            repeat(count) {
                val byte = data[position shr 3].toInt()
                value = (value shl 1) or ((byte shr (7 - (position and 7))) and 1).toLong()
                position++
            }
            return value.toInt()
        }

        /** ue(v), at most 31 leading zeros as the specification allows. */
        fun exp(): Long {
            var zeros = 0
            while (!overrun && read(1) == 0) {
                if (++zeros > MAX_EXP_GOLOMB_ZEROS) overrun = true
            }
            if (overrun) return 0
            return (1L shl zeros) - 1 + (read(zeros).toLong() and 0xFFFF_FFFFL)
        }
    }
}
