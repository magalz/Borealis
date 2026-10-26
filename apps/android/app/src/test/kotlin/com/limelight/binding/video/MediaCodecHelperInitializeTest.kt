package com.limelight.binding.video

import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes `MediaCodecHelper.initialize`: what it appends to the prefix lists for the Fire TV
 * and Amazon checks and for each OpenGL ES version and renderer, which Adreno flags it sets, and
 * that it runs once. Each row loads a fresh helper, runs `initialize` once and reads what the lists
 * gained.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCodecHelperInitializeTest : MediaCodecHelperTestBase() {
    /** What `initialize` leaves behind: the list additions by field name and the Adreno flags. */
    private data class Outcome(
        val additions: Map<String, List<String>>,
        val lowEndSnapdragon: Boolean = false,
        val adreno620: Boolean = false,
    )

    private class Scenario(
        val label: String,
        val build: DeviceBuild,
        val gl: GlFacts,
        val fireTv: Boolean,
        val outcome: Outcome,
    )

    private fun observe(scenario: Scenario): Outcome {
        val loaded = fresh(scenario.build)
        setGl(scenario.gl, scenario.fireTv)
        val additions = loaded.additionsDuring { loaded.initialize(context, scenario.gl.renderer) }
        check(loaded.flag("initialized")) { "initialize does not end with initialized set" }
        return Outcome(
            additions,
            lowEndSnapdragon = loaded.flag("isLowEndSnapdragon"),
            adreno620 = loaded.flag("isAdreno620"),
        )
    }

    private fun scenario(
        label: String,
        gl: GlFacts,
        outcome: Outcome,
        build: DeviceBuild = DeviceBuild(),
        fireTv: Boolean = false,
    ) = Scenario(label, build, gl, fireTv, outcome)

    private fun checkAll(vararg scenarios: Scenario) {
        val rows = Rows()
        scenarios.forEach { rows.check(it.label, it.outcome) { observe(it) } }
        rows.verify()
    }

    @Test
    fun `a device that reports no OpenGL ES version adds nothing`() =
        checkAll(
            scenario(
                "Adreno 640",
                GlFacts(GlFacts.UNDEFINED, "Adreno (TM) 640"),
                Outcome(emptyMap()),
            ),
            scenario(
                "Adreno 306",
                GlFacts(GlFacts.UNDEFINED, "Adreno (TM) 306"),
                Outcome(emptyMap()),
            ),
            scenario(
                "PowerVR GX6",
                GlFacts(GlFacts.UNDEFINED, "PowerVR Rogue GX6250"),
                Outcome(emptyMap()),
            ),
        )

    @Test
    fun `the Adreno model decides the Qualcomm HEVC decoders`() =
        checkAll(
            scenario(
                "OpenGL ES 2.0 adds no reference frame invalidation",
                GlFacts(GlFacts.GL_2_0, "Adreno (TM) 640"),
                Outcome(mapOf(HEVC_WHITE to QUALCOMM)),
            ),
            scenario("Adreno 640", GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"), Outcome(MODERN)),
            scenario(
                "OpenGL ES 3.0 is the lowest version with reference frame invalidation",
                GlFacts(GlFacts.GL_3_0, "Adreno (TM) 530"),
                Outcome(MODERN),
            ),
            scenario(
                "an Adreno renderer in capitals",
                GlFacts(GlFacts.GL_3_2, "ADRENO (TM) 640"),
                Outcome(MODERN),
            ),
            scenario(
                "an Adreno name without a model number counts as another GPU",
                GlFacts(GlFacts.GL_3_2, "Adreno (TM) 6xx"),
                Outcome(OLD),
            ),
            scenario(
                "a Mali GPU counts as an old Qualcomm decoder",
                GlFacts(GlFacts.GL_3_2, "Mali-G78 MP14"),
                Outcome(OLD),
            ),
        )

    @Test
    fun `the Adreno model sets the low end and the Adreno 620 flags`() =
        checkAll(
            scenario(
                "Adreno 405 is low end and has OpenGL ES 3.1 decoders",
                GlFacts(GlFacts.GL_3_0 + 1, "Adreno (TM) 405"),
                Outcome(MODERN, lowEndSnapdragon = true),
            ),
            scenario(
                "Adreno 306 is low end and has the old decoders",
                GlFacts(GlFacts.GL_3_0, "Adreno (TM) 306"),
                Outcome(OLD, lowEndSnapdragon = true),
            ),
            scenario(
                "Adreno 400 is the first one with OpenGL ES 3.1 decoders",
                GlFacts(GlFacts.GL_3_0 + 1, "Adreno (TM) 400"),
                Outcome(MODERN, lowEndSnapdragon = true),
            ),
            scenario(
                "Adreno 620 is the one with the operating rate problem",
                GlFacts(GlFacts.GL_3_2, "Adreno (TM) 620"),
                Outcome(MODERN, adreno620 = true),
            ),
            scenario(
                "Adreno 530 is neither",
                GlFacts(GlFacts.GL_3_2, "Adreno (TM) 530"),
                Outcome(MODERN),
            ),
        )

    @Test
    fun `NVIDIA gets HEVC reference frame invalidation on Android 11 and later except on the Pixel C`() {
        val gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640")
        val withoutNvidia = listOf("c2.nvidia", "omx.qcom", "c2.qti")
        checkAll(
            scenario(
                "Android 10",
                gl,
                Outcome(
                    mapOf(AVC_RFI to RFI_ALL, HEVC_RFI to withoutNvidia, HEVC_WHITE to QUALCOMM)
                ),
                build = DeviceBuild(sdk = Build.VERSION_CODES.Q),
            ),
            scenario(
                "Android 11",
                gl,
                Outcome(MODERN),
                build = DeviceBuild(sdk = Build.VERSION_CODES.R),
            ),
            scenario(
                "the Pixel C on Android 13",
                gl,
                Outcome(
                    mapOf(AVC_RFI to RFI_ALL, HEVC_RFI to withoutNvidia, HEVC_WHITE to QUALCOMM)
                ),
                build = DeviceBuild(device = "dragon"),
            ),
            scenario(
                "the Pixel C name ignores the case",
                gl,
                Outcome(
                    mapOf(AVC_RFI to RFI_ALL, HEVC_RFI to withoutNvidia, HEVC_WHITE to QUALCOMM)
                ),
                build = DeviceBuild(device = "DRAGON"),
            ),
        )
    }

    @Test
    fun `a PowerVR GPU allows MediaTek HEVC and the Series6XT also reference frame invalidation`() =
        checkAll(
            scenario(
                "PowerVR GX6250",
                GlFacts(GlFacts.GL_3_2, "PowerVR Rogue GX6250"),
                Outcome(
                    mapOf(
                        AVC_RFI to RFI_ALL,
                        HEVC_RFI to RFI_ALL + listOf("omx.mtk", "c2.mtk"),
                        BLACK to listOf("OMX.qcom.video.decoder.hevc"),
                        FOUR_SLICES to listOf("omx.qcom"),
                        HEVC_WHITE to listOf("omx.mtk"),
                    )
                ),
            ),
            scenario(
                "PowerVR GE8320",
                GlFacts(GlFacts.GL_3_2, "PowerVR Rogue GE8320"),
                Outcome(
                    mapOf(
                        AVC_RFI to RFI_ALL,
                        HEVC_RFI to RFI_ALL,
                        BLACK to listOf("OMX.qcom.video.decoder.hevc"),
                        FOUR_SLICES to listOf("omx.qcom"),
                        HEVC_WHITE to listOf("omx.mtk"),
                    )
                ),
            ),
            scenario(
                "the PowerVR name ignores the case but the GX6 series does not",
                GlFacts(GlFacts.GL_3_2, "powervr rogue gx6250"),
                Outcome(
                    mapOf(
                        AVC_RFI to RFI_ALL,
                        HEVC_RFI to RFI_ALL,
                        BLACK to listOf("OMX.qcom.video.decoder.hevc"),
                        FOUR_SLICES to listOf("omx.qcom"),
                        HEVC_WHITE to listOf("omx.mtk"),
                    )
                ),
            ),
            scenario(
                "PowerVR without OpenGL ES 3.0 has no reference frame invalidation list",
                GlFacts(GlFacts.GL_2_0, "PowerVR Rogue GX6250"),
                Outcome(
                    mapOf(
                        HEVC_RFI to listOf("omx.mtk", "c2.mtk"),
                        BLACK to listOf("OMX.qcom.video.decoder.hevc"),
                        FOUR_SLICES to listOf("omx.qcom"),
                        HEVC_WHITE to listOf("omx.mtk"),
                    )
                ),
            ),
        )

    @Test
    fun `an Amazon device allows MediaTek and Amlogic HEVC with reference frame invalidation`() {
        val amazon =
            Outcome(
                mapOf(
                    HEVC_WHITE to listOf("omx.mtk", "omx.amlogic"),
                    HEVC_RFI to listOf("omx.mtk", "c2.mtk", "omx.amlogic", "c2.amlogic"),
                )
            )
        val noGl = GlFacts(GlFacts.UNDEFINED, "Adreno (TM) 640")
        checkAll(
            scenario("the Fire TV system feature", noGl, amazon, fireTv = true),
            scenario(
                "the Amazon manufacturer of a Fire tablet",
                noGl,
                amazon,
                build = DeviceBuild(manufacturer = "Amazon"),
            ),
            scenario(
                "the manufacturer ignores the case",
                noGl,
                amazon,
                build = DeviceBuild(manufacturer = "amazon"),
            ),
            scenario(
                "a manufacturer that only starts with Amazon",
                noGl,
                Outcome(emptyMap()),
                build = DeviceBuild(manufacturer = "Amazon Basics"),
            ),
            scenario(
                "Sabrina on Fire OS gets Amlogic HEVC back",
                noGl,
                amazon,
                build = DeviceBuild(device = "sabrina"),
                fireTv = true,
            ),
            scenario(
                "the Amazon entries come before the GPU ones",
                GlFacts(GlFacts.GL_3_2, "PowerVR Rogue GX6250"),
                Outcome(
                    mapOf(
                        AVC_RFI to RFI_ALL,
                        HEVC_RFI to
                            listOf("omx.mtk", "c2.mtk", "omx.amlogic", "c2.amlogic") +
                                RFI_ALL +
                                listOf("omx.mtk", "c2.mtk"),
                        BLACK to listOf("OMX.qcom.video.decoder.hevc"),
                        FOUR_SLICES to listOf("omx.qcom"),
                        HEVC_WHITE to listOf("omx.mtk", "omx.amlogic", "omx.mtk"),
                    )
                ),
                fireTv = true,
            ),
        )
    }

    @Test
    fun `initialize runs only once`() {
        val loaded = fresh()
        setGl(GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"), fireTv = false)
        loaded.initialize(context, "Adreno (TM) 640")

        setGl(GlFacts(GlFacts.GL_3_2, "Adreno (TM) 306"), fireTv = true)
        val second = loaded.additionsDuring { loaded.initialize(context, "Adreno (TM) 306") }

        assertThatSecondRunChangedNothing(second, loaded)
    }

    private fun assertThatSecondRunChangedNothing(
        additions: Map<String, List<String>>,
        loaded: HelperHandle,
    ) {
        val rows = Rows()
        rows.check("the second run adds nothing", emptyMap<String, List<String>>()) { additions }
        rows.check("the Adreno flags of the first run stay", listOf(false, false)) {
            listOf(loaded.flag("isLowEndSnapdragon"), loaded.flag("isAdreno620"))
        }
        rows.check("the state stays initialized", true) { loaded.flag("initialized") }
        rows.verify()
    }

    private companion object {
        const val AVC_RFI = "refFrameInvalidationAvcPrefixes"
        const val HEVC_RFI = "refFrameInvalidationHevcPrefixes"
        const val HEVC_WHITE = "whitelistedHevcDecoders"
        const val BLACK = "blacklistedDecoderPrefixes"
        const val FOUR_SLICES = "useFourSlicesPrefixes"

        val QUALCOMM = listOf("omx.qcom", "c2.qti")

        /** What OpenGL ES 3.0 and later adds on a phone on Android 13 (not a Pixel C). */
        val RFI_ALL = listOf("omx.nvidia", "c2.nvidia", "omx.qcom", "c2.qti")

        /** A modern Adreno (400 and later) with OpenGL ES 3.0 or later. */
        val MODERN = mapOf(AVC_RFI to RFI_ALL, HEVC_RFI to RFI_ALL, HEVC_WHITE to QUALCOMM)

        /** A GPU that is not an Adreno 400 or later, with OpenGL ES 3.0 or later. */
        val OLD =
            mapOf(
                AVC_RFI to RFI_ALL,
                HEVC_RFI to RFI_ALL,
                BLACK to listOf("OMX.qcom.video.decoder.hevc"),
                FOUR_SLICES to listOf("omx.qcom"),
            )
    }
}
