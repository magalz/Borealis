package com.limelight.binding.video

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes the decisions that `MediaCodecHelper` takes from a decoder name alone: direct
 * submit, the SPS and profile workarounds, the slices per frame and AVC reference frame
 * invalidation. The prefix match ignores the case and needs the name to be as long as the prefix.
 * The rows read `/proc/cpuinfo` and `/sys/devices/system` of the machine through `isExynos4Device`
 * for direct submit, and no machine that runs them is an Exynos 4.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCodecHelperNameDecisionsTest : MediaCodecHelperTestBase() {
    @Test
    fun `decoders with a low enough input latency are submitted directly`() {
        val helper = helper()
        val rows = Rows()

        listOf(
                "omx.qcom.video.decoder.avc",
                "OMX.qcom.video.decoder.avc",
                "OMX.SEC.avc.dec",
                "OMX.Exynos.AVC.Decoder",
                "OMX.Intel.hw_vd.h264",
                "OMX.brcm.video.h264.hw.decoder",
                "OMX.TI.DUCATI1.VIDEO.DECODER",
                "omx.ti.video.decoder",
                "OMX.arc.video.decoder",
                "OMX.Nvidia.h264.decode",
                "c2.qti.avc.decoder",
                "c2.android.avc.decoder",
                "C2.exynos.h264.decoder",
                "c2.",
            )
            .forEach { name ->
                rows.check("$name submits directly", true) { helper.canDirectSubmit(name) }
            }
        listOf(
                "OMX.google.h264.decoder",
                "OMX.MTK.VIDEO.DECODER.AVC",
                "OMX.rk.video_decoder.avc",
                "OMX.amlogic.avc.decoder",
                "OMX.hisi.video.decoder.avc",
                "c2",
                "omx.qco",
                "x.omx.qcom.video",
                "",
            )
            .forEach { name ->
                rows.check("$name does not", false) { helper.canDirectSubmit(name) }
            }

        rows.verify()
    }

    @Test
    fun `only the Intel decoders need the constrained high profile and the baseline SPS hack`() {
        val helper = helper()
        val rows = Rows()

        listOf("omx.intel.hw_vd.h264", "OMX.Intel.hw_vd.h264", "OMX.INTEL").forEach { name ->
            rows.check("$name needs the constrained high profile", true) {
                helper.needsConstrainedHighProfile(name)
            }
            rows.check("$name needs the baseline SPS hack", true) {
                helper.needsBaselineSpsHack(name)
            }
        }
        listOf("OMX.qcom.video.decoder.avc", "OMX.Inte", "c2.intel.avc", "").forEach { name ->
            rows.check("$name needs no constrained high profile", false) {
                helper.needsConstrainedHighProfile(name)
            }
            rows.check("$name needs no baseline SPS hack", false) {
                helper.needsBaselineSpsHack(name)
            }
        }

        rows.verify()
    }

    @Test
    fun `NVIDIA, Qualcomm and Broadcom decoders need the SPS bitstream restrictions fixup`() {
        val helper = helper()
        val rows = Rows()

        listOf("omx.nvidia.h264.decode", "OMX.qcom.video.decoder.avc", "OMX.BRCM.video.h264")
            .forEach { name ->
                rows.check("$name needs the fixup", true) {
                    helper.needsSpsBitstreamRestrictions(name)
                }
            }
        listOf("OMX.Exynos.AVC.Decoder", "c2.qti.avc.decoder", "OMX.Intel.hw_vd.h264", "OMX.qco")
            .forEach { name ->
                rows.check("$name does not", false) { helper.needsSpsBitstreamRestrictions(name) }
            }

        rows.verify()
    }

    @Test
    fun `software decoders use four slices per frame and so do old Qualcomm decoders`() {
        val rows = Rows()
        val modern = helper(gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"))
        val oldAdreno = helper(gl = GlFacts(GlFacts.GL_3_0, "Adreno (TM) 306"))
        val mali = helper(gl = GlFacts(GlFacts.GL_3_2, "Mali-G78 MP14"))
        val noGl = helper(gl = GlFacts(GlFacts.UNDEFINED, "Adreno (TM) 306"))
        val four: Byte = 4
        val one: Byte = 1

        listOf(
                "OMX.google.h264.decoder",
                "AVCDecoder",
                "avcdecoder",
                "OMX.ffmpeg.h264",
                "c2.android.avc.decoder",
            )
            .forEach { name ->
                rows.check("$name has four slices", four) { modern.slicesPerFrame(name) }
            }
        listOf(
                "OMX.qcom.video.decoder.avc",
                "c2.qti.avc.decoder",
                "OMX.Exynos.AVC.Decoder",
                "OMX.MTK.VIDEO.DECODER.AVC",
                "omx.goo",
                "",
            )
            .forEach { name ->
                rows.check("$name has one slice", one) { modern.slicesPerFrame(name) }
            }
        rows.check("a Qualcomm decoder on an old Adreno", four) {
            oldAdreno.slicesPerFrame("OMX.qcom.video.decoder.avc")
        }
        rows.check("a Codec2 Qualcomm decoder on an old Adreno", one) {
            oldAdreno.slicesPerFrame("c2.qti.avc.decoder")
        }
        rows.check("a Qualcomm decoder on a Mali GPU", four) {
            mali.slicesPerFrame("omx.qcom.video.decoder.avc")
        }
        rows.check("a Qualcomm decoder without an OpenGL ES version", one) {
            noGl.slicesPerFrame("OMX.qcom.video.decoder.avc")
        }

        rows.verify()
    }

    @Test
    fun `AVC reference frame invalidation follows the lists that OpenGL ES 3 adds`() {
        val modern = helper(gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"))
        val gles2 = helper(gl = GlFacts(GlFacts.GL_2_0, "Adreno (TM) 640"))
        val noGl = helper(gl = GlFacts(GlFacts.UNDEFINED, "Adreno (TM) 640"))
        val rows = Rows()

        listOf(
                "OMX.Nvidia.h264.decode",
                "c2.nvidia.avc.decoder",
                "OMX.qcom.video.decoder.avc",
                "c2.qti.avc.decoder",
            )
            .forEach { name ->
                rows.check("$name can invalidate", true) {
                    modern.refFrameInvalidationAvc(name, 1080)
                }
            }
        listOf("OMX.Exynos.AVC.Decoder", "OMX.google.h264.decoder", "OMX.MTK.VIDEO.DECODER.AVC")
            .forEach { name ->
                rows.check("$name cannot", false) { modern.refFrameInvalidationAvc(name, 1080) }
            }
        rows.check("OpenGL ES 2 has no list", false) {
            gles2.refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 720)
        }
        rows.check("no OpenGL ES version has no list", false) {
            noGl.refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 720)
        }

        rows.verify()
    }

    @Test
    fun `AVC reference frame invalidation is off above 720p on a low end Snapdragon`() {
        val lowEnd = helper(gl = GlFacts(GlFacts.GL_3_0, "Adreno (TM) 306"))
        val lowEnd405 = helper(gl = GlFacts(GlFacts.GL_3_0 + 1, "Adreno (TM) 405"))
        val modern = helper(gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"))
        val rows = Rows()

        rows.check("Adreno 306 at 1080p", false) {
            lowEnd.refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 1080)
        }
        rows.check("Adreno 306 at 721p", false) {
            lowEnd.refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 721)
        }
        rows.check("Adreno 306 at 720p", true) {
            lowEnd.refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 720)
        }
        rows.check("Adreno 306 below 720p", true) {
            lowEnd.refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 480)
        }
        rows.check("the rule is for every decoder name", false) {
            lowEnd.refFrameInvalidationAvc("OMX.Nvidia.h264.decode", 1440)
        }
        rows.check("Adreno 405 at 1440p", false) {
            lowEnd405.refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 1440)
        }
        rows.check("Adreno 405 at 720p", true) {
            lowEnd405.refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 720)
        }
        rows.check("Adreno 640 at 4K", true) {
            modern.refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 2160)
        }

        rows.verify()
    }

    @Test
    fun `AVC reference frame invalidation is off on the b3 and b5 devices`() {
        val rows = Rows()

        listOf("b3", "b5").forEach { device ->
            rows.check("$device at 720p", false) {
                helper(DeviceBuild(device = device))
                    .refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 720)
            }
        }
        listOf("B3", "b4", "b30").forEach { device ->
            rows.check("$device is another device", true) {
                helper(DeviceBuild(device = device))
                    .refFrameInvalidationAvc("OMX.qcom.video.decoder.avc", 720)
            }
        }

        rows.verify()
    }
}
