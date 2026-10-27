package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test

/**
 * Characterizes the bytes that `submitDecodeUnit` queues to the codec for H.264: the SPS that the
 * renderer rewrites before it goes to the decoder, the parameter sets that are sent as codec
 * config, the IDR frame and the predicted frame, with their flags and timestamps.
 *
 * The SPS the renderer writes is compared byte for byte with an encoding made by [H264Sps], which
 * is written from the specification and shares no code with the library that the renderer uses.
 */
class RendererSubmitH264Test : RendererTestBase() {
    private val pps = bytes("00000001 68ce3c80")
    private val slice = bytes("00000001 6588 8400 33ff")

    private fun avcDecoder(name: String, change: CodecBuilder.() -> Unit = {}) =
        install(
            codec(name) {
                mime = "video/avc"
                change()
            }
        )

    /** A renderer for the decoder [name], set up for H.264. */
    private fun h264(
        name: String = "vendor.test.avc.decoder",
        change: CodecBuilder.() -> Unit = {},
    ): MediaCodecDecoderRenderer {
        avcDecoder(name, change)
        return renderer().also { assertThat(it.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0) }
    }

    private fun sps(nal: ByteArray, number: Int = 1) =
        parameterSet(MoonBridge.BUFFER_TYPE_SPS, hex(nal), number)

    /** Sends the three units of an IDR frame; the SPS is [nal]. Returns what each call returned. */
    private fun MediaCodecDecoderRenderer.sendIdr(nal: ByteArray, number: Int = 1): List<Int> =
        listOf(submit(sps(nal, number)), submit(h264Pps(number)), submit(idrSlice(number)))

    private fun queued() = events<CodecEvent.Queued>()

    private val original = H264Sps()

    /**
     * The SPS as the renderer patches it for a decoder that is not asked to keep its references.
     */
    private val patched =
        original.copy(
            numRefFrames = 1,
            vui =
                original.vui?.copy(
                    restriction = H264Sps.restrictionAdded(maxDecFrameBuffering = 1)
                ),
        )

    // The patched SPS, byte for byte

    @Test
    fun `an SPS with a VUI gets one reference frame and a bitstream restriction`() {
        val renderer = h264()

        renderer.sendIdr(original.nal())

        assertThat(queued().first().hex).isEqualTo(hex(patched.nal() + pps))
    }

    @Test
    fun `an SPS without a VUI gets a VUI with a bitstream restriction`() {
        val renderer = h264()
        val noVui = original.copy(vui = null)

        renderer.sendIdr(noVui.nal())

        val expected =
            noVui.copy(
                numRefFrames = 1,
                vui =
                    Vui(
                        timing = false,
                        videoSignal = false,
                        restriction = H264Sps.restrictionAdded(1),
                    ),
            )
        assertThat(queued().first().hex).isEqualTo(hex(expected.nal() + pps))
    }

    @Test
    fun `an SPS that has a bitstream restriction keeps it but for the frame buffering`() {
        val renderer = h264()
        val restricted =
            original.copy(vui = Vui(restriction = Restriction(maxDecFrameBuffering = 4)))

        renderer.sendIdr(restricted.nal())

        val expected =
            restricted.copy(
                numRefFrames = 1,
                vui = Vui(restriction = Restriction(maxDecFrameBuffering = 1)),
            )
        assertThat(queued().first().hex).isEqualTo(hex(expected.nal() + pps))
    }

    @Test
    fun `an SPS with a three byte start code is rewritten behind the same start code`() {
        val renderer = h264()

        renderer.sendIdr(original.nal(H264Sps.START_CODE_3))

        assertThat(queued().first().hex).isEqualTo(hex(patched.nal(H264Sps.START_CODE_3) + pps))
    }

    @Test
    fun `a decoder that invalidates reference frames keeps the references of the SPS`() {
        val renderer = h264("c2.qti.avc.decoder")

        renderer.sendIdr(original.nal())

        val expected =
            original.copy(
                vui =
                    original.vui?.copy(
                        restriction = H264Sps.restrictionAdded(maxDecFrameBuffering = 4)
                    )
            )
        assertThat(queued().first().hex).isEqualTo(hex(expected.nal() + pps))
        assertThat(log.lines()).doesNotContain("INFO: Patching num_ref_frames in SPS")
    }

    @Test
    fun `the rewriting of an SPS is logged step by step`() {
        val renderer = h264()

        renderer.sendIdr(original.nal())
        renderer.sendIdr(original.copy(vui = null).nal(), number = 2)
        renderer.sendIdr(original.copy(vui = Vui(restriction = Restriction())).nal(), number = 3)

        assertThat(
                log.lines().filter {
                    it.contains("SPS") || it.contains("VUI") || it.contains("restrictions")
                }
            )
            .containsExactly(
                "INFO: Patching num_ref_frames in SPS",
                "INFO: Adding bitstream restrictions",
                "INFO: Patching num_ref_frames in SPS",
                "INFO: Adding VUI parameters",
                "INFO: Adding bitstream restrictions",
                "INFO: Patching num_ref_frames in SPS",
                "INFO: Patching bitstream restrictions",
            )
            .inOrder()
    }

    // The sequence of an IDR frame

    @Test
    fun `the parameter sets are held back until the picture data of the IDR frame arrives`() {
        val renderer = h264()

        val results = renderer.sendIdr(original.nal())

        assertThat(results).containsExactly(MoonBridge.DR_OK, MoonBridge.DR_OK, MoonBridge.DR_OK)
        assertThat(queued()).hasSize(2)
    }

    @Test
    fun `the parameter sets go to the codec as one config buffer and the frame as another`() {
        val renderer = h264()

        renderer.sendIdr(original.nal())

        val (config, frame) = queued()
        assertThat(config.flags).isEqualTo(CODEC_CONFIG)
        assertThat(config.ptsUs).isEqualTo(0)
        assertThat(config.hex).isEqualTo(hex(patched.nal() + pps))
        assertThat(frame.flags).isEqualTo(SYNC_FRAME)
        assertThat(frame.hex).isEqualTo(hex(slice))
    }

    @Test
    fun `a decoder that is not fused gets the parameter sets again with every IDR frame`() {
        val renderer = h264()

        renderer.sendIdr(original.nal(), number = 1)
        renderer.sendIdr(original.nal(), number = 2)

        assertThat(queued().map { it.flags })
            .containsExactly(CODEC_CONFIG, SYNC_FRAME, CODEC_CONFIG, SYNC_FRAME)
            .inOrder()
        assertThat(queued()[2].hex).isEqualTo(hex(patched.nal() + pps))
    }

    @Test
    fun `a fused decoder gets the parameter sets alone once and then in front of each IDR frame`() {
        val renderer = h264(change = { adaptivePlayback = true })

        renderer.sendIdr(original.nal(), number = 1)
        renderer.sendIdr(original.nal(), number = 2)

        assertThat(queued().map { it.flags })
            .containsExactly(CODEC_CONFIG, SYNC_FRAME, SYNC_FRAME)
            .inOrder()
        assertThat(queued()[1].hex).isEqualTo(hex(slice))
        assertThat(queued()[2].hex).isEqualTo(hex(patched.nal() + pps + slice))
    }

    @Test
    fun `the parameter sets of one IDR frame accumulate even when a kind comes twice`() {
        val renderer = h264()
        renderer.submit(sps(original.nal(), number = 1))
        renderer.submit(h264Pps(number = 1))

        // The same frame number again: the native layer sends each NAL unit of an IDR frame so.
        renderer.submit(h264Pps(number = 1))
        renderer.submit(idrSlice(number = 1))

        assertThat(queued().first().hex).isEqualTo(hex(patched.nal() + pps + pps))
    }

    @Test
    fun `a new IDR frame forgets the parameter sets of the last one`() {
        val renderer = h264()
        renderer.sendIdr(original.nal(), number = 1)

        renderer.sendIdr(original.nal(), number = 2)

        assertThat(queued()[2].hex).isEqualTo(hex(patched.nal() + pps))
    }

    // The baseline profile hack

    @Test
    fun `a decoder that needs the baseline hack first gets an SPS that says baseline`() {
        val renderer = h264("omx.intel.avc.decoder")

        renderer.sendIdr(original.nal())

        val baseline = patched.copy(profile = 66)
        assertThat(queued()[0].hex).isEqualTo(hex(baseline.nal() + pps))
        assertThat(queued()[0].flags).isEqualTo(CODEC_CONFIG)
        assertThat(log.lines()).contains("INFO: Hacking SPS to baseline")
    }

    @Test
    fun `the baseline hack is followed by an SPS of the real profile with the constraint flags`() {
        val renderer = h264("omx.intel.avc.decoder")

        renderer.sendIdr(original.nal())

        val replay = bytes("00000001 67") + patched.copy(constraintSets = setOf(4, 5)).nal().drop(5)
        assertThat(queued().map { it.flags })
            .containsExactly(CODEC_CONFIG, CODEC_CONFIG, SYNC_FRAME)
            .inOrder()
        assertThat(queued()[1].hex).isEqualTo(hex(replay))
        assertThat(queued()[1].ptsUs).isEqualTo(0)
        assertThat(queued()[2].hex).isEqualTo(hex(slice))
        assertThat(log.lines())
            .containsAtLeast(
                "INFO: Setting constraint set flags for constrained high profile",
                "INFO: SPS replay complete",
            )
    }

    @Test
    fun `the baseline hack is used once`() {
        val renderer = h264("omx.intel.avc.decoder")
        renderer.sendIdr(original.nal(), number = 1)

        renderer.sendIdr(original.nal(), number = 2)

        val constrained = patched.copy(constraintSets = setOf(4, 5))
        assertThat(queued().map { it.flags })
            .containsExactly(CODEC_CONFIG, CODEC_CONFIG, SYNC_FRAME, CODEC_CONFIG, SYNC_FRAME)
            .inOrder()
        assertThat(queued()[3].hex).isEqualTo(hex(constrained.nal() + pps))
        assertThat(log.lines().filter { it == "INFO: SPS replay complete" }).hasSize(1)
    }

    @Test
    fun `the constraint flags are only set for the High profile`() {
        val renderer = h264("omx.intel.avc.decoder")
        val main = original.copy(profile = 77)
        // The first IDR frame uses up the baseline hack.
        renderer.sendIdr(main.nal(), number = 1)

        renderer.sendIdr(main.nal(), number = 2)

        val expected = main.copy(numRefFrames = 1, vui = patched.vui)
        assertThat(queued().last { it.flags == CODEC_CONFIG }.hex)
            .isEqualTo(hex(expected.nal() + pps))
    }

    @Test
    fun `the replay after the baseline hack says High profile whatever the stream said`() {
        val renderer = h264("omx.intel.avc.decoder")
        val main = original.copy(profile = 77)

        renderer.sendIdr(main.nal())

        val replayed = patched.copy(profile = 100, constraintSets = setOf(4, 5))
        assertThat(queued()[1].hex).isEqualTo(hex(bytes("00000001 67") + replayed.nal().drop(5)))
    }

    // Frames that are not IDR frames

    @Test
    fun `a predicted frame is queued as it came with no flags`() {
        val renderer = h264()

        val result = renderer.submit(pFrame(1, DecodeUnit.Times(enqueueUs = 5_000)))

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        val frame = queued().single()
        assertThat(frame.hex).isEqualTo(hex(bytes("00000001 419a 0011 22")))
        assertThat(frame.flags).isEqualTo(0)
        assertThat(frame.ptsUs).isEqualTo(100_000)
    }

    @Test
    fun `the data of a decode unit longer than the unit is copied only up to the length`() {
        val renderer = h264()
        val padded = bytes("00000001 419a 0011 22 ffffffff")

        val result =
            renderer.submitDecodeUnit(
                padded,
                9,
                BUFFER_TYPE_PICDATA,
                1,
                FRAME_TYPE_P,
                0.toChar(),
                0,
                0,
            )

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        assertThat(queued().single().hex).isEqualTo(hex(bytes("00000001 419a 0011 22")))
    }

    @Test
    fun `nothing is queued after the renderer was told to stop`() {
        val renderer = h264()
        renderer.prepareForStop()

        val result = renderer.submit(pFrame(1))

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        assertThat(queued()).isEmpty()
    }
}
