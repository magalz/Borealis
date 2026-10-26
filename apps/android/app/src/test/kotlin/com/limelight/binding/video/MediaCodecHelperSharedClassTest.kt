package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The same helper, through the class that `Game` and the renderer call. Its static state lives as
 * long as the sandbox, so the tests copy it before and put it back after; the other tests load a
 * fresh copy of the class instead.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCodecHelperSharedClassTest : MediaCodecHelperTestBase() {
    private val shared = HelperHandle(MediaCodecHelper::class.java)
    private lateinit var saved: HelperHandle.State

    @Before
    fun saveState() {
        saved = shared.snapshot()
    }

    @After
    fun restoreState() {
        shared.restore(saved)
    }

    @Test
    fun `initialize adds the lists of the GPU once and the decisions read them`() {
        val gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640")
        buildFacts.set(DeviceBuild())
        setGl(gl, fireTv = false)
        shared.setFlag("initialized", false)
        val rfi = listOf("omx.nvidia", "c2.nvidia", "omx.qcom", "c2.qti")

        val first = shared.additionsDuring { MediaCodecHelper.initialize(context, gl.renderer) }
        val second = shared.additionsDuring {
            MediaCodecHelper.initialize(context, "Adreno (TM) 306")
        }

        assertThat(first)
            .containsExactly(
                "refFrameInvalidationAvcPrefixes",
                rfi,
                "refFrameInvalidationHevcPrefixes",
                rfi,
                "whitelistedHevcDecoders",
                listOf("omx.qcom", "c2.qti"),
            )
        assertThat(second).isEmpty()
        assertThat(shared.flag("initialized")).isTrue()
        assertThat(MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc("OMX.qcom.video", 1080))
            .isTrue()
        assertThat(MediaCodecHelper.decoderCanDirectSubmit("omx.qcom.video")).isTrue()
        assertThat(MediaCodecHelper.decoderNeedsBaselineSpsHack("omx.intel.hw_vd.h264")).isTrue()
        val slices: Byte = MediaCodecHelper.getDecoderOptimalSlicesPerFrame("OMX.google.h264")
        assertThat(slices).isEqualTo(4.toByte())
    }

    @Test
    fun `before initialize the decisions throw`() {
        shared.setFlag("initialized", false)

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                MediaCodecHelper.decoderNeedsBaselineSpsHack("omx.intel.hw_vd.h264")
            }

        assertThat(thrown).hasMessageThat().isEqualTo(HelperHandle.NOT_INITIALIZED)
    }
}
