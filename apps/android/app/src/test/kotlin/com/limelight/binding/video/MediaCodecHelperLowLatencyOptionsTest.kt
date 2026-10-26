package com.limelight.binding.video

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes `setDecoderLowLatencyOptions`: for each try number, which keys the helper sets on
 * the format and whether it says that it set one. The options go from the most to the least risky,
 * so a higher try number sets fewer of them, and the vendor options depend on the decoder family.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCodecHelperLowLatencyOptionsTest : MediaCodecHelperTestBase() {
    /** What a call did: whether it reported a new option and the integer keys that it added. */
    private data class Applied(val newOption: Boolean, val options: Map<String, Int>)

    private fun applied(helper: HelperHandle, decoder: MediaCodecInfo, tryNumber: Int): Applied {
        val format = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        val before = format.keys.toSet()
        val newOption = helper.lowLatencyOptions(format, decoder, tryNumber)
        return Applied(newOption, (format.keys - before).associateWith { format.getInteger(it) })
    }

    /** Checks the tries of one decoder: the keys added for each try number from 0, then none. */
    private fun checkTries(
        label: String,
        helper: HelperHandle,
        decoder: MediaCodecInfo,
        vararg expected: Map<String, Int>,
    ) {
        val rows = Rows()
        expected.forEachIndexed { tryNumber, options ->
            rows.check("$label, try $tryNumber", Applied(options.isNotEmpty(), options)) {
                applied(helper, decoder, tryNumber)
            }
        }
        rows.check("$label, try ${expected.size}", Applied(false, emptyMap())) {
            applied(helper, decoder, expected.size)
        }
        rows.check("$label, a try number below zero is the first try", Applied(true, expected[0])) {
            applied(helper, decoder, -1)
        }
        rows.verify()
    }

    private fun avc(name: String, configure: CodecBuilder.() -> Unit = {}) = codec(name, configure)

    @Test
    fun `a decoder of no known family gets the generic options for three tries`() {
        val helper = helper()

        checkTries(
            "MediaTek",
            helper,
            avc("OMX.MTK.VIDEO.DECODER.AVC"),
            mapOf(LOW_LATENCY to 1, VDEC_LOW_LATENCY to 1, PRIORITY to 0),
            mapOf(VDEC_LOW_LATENCY to 1, PRIORITY to 0),
            mapOf(PRIORITY to 0),
        )
    }

    @Test
    fun `a Qualcomm decoder gets the operating rate and two vendor options`() {
        val helper = helper(gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"))
        val expected =
            arrayOf(
                mapOf(
                    LOW_LATENCY to 1,
                    VDEC_LOW_LATENCY to 1,
                    OPERATING_RATE to Short.MAX_VALUE.toInt(),
                    QTI_PICTURE_ORDER to 1,
                    QTI_LOW_LATENCY to 1,
                ),
                mapOf(
                    VDEC_LOW_LATENCY to 1,
                    OPERATING_RATE to Short.MAX_VALUE.toInt(),
                    QTI_PICTURE_ORDER to 1,
                    QTI_LOW_LATENCY to 1,
                ),
                mapOf(
                    OPERATING_RATE to Short.MAX_VALUE.toInt(),
                    QTI_PICTURE_ORDER to 1,
                    QTI_LOW_LATENCY to 1,
                ),
                mapOf(QTI_PICTURE_ORDER to 1, QTI_LOW_LATENCY to 1),
                mapOf(QTI_LOW_LATENCY to 1),
            )

        checkTries("OMX.qcom", helper, avc("OMX.qcom.video.decoder.avc"), *expected)
        checkTries("c2.qti", helper, avc("c2.qti.avc.decoder"), *expected)
        checkTries("the case of the name", helper, avc("OMX.QCOM.Video.Decoder.AVC"), *expected)
    }

    @Test
    fun `an Adreno 620 gets the realtime priority instead of the operating rate`() {
        val helper = helper(gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 620"))

        checkTries(
            "OMX.qcom on Adreno 620",
            helper,
            avc("OMX.qcom.video.decoder.avc"),
            mapOf(
                LOW_LATENCY to 1,
                VDEC_LOW_LATENCY to 1,
                PRIORITY to 0,
                QTI_PICTURE_ORDER to 1,
                QTI_LOW_LATENCY to 1,
            ),
            mapOf(
                VDEC_LOW_LATENCY to 1,
                PRIORITY to 0,
                QTI_PICTURE_ORDER to 1,
                QTI_LOW_LATENCY to 1,
            ),
            mapOf(PRIORITY to 0, QTI_PICTURE_ORDER to 1, QTI_LOW_LATENCY to 1),
            mapOf(QTI_PICTURE_ORDER to 1, QTI_LOW_LATENCY to 1),
            mapOf(QTI_LOW_LATENCY to 1),
        )
    }

    @Test
    fun `a Kirin decoder gets the HiSilicon options for four tries`() {
        val helper = helper()
        val expected =
            arrayOf(
                mapOf(
                    LOW_LATENCY to 1,
                    VDEC_LOW_LATENCY to 1,
                    PRIORITY to 0,
                    KIRIN_REQUEST to 1,
                    KIRIN_READY to -1,
                ),
                mapOf(VDEC_LOW_LATENCY to 1, PRIORITY to 0, KIRIN_REQUEST to 1, KIRIN_READY to -1),
                mapOf(PRIORITY to 0, KIRIN_REQUEST to 1, KIRIN_READY to -1),
                mapOf(KIRIN_REQUEST to 1, KIRIN_READY to -1),
            )

        checkTries("OMX.hisi", helper, avc("OMX.hisi.video.decoder.avc"), *expected)
        checkTries("c2.hisi", helper, avc("c2.hisi.avc.decoder"), *expected)
    }

    @Test
    fun `an Exynos decoder gets the real time communication option for four tries`() {
        val helper = helper()
        val expected =
            arrayOf(
                mapOf(LOW_LATENCY to 1, VDEC_LOW_LATENCY to 1, PRIORITY to 0, EXYNOS_RTC to 1),
                mapOf(VDEC_LOW_LATENCY to 1, PRIORITY to 0, EXYNOS_RTC to 1),
                mapOf(PRIORITY to 0, EXYNOS_RTC to 1),
                mapOf(EXYNOS_RTC to 1),
            )

        checkTries("OMX.Exynos", helper, avc("OMX.Exynos.AVC.Decoder"), *expected)
        checkTries("c2.exynos", helper, avc("c2.exynos.h264.decoder"), *expected)
    }

    @Test
    fun `an Amlogic decoder gets the low latency enable option for four tries`() {
        val helper = helper()
        val expected =
            arrayOf(
                mapOf(LOW_LATENCY to 1, VDEC_LOW_LATENCY to 1, PRIORITY to 0, AMLOGIC to 1),
                mapOf(VDEC_LOW_LATENCY to 1, PRIORITY to 0, AMLOGIC to 1),
                mapOf(PRIORITY to 0, AMLOGIC to 1),
                mapOf(AMLOGIC to 1),
            )

        checkTries("OMX.amlogic", helper, avc("OMX.amlogic.avc.decoder"), *expected)
        checkTries("c2.amlogic", helper, avc("c2.amlogic.avc.decoder"), *expected)
    }

    @Test
    fun `FEATURE_LowLatency from Android 11 ends the first try with the official key alone`() {
        val rows = Rows()
        val withFeature = avc("c2.qti.avc.decoder") { lowLatency = true }

        rows.check("try 0 on Android 13", Applied(true, mapOf(LOW_LATENCY to 1))) {
            applied(helper(), withFeature, 0)
        }
        rows.check("try 0 on Android 11", Applied(true, mapOf(LOW_LATENCY to 1))) {
            applied(helper(DeviceBuild(sdk = Build.VERSION_CODES.R)), withFeature, 0)
        }
        rows.check(
            "try 1 goes on with the other options and not the official key",
            Applied(
                true,
                mapOf(
                    VDEC_LOW_LATENCY to 1,
                    OPERATING_RATE to Short.MAX_VALUE.toInt(),
                    QTI_PICTURE_ORDER to 1,
                    QTI_LOW_LATENCY to 1,
                ),
            ),
        ) {
            applied(helper(), withFeature, 1)
        }
        rows.check(
            "try 0 on Android 10 does not look at the feature",
            Applied(
                true,
                mapOf(
                    LOW_LATENCY to 1,
                    VDEC_LOW_LATENCY to 1,
                    OPERATING_RATE to Short.MAX_VALUE.toInt(),
                    QTI_PICTURE_ORDER to 1,
                    QTI_LOW_LATENCY to 1,
                ),
            ),
        ) {
            applied(helper(DeviceBuild(sdk = Build.VERSION_CODES.Q)), withFeature, 0)
        }
        rows.check(
            "try 0 with a decoder that throws for its capabilities",
            Applied(true, mapOf(LOW_LATENCY to 1, VDEC_LOW_LATENCY to 1, PRIORITY to 0)),
        ) {
            applied(
                helper(),
                avc("OMX.MTK.VIDEO.DECODER.AVC") {
                    lowLatency = true
                    brokenCapabilities = true
                },
                0,
            )
        }
        rows.check(
            "the official key alone for any decoder",
            Applied(true, mapOf(LOW_LATENCY to 1)),
        ) {
            applied(helper(), avc("OMX.MTK.VIDEO.DECODER.AVC") { lowLatency = true }, 0)
        }

        rows.verify()
    }

    private companion object {
        const val LOW_LATENCY = "low-latency"
        const val VDEC_LOW_LATENCY = "vdec-lowlatency"
        const val OPERATING_RATE = MediaFormat.KEY_OPERATING_RATE
        const val PRIORITY = MediaFormat.KEY_PRIORITY
        const val QTI_PICTURE_ORDER = "vendor.qti-ext-dec-picture-order.enable"
        const val QTI_LOW_LATENCY = "vendor.qti-ext-dec-low-latency.enable"
        const val KIRIN_REQUEST =
            "vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req"
        const val KIRIN_READY =
            "vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-rdy"
        const val EXYNOS_RTC = "vendor.rtc-ext-dec-low-latency.enable"
        const val AMLOGIC = "vendor.low-latency.enable"
    }
}
