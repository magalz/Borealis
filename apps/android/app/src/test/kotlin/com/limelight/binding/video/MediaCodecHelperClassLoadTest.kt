package com.limelight.binding.video

import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes what `MediaCodecHelper` decides when its class loads, before `initialize`: the
 * `Build` fields that the static initializers read and the prefix lists that they seed. Each row
 * loads a fresh copy of the helper after it sets the `Build` fields.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCodecHelperClassLoadTest : MediaCodecHelperTestBase() {
    private fun blacklist(build: DeviceBuild) = fresh(build).strings("blacklistedDecoderPrefixes")

    private fun hevcWhitelist(build: DeviceBuild) = fresh(build).strings("whitelistedHevcDecoders")

    @Test
    fun `the software block is bypassed on emulators and on Android-x86 only`() {
        val rows = Rows()

        rows.check("the Android emulator", true) {
            fresh(DeviceBuild(hardware = "ranchu")).bypassesSoftwareBlock
        }
        rows.check("a Chromebook", true) {
            fresh(DeviceBuild(hardware = "cheets")).bypassesSoftwareBlock
        }
        rows.check("Android-x86", true) {
            fresh(DeviceBuild(brand = "Android-x86")).bypassesSoftwareBlock
        }
        rows.check("the hardware name is case sensitive", false) {
            fresh(DeviceBuild(hardware = "RANCHU")).bypassesSoftwareBlock
        }
        rows.check("the brand is case sensitive", false) {
            fresh(DeviceBuild(brand = "android-x86")).bypassesSoftwareBlock
        }
        rows.check("a phone", false) { fresh(DeviceBuild()).bypassesSoftwareBlock }

        rows.verify()
    }

    @Test
    fun `the blacklist keeps the software decoders out unless the device has no other`() {
        val hevcSoftware = listOf("OMX.qcom.video.decoder.hevcswvdec", "OMX.SEC.hevc.sw.dec")
        val rows = Rows()

        rows.check("a phone on Android 13", listOf("omx.google", "AVCDecoder") + hevcSoftware) {
            blacklist(DeviceBuild(sdk = Build.VERSION_CODES.TIRAMISU))
        }
        rows.check(
            "a phone before Android 10 also blocks ffmpeg",
            listOf("omx.google", "AVCDecoder", "OMX.ffmpeg") + hevcSoftware,
        ) {
            blacklist(DeviceBuild(sdk = Build.VERSION_CODES.P))
        }
        rows.check(
            "Android 10 leaves ffmpeg to isSoftwareOnly",
            listOf("omx.google", "AVCDecoder") + hevcSoftware,
        ) {
            blacklist(DeviceBuild(sdk = Build.VERSION_CODES.Q))
        }
        rows.check("the emulator blocks only the HEVC software decoders", hevcSoftware) {
            blacklist(DeviceBuild(hardware = "ranchu", sdk = Build.VERSION_CODES.P))
        }
        rows.check("a Chromebook blocks only the HEVC software decoders", hevcSoftware) {
            blacklist(DeviceBuild(hardware = "cheets"))
        }
        rows.check("Android-x86 blocks only the HEVC software decoders", hevcSoftware) {
            blacklist(DeviceBuild(brand = "Android-x86"))
        }

        rows.verify()
    }

    @Test
    fun `the HEVC whitelist at class load depends on the device`() {
        val base = listOf("omx.exynos", "omx.nvidia", "omx.amlogic", "omx.realtek", "c2.")
        val rows = Rows()

        rows.check("a phone", base) { hevcWhitelist(DeviceBuild()) }
        rows.check("the emulator also allows the software decoder", listOf("omx.google") + base) {
            hevcWhitelist(DeviceBuild(hardware = "ranchu"))
        }
        val withoutNvidia = listOf("omx.exynos", "omx.amlogic", "omx.realtek", "c2.")
        rows.check("the Shield Tablet has no NVIDIA HEVC", withoutNvidia) {
            hevcWhitelist(DeviceBuild(device = "shieldtablet"))
        }
        rows.check("the device name of the Shield Tablet ignores the case", withoutNvidia) {
            hevcWhitelist(DeviceBuild(device = "ShieldTablet"))
        }
        rows.check("the Mi Pad has no NVIDIA HEVC", withoutNvidia) {
            hevcWhitelist(DeviceBuild(device = "mocha"))
        }
        rows.check("the device name of the Mi Pad ignores the case", withoutNvidia) {
            hevcWhitelist(DeviceBuild(device = "MOCHA"))
        }
        rows.check(
            "a Sony Bravia allows MediaTek HEVC",
            listOf("omx.exynos", "omx.nvidia", "omx.mtk", "omx.amlogic", "omx.realtek", "c2."),
        ) {
            hevcWhitelist(DeviceBuild(device = "BRAVIA_ATV3_4K"))
        }
        rows.check("the Bravia prefix is case sensitive", base) {
            hevcWhitelist(DeviceBuild(device = "bravia_atv3_4k"))
        }
        rows.check("the Bravia prefix is a prefix", base) {
            hevcWhitelist(DeviceBuild(device = "SONY_BRAVIA_ATV3"))
        }
        val withoutAmlogic = listOf("omx.exynos", "omx.nvidia", "omx.realtek", "c2.")
        rows.check("Sabrina has no Amlogic HEVC", withoutAmlogic) {
            hevcWhitelist(DeviceBuild(device = "sabrina"))
        }
        rows.check("the device name of Sabrina ignores the case", withoutAmlogic) {
            hevcWhitelist(DeviceBuild(device = "SABRINA"))
        }
        rows.check(
            "the checks add up",
            listOf("omx.google", "omx.exynos", "omx.amlogic", "omx.realtek", "c2."),
        ) {
            hevcWhitelist(DeviceBuild(hardware = "ranchu", device = "shieldtablet"))
        }

        rows.verify()
    }

    @Test
    fun `the other lists hold their fixed prefixes at class load`() {
        val loaded = fresh()
        val rows = Rows()
        val expected =
            mapOf(
                "spsFixupBitstreamFixupDecoderPrefixes" to
                    listOf("omx.nvidia", "omx.qcom", "omx.brcm"),
                "blacklistedAdaptivePlaybackPrefixes" to listOf("omx.intel", "omx.mtk"),
                "baselineProfileHackPrefixes" to listOf("omx.intel"),
                "directSubmitPrefixes" to
                    listOf(
                        "omx.qcom",
                        "omx.sec",
                        "omx.exynos",
                        "omx.intel",
                        "omx.brcm",
                        "omx.TI",
                        "omx.arc",
                        "omx.nvidia",
                        "c2.",
                    ),
                "constrainedHighProfilePrefixes" to listOf("omx.intel"),
                "refFrameInvalidationAvcPrefixes" to emptyList(),
                "refFrameInvalidationHevcPrefixes" to listOf("omx.exynos", "c2.exynos"),
                "useFourSlicesPrefixes" to
                    listOf("omx.google", "AVCDecoder", "omx.ffmpeg", "c2.android"),
                "qualcommDecoderPrefixes" to listOf("omx.qcom", "c2.qti"),
                "kirinDecoderPrefixes" to listOf("omx.hisi", "c2.hisi"),
                "exynosDecoderPrefixes" to listOf("omx.exynos", "c2.exynos"),
                "amlogicDecoderPrefixes" to listOf("omx.amlogic", "c2.amlogic"),
                "knownVendorLowLatencyOptions" to
                    listOf(
                        "vendor.qti-ext-dec-low-latency.enable",
                        "vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req",
                        "vendor.rtc-ext-dec-low-latency.enable",
                        "vendor.low-latency.enable",
                    ),
            )

        expected.forEach { (field, prefixes) ->
            rows.check(field, prefixes) { loaded.strings(field) }
        }
        rows.check(
            "every list is checked",
            HelperHandle.LIST_FIELDS.toSet() -
                setOf("blacklistedDecoderPrefixes", "whitelistedHevcDecoders"),
        ) {
            expected.keys
        }

        rows.verify()
    }

    @Test
    fun `a fresh helper is not initialized and has no Adreno flag`() {
        val rows = Rows()

        rows.check("before initialize", false) { fresh().flag("initialized") }
        rows.check("the Adreno flags", listOf(false, false)) {
            fresh().let { listOf(it.flag("isLowEndSnapdragon"), it.flag("isAdreno620")) }
        }

        rows.verify()
    }
}
