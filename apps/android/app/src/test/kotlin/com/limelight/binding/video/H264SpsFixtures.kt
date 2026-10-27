package com.limelight.binding.video

import java.io.ByteArrayOutputStream

/**
 * A bit writer for H.264 syntax. It is written from the specification (ITU-T H.264, 7.3.2.1.1 and
 * E.1.1), not from the library that the renderer uses, so that the SPS the renderer writes is
 * compared with an encoding it does not share code with.
 */
private class SpsBits {
    private val bytes = ByteArrayOutputStream()
    private var current = 0
    private var used = 0

    fun bit(value: Boolean) = bits(if (value) 1L else 0L, 1)

    fun bits(value: Long, count: Int) {
        for (shift in count - 1 downTo 0) {
            current = (current shl 1) or ((value shr shift) and 1L).toInt()
            if (++used == BITS_PER_BYTE) {
                bytes.write(current)
                current = 0
                used = 0
            }
        }
    }

    fun bits(value: Int, count: Int) = bits(value.toLong(), count)

    /** An unsigned Exp-Golomb code, ue(v). */
    fun ue(value: Int) {
        val coded = value + 1L
        val width = Long.SIZE_BITS - java.lang.Long.numberOfLeadingZeros(coded)
        bits(0L, width - 1)
        bits(coded, width)
    }

    /** The stop bit and the zero bits that align the end. */
    fun finish(): ByteArray {
        bit(true)
        while (used != 0) bit(false)
        return bytes.toByteArray()
    }

    private companion object {
        const val BITS_PER_BYTE = 8
    }
}

/** The base 2 logarithms of the longest motion vector, horizontal and vertical. */
internal data class MvLength(val horizontal: Int, val vertical: Int)

/** The bitstream restriction of a VUI. */
internal data class Restriction(
    val motionVectorsOverPicBoundaries: Boolean = true,
    val maxBytesPerPicDenom: Int = 4,
    val maxBitsPerMbDenom: Int = 0,
    val log2MaxMvLength: MvLength = MvLength(horizontal = 15, vertical = 14),
    val numReorderFrames: Int = 2,
    val maxDecFrameBuffering: Int = 4,
)

/** The VUI of an SPS: the parts an encoder writes, and its bitstream restriction if it has one. */
internal data class Vui(
    val timing: Boolean = true,
    val videoSignal: Boolean = true,
    val restriction: Restriction? = null,
)

/**
 * The fields of an H.264 SPS that a test sets; the rest is fixed. The defaults are a High profile
 * SPS of 1920 x 1080 as an encoder writes it: level 4.0, four reference frames, a cropped bottom
 * edge, and a VUI with timing and video signal information but no bitstream restriction.
 */
internal data class H264Sps(
    val profile: Int = 100,
    val constraintSets: Set<Int> = emptySet(),
    val level: Int = 40,
    val numRefFrames: Int = 4,
    val vui: Vui? = Vui(),
) {
    /** The NAL unit as the host sends it: the start code, the header byte `67`, the escaped data. */
    fun nal(startCode: ByteArray = START_CODE_4): ByteArray =
        startCode + byteArrayOf(NAL_HEADER) + escape(rbsp())

    private fun rbsp(): ByteArray {
        val w = SpsBits()
        w.bits(profile, 8)
        for (flag in 0..5) w.bit(flag in constraintSets)
        w.bits(0, 2) // reserved_zero_2bits
        w.bits(level, 8)
        w.ue(0) // seq_parameter_set_id
        if (profile in HIGH_PROFILES) {
            w.ue(1) // chroma_format_idc: 4:2:0
            w.ue(0) // bit_depth_luma_minus8
            w.ue(0) // bit_depth_chroma_minus8
            w.bit(false) // qpprime_y_zero_transform_bypass_flag
            w.bit(false) // seq_scaling_matrix_present_flag
        }
        w.ue(0) // log2_max_frame_num_minus4
        w.ue(0) // pic_order_cnt_type
        w.ue(2) // log2_max_pic_order_cnt_lsb_minus4
        w.ue(numRefFrames)
        w.bit(false) // gaps_in_frame_num_value_allowed_flag
        w.ue(119) // pic_width_in_mbs_minus1: 1920
        w.ue(67) // pic_height_in_map_units_minus1: 1088
        w.bit(true) // frame_mbs_only_flag
        w.bit(true) // direct_8x8_inference_flag
        w.bit(true) // frame_cropping_flag
        w.ue(0)
        w.ue(0)
        w.ue(0)
        w.ue(4) // frame_crop_bottom_offset: 8 lines, so 1080
        w.bit(vui != null)
        vui?.let { writeVui(w, it) }
        return w.finish()
    }

    private fun writeVui(w: SpsBits, vui: Vui) {
        w.bit(false) // aspect_ratio_info_present_flag
        w.bit(false) // overscan_info_present_flag
        w.bit(vui.videoSignal)
        if (vui.videoSignal) {
            w.bits(5, 3) // video_format: unspecified
            w.bit(false) // video_full_range_flag
            w.bit(true) // colour_description_present_flag
            w.bits(1, 8) // colour_primaries: BT.709
            w.bits(1, 8) // transfer_characteristics: BT.709
            w.bits(1, 8) // matrix_coefficients: BT.709
        }
        w.bit(false) // chroma_loc_info_present_flag
        w.bit(vui.timing)
        if (vui.timing) {
            w.bits(1, 32) // num_units_in_tick
            w.bits(120, 32) // time_scale
            w.bit(true) // fixed_frame_rate_flag
        }
        w.bit(false) // nal_hrd_parameters_present_flag
        w.bit(false) // vcl_hrd_parameters_present_flag
        w.bit(false) // pic_struct_present_flag
        w.bit(vui.restriction != null)
        vui.restriction?.let {
            w.bit(it.motionVectorsOverPicBoundaries)
            w.ue(it.maxBytesPerPicDenom)
            w.ue(it.maxBitsPerMbDenom)
            w.ue(it.log2MaxMvLength.horizontal)
            w.ue(it.log2MaxMvLength.vertical)
            w.ue(it.numReorderFrames)
            w.ue(it.maxDecFrameBuffering)
        }
    }

    companion object {
        val START_CODE_4 = byteArrayOf(0, 0, 0, 1)
        val START_CODE_3 = byteArrayOf(0, 0, 1)
        const val NAL_HEADER: Byte = 0x67

        // The profiles whose SPS carries the chroma format and the bit depths.
        private val HIGH_PROFILES = setOf(100, 110, 122, 144)

        /** What the renderer adds to an SPS that has no bitstream restriction. */
        fun restrictionAdded(maxDecFrameBuffering: Int) =
            Restriction(
                motionVectorsOverPicBoundaries = true,
                maxBytesPerPicDenom = 2,
                maxBitsPerMbDenom = 1,
                log2MaxMvLength = MvLength(horizontal = 16, vertical = 16),
                numReorderFrames = 0,
                maxDecFrameBuffering = maxDecFrameBuffering,
            )

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
    }
}
