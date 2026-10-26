package com.limelight.binding.video

import android.media.MediaCodecInfo.CodecProfileLevel
import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes how `MediaCodecHelper` picks a decoder from the list of the device:
 * `findFirstDecoder` takes the first one that is not blacklisted and `findProbableSafeDecoder`
 * prefers a decoder with FEATURE_LowLatency, checks the required profile and falls back to the
 * first decoder when a decoder throws. The rows give the decoders as names and read back the name
 * of the decoder that the helper chose.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCodecHelperSelectionTest : MediaCodecHelperTestBase() {
    private val avc = "video/avc"
    private val hevc = "video/hevc"

    private fun first(helper: HelperHandle, mime: String = avc) =
        helper.findFirstDecoder(mime)?.name

    private fun probable(helper: HelperHandle, mime: String = avc, profile: Int = -1) =
        helper.findProbableSafeDecoder(mime, profile)?.name

    @Test
    fun `the first decoder is the first one of the type and the list order wins`() {
        val helper = helper()
        val rows = Rows()

        rows.check("an empty list", null) {
            installCodecs()
            first(helper)
        }
        rows.check("the first of two", "c2.vendor.first") {
            installCodecs(codec("c2.vendor.first"), codec("c2.vendor.second"))
            first(helper)
        }
        rows.check("encoders are skipped", "c2.vendor.decoder") {
            installCodecs(codec("c2.vendor.encoder") { encoder = true }, codec("c2.vendor.decoder"))
            first(helper)
        }
        rows.check("another type is skipped", "c2.vendor.avc") {
            installCodecs(codec("c2.vendor.hevc") { mime = hevc }, codec("c2.vendor.avc"))
            first(helper)
        }
        rows.check("no decoder of the type", null) {
            installCodecs(codec("c2.vendor.hevc") { mime = hevc })
            first(helper)
        }
        rows.check("the type ignores the case", "c2.vendor.avc") {
            installCodecs(codec("c2.vendor.avc"))
            first(helper, "VIDEO/AVC")
        }
        rows.check("a second supported type counts", "c2.vendor.both") {
            installCodecs(codec("c2.vendor.both") { mimes = listOf(hevc, avc) })
            first(helper)
        }

        rows.verify()
    }

    @Test
    fun `the first decoder skips aliases from Android 10`() {
        val rows = Rows()
        fun aliasThenReal() =
            installCodecs(
                codec("c2.vendor.alias") { canonicalName = "c2.vendor.real" },
                codec("c2.vendor.real"),
            )

        rows.check("Android 13", "c2.vendor.real") {
            val helper = helper()
            aliasThenReal()
            first(helper)
        }
        rows.check("Android 10", "c2.vendor.real") {
            val helper = helper(DeviceBuild(sdk = Build.VERSION_CODES.Q))
            aliasThenReal()
            first(helper)
        }
        rows.check("Android 9 does not look at aliases", "c2.vendor.alias") {
            val helper = helper(DeviceBuild(sdk = Build.VERSION_CODES.P))
            aliasThenReal()
            first(helper)
        }

        rows.verify()
    }

    @Test
    fun `the first decoder skips blacklisted decoders and software only decoders`() {
        val rows = Rows()

        rows.check("the Google software decoder", "c2.vendor.avc") {
            val helper = helper()
            installCodecs(codec("OMX.google.h264.decoder"), codec("c2.vendor.avc"))
            first(helper)
        }
        rows.check("the blacklist ignores the case", "c2.vendor.avc") {
            val helper = helper()
            installCodecs(codec("omx.GOOGLE.h264.decoder"), codec("c2.vendor.avc"))
            first(helper)
        }
        rows.check("the AVCDecoder software decoder", "c2.vendor.avc") {
            val helper = helper()
            installCodecs(codec("AVCDecoder"), codec("c2.vendor.avc"))
            first(helper)
        }
        rows.check("ffmpeg before Android 10", "c2.vendor.avc") {
            val helper = helper(DeviceBuild(sdk = Build.VERSION_CODES.P))
            installCodecs(codec("OMX.ffmpeg.h264.decoder"), codec("c2.vendor.avc"))
            first(helper)
        }
        rows.check("ffmpeg from Android 10", "OMX.ffmpeg.h264.decoder") {
            val helper = helper()
            installCodecs(codec("OMX.ffmpeg.h264.decoder"), codec("c2.vendor.avc"))
            first(helper)
        }
        rows.check("a software only decoder from Android 10", "c2.vendor.avc") {
            val helper = helper()
            installCodecs(
                codec("c2.android.avc.decoder") { softwareOnly = true },
                codec("c2.vendor.avc"),
            )
            first(helper)
        }
        rows.check("the flag is not looked at before Android 10", "c2.android.avc.decoder") {
            val helper = helper(DeviceBuild(sdk = Build.VERSION_CODES.P))
            installCodecs(
                codec("c2.android.avc.decoder") { softwareOnly = true },
                codec("c2.vendor.avc"),
            )
            first(helper)
        }
        rows.check("the only decoder is blacklisted", null) {
            val helper = helper()
            installCodecs(codec("OMX.google.h264.decoder"))
            first(helper)
        }
        rows.check("a blacklisted decoder of two types is skipped whole", "c2.vendor.avc") {
            val helper = helper()
            installCodecs(
                codec("OMX.google.h264.decoder") { mimes = listOf(hevc, avc) },
                codec("c2.vendor.avc"),
            )
            first(helper)
        }

        rows.verify()
    }

    @Test
    fun `an emulator or a Chromebook takes the software decoders`() {
        val rows = Rows()

        listOf(
                DeviceBuild(hardware = "ranchu"),
                DeviceBuild(hardware = "cheets"),
                DeviceBuild(brand = "Android-x86"),
            )
            .forEach { build ->
                rows.check(
                    "${build.hardware}/${build.brand} takes the Google decoder",
                    "OMX.google.h264.decoder",
                ) {
                    val helper = helper(build)
                    installCodecs(codec("OMX.google.h264.decoder") { softwareOnly = true })
                    first(helper)
                }
            }
        rows.check("the Qualcomm HEVC software decoder stays out", "c2.vendor.hevc") {
            val helper = helper(DeviceBuild(hardware = "ranchu"))
            installCodecs(
                codec("OMX.qcom.video.decoder.hevcswvdec") { mime = hevc },
                codec("c2.vendor.hevc") { mime = hevc },
            )
            first(helper, hevc)
        }
        rows.check("the Samsung HEVC software decoder stays out", "c2.vendor.hevc") {
            val helper = helper(DeviceBuild(hardware = "ranchu"))
            installCodecs(
                codec("OMX.SEC.hevc.sw.dec") { mime = hevc },
                codec("c2.vendor.hevc") { mime = hevc },
            )
            first(helper, hevc)
        }

        rows.verify()
    }

    @Test
    fun `an old Adreno blacklists the Qualcomm HEVC decoder that a new one takes`() {
        val rows = Rows()

        rows.check("Adreno 306", "c2.vendor.hevc") {
            val helper = helper(gl = GlFacts(GlFacts.GL_3_0, "Adreno (TM) 306"))
            installCodecs(
                codec("OMX.qcom.video.decoder.hevc") { mime = hevc },
                codec("c2.vendor.hevc") { mime = hevc },
            )
            first(helper, hevc)
        }
        rows.check("Adreno 640", "OMX.qcom.video.decoder.hevc") {
            val helper = helper(gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"))
            installCodecs(
                codec("OMX.qcom.video.decoder.hevc") { mime = hevc },
                codec("c2.vendor.hevc") { mime = hevc },
            )
            first(helper, hevc)
        }
        rows.check(
            "the blacklist of the old decoder is not for AVC",
            "OMX.qcom.video.decoder.avc",
        ) {
            val helper = helper(gl = GlFacts(GlFacts.GL_3_0, "Adreno (TM) 306"))
            installCodecs(codec("OMX.qcom.video.decoder.avc"))
            first(helper)
        }

        rows.verify()
    }

    @Test
    fun `the probable safe decoder prefers a decoder with FEATURE_LowLatency`() {
        val rows = Rows()

        rows.check("the second has the feature", "c2.vendor.low") {
            val helper = helper()
            installCodecs(codec("c2.vendor.plain"), codec("c2.vendor.low") { lowLatency = true })
            probable(helper)
        }
        rows.check("two with the feature: the first", "c2.vendor.low1") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.plain"),
                codec("c2.vendor.low1") { lowLatency = true },
                codec("c2.vendor.low2") { lowLatency = true },
            )
            probable(helper)
        }
        rows.check("none has it: the first", "c2.vendor.plain1") {
            val helper = helper()
            installCodecs(codec("c2.vendor.plain1"), codec("c2.vendor.plain2"))
            probable(helper)
        }
        rows.check("Android 10 does not look at the feature", "c2.vendor.plain") {
            val helper = helper(DeviceBuild(sdk = Build.VERSION_CODES.Q))
            installCodecs(codec("c2.vendor.plain"), codec("c2.vendor.low") { lowLatency = true })
            probable(helper)
        }
        rows.check("no decoder", null) {
            val helper = helper()
            installCodecs()
            probable(helper)
        }
        rows.check("no decoder of the type", null) {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.hevc") {
                    mime = hevc
                    lowLatency = true
                }
            )
            probable(helper)
        }
        rows.check("the type ignores the case", "c2.vendor.low") {
            val helper = helper()
            installCodecs(codec("c2.vendor.low") { lowLatency = true })
            probable(helper, "Video/AVC")
        }
        rows.check("a decoder of two types is found by either type", "c2.vendor.mixed") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.plain"),
                codec("c2.vendor.mixed") {
                    mimes = listOf(hevc, avc)
                    lowLatency = true
                },
            )
            probable(helper)
        }

        rows.verify()
    }

    @Test
    fun `the probable safe decoder skips encoders and aliases`() {
        val rows = Rows()

        rows.check("encoders", "c2.vendor.decoder") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.encoder") {
                    encoder = true
                    lowLatency = true
                },
                codec("c2.vendor.decoder"),
            )
            probable(helper)
        }
        rows.check("aliases from Android 10", "c2.vendor.real") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.alias") {
                    canonicalName = "c2.vendor.real"
                    lowLatency = true
                },
                codec("c2.vendor.real"),
            )
            probable(helper)
        }
        rows.check("aliases are not looked at before Android 10", "c2.vendor.alias") {
            val helper = helper(DeviceBuild(sdk = Build.VERSION_CODES.P))
            installCodecs(
                codec("c2.vendor.alias") { canonicalName = "c2.vendor.real" },
                codec("c2.vendor.real"),
            )
            probable(helper)
        }
        rows.verify()
    }

    @Test
    fun `the probable safe decoder skips blacklisted and software only decoders`() {
        val rows = Rows()

        rows.check("the blacklist", "c2.vendor.avc") {
            val helper = helper()
            installCodecs(
                codec("OMX.google.h264.decoder") { lowLatency = true },
                codec("c2.vendor.avc"),
            )
            probable(helper)
        }
        rows.check("software only decoders from Android 10", "c2.vendor.avc") {
            val helper = helper()
            installCodecs(
                codec("c2.android.avc.decoder") {
                    softwareOnly = true
                    lowLatency = true
                },
                codec("c2.vendor.avc"),
            )
            probable(helper)
        }
        rows.check("the emulator takes software only decoders", "c2.android.avc.decoder") {
            val helper = helper(DeviceBuild(hardware = "ranchu"))
            installCodecs(
                codec("c2.android.avc.decoder") {
                    softwareOnly = true
                    lowLatency = true
                }
            )
            probable(helper)
        }
        rows.check("only blacklisted decoders", null) {
            val helper = helper()
            installCodecs(codec("OMX.google.h264.decoder"), codec("AVCDecoder"))
            probable(helper)
        }

        rows.verify()
    }

    @Test
    fun `the probable safe decoder checks the required profile`() {
        val high = CodecProfileLevel.AVCProfileHigh
        val baseline = CodecProfileLevel.AVCProfileBaseline
        val main = CodecProfileLevel.AVCProfileMain
        val rows = Rows()

        rows.check("the second has the profile", "c2.vendor.high") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.baseline") { profiles = listOf(baseline) },
                codec("c2.vendor.high") { profiles = listOf(baseline, high) },
            )
            probable(helper, profile = high)
        }
        rows.check("the first has the profile", "c2.vendor.baseline") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.baseline") { profiles = listOf(baseline, high) },
                codec("c2.vendor.high") { profiles = listOf(high) },
            )
            probable(helper, profile = high)
        }
        rows.check("none has the profile", null) {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.baseline") { profiles = listOf(baseline) },
                codec("c2.vendor.high") {
                    profiles = listOf(high)
                    lowLatency = true
                },
            )
            probable(helper, profile = main)
        }
        rows.verify()
    }

    @Test
    fun `the probable safe decoder takes the profile and the feature together`() {
        val high = CodecProfileLevel.AVCProfileHigh
        val baseline = CodecProfileLevel.AVCProfileBaseline
        val rows = Rows()

        rows.check("without a required profile the first decoder is enough", "c2.vendor.baseline") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.baseline") { profiles = listOf(baseline) },
                codec("c2.vendor.high") { profiles = listOf(high) },
            )
            probable(helper, profile = -1)
        }
        rows.check("the profile of a decoder with the feature comes first", "c2.vendor.low") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.plain") { profiles = listOf(high) },
                codec("c2.vendor.low") {
                    profiles = listOf(high)
                    lowLatency = true
                },
            )
            probable(helper, profile = high)
        }
        rows.check(
            "a feature decoder without the profile gives way to the second round",
            "c2.vendor.plain",
        ) {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.low") {
                    profiles = listOf(baseline)
                    lowLatency = true
                },
                codec("c2.vendor.plain") { profiles = listOf(high) },
            )
            probable(helper, profile = high)
        }

        rows.verify()
    }

    @Test
    fun `a decoder that throws for its capabilities sends the search to the first decoder`() {
        val rows = Rows()

        rows.check("the first decoder is the one that throws", "c2.vendor.buggy") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.buggy") { brokenCapabilities = true },
                codec("c2.vendor.low") { lowLatency = true },
            )
            probable(helper)
        }
        rows.check("a later decoder throws and the first one is taken", "c2.vendor.plain") {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.plain"),
                codec("c2.vendor.buggy") { brokenCapabilities = true },
                codec("c2.vendor.low") { lowLatency = true },
            )
            probable(helper)
        }
        rows.check("a blacklisted decoder that throws is never asked", "c2.vendor.low") {
            val helper = helper()
            installCodecs(
                codec("OMX.google.h264.decoder") { brokenCapabilities = true },
                codec("c2.vendor.low") { lowLatency = true },
            )
            probable(helper)
        }
        rows.check(
            "a blacklisted decoder that throws is skipped before its capabilities are read",
            "c2.vendor.low",
        ) {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.plain"),
                codec("OMX.google.h264.decoder") { brokenCapabilities = true },
                codec("c2.vendor.low") { lowLatency = true },
            )
            probable(helper)
        }
        rows.check("the fallback skips blacklisted decoders", "c2.vendor.avc") {
            val helper = helper()
            installCodecs(
                codec("OMX.google.h264.decoder"),
                codec("c2.vendor.avc") { brokenCapabilities = true },
            )
            probable(helper)
        }
        rows.check("the fallback does not look at the profile", "c2.vendor.buggy") {
            val helper = helper()
            installCodecs(codec("c2.vendor.buggy") { brokenCapabilities = true })
            probable(helper, profile = CodecProfileLevel.AVCProfileHigh)
        }

        rows.verify()
    }
}
