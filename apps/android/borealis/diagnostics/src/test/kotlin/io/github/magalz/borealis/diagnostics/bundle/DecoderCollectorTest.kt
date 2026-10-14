package io.github.magalz.borealis.diagnostics.bundle

import android.media.MediaCodecInfo.CodecProfileLevel as P
import android.view.Display.HdrCapabilities as Hdr
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class DecoderCollectorTest {
    private class FakeSource(
        private val decoders: List<VideoDecoder>,
        private val hdrTypes: List<Int> = emptyList(),
    ) : DecoderSource {
        override fun decoders() = decoders

        override fun displayHdrTypes() = hdrTypes
    }

    private fun collect(vararg decoders: VideoDecoder, hdr: List<Int> = emptyList()) =
        DecoderCollector.collect(FakeSource(decoders.toList(), hdr))

    @Test
    fun `names the HEVC profiles including Main10 HDR10 and HDR10 plus`() {
        val report =
            collect(
                VideoDecoder(
                    "c2.acme.hevc.decoder",
                    "video/hevc",
                    hardware = true,
                    profiles =
                        listOf(
                            P.HEVCProfileMain,
                            P.HEVCProfileMain10,
                            P.HEVCProfileMain10HDR10,
                            P.HEVCProfileMain10HDR10Plus,
                        ),
                )
            )

        assertThat(report.decoders)
            .containsExactly(
                DecoderEntry(
                    "c2.acme.hevc.decoder",
                    "video/hevc",
                    true,
                    listOf("Main", "Main10", "Main10 HDR10", "Main10 HDR10+"),
                )
            )
    }

    @Test
    fun `names the AV1 and H264 profiles`() {
        val report =
            collect(
                VideoDecoder(
                    "c2.android.av1.decoder",
                    "video/av01",
                    hardware = false,
                    profiles =
                        listOf(
                            P.AV1ProfileMain8,
                            P.AV1ProfileMain10,
                            P.AV1ProfileMain10HDR10,
                            P.AV1ProfileMain10HDR10Plus,
                        ),
                ),
                VideoDecoder(
                    "c2.acme.avc.decoder",
                    "video/avc",
                    hardware = true,
                    profiles =
                        listOf(
                            P.AVCProfileBaseline,
                            P.AVCProfileMain,
                            P.AVCProfileHigh,
                            P.AVCProfileHigh10,
                            P.AVCProfileConstrainedHigh,
                        ),
                ),
            )

        assertThat(report.decoders.map { it.profiles })
            .containsExactly(
                listOf("Baseline", "Main", "High", "High10", "Constrained High"),
                listOf("Main8", "Main10", "Main10 HDR10", "Main10 HDR10+"),
            )
            .inOrder()
    }

    @Test
    fun `an unknown profile is shown by its number and repeats are dropped`() {
        val report =
            collect(
                VideoDecoder(
                    "x",
                    "video/hevc",
                    true,
                    listOf(P.HEVCProfileMain, P.HEVCProfileMain, 0x7000),
                )
            )

        assertThat(report.decoders.single().profiles).containsExactly("Main", "profile-28672").inOrder()
    }

    @Test
    fun `only H264, HEVC and AV1 decoders are kept, ordered by codec then hardware then name`() {
        val report =
            collect(
                VideoDecoder("z.vp9", "video/x-vnd.on2.vp9", true, listOf(1)),
                VideoDecoder("b.sw.hevc", "video/hevc", false, listOf(P.HEVCProfileMain)),
                VideoDecoder("b.hw.av1", "video/av01", true, listOf(P.AV1ProfileMain8)),
                VideoDecoder("c.hw.hevc", "video/hevc", true, listOf(P.HEVCProfileMain)),
                VideoDecoder("a.hw.hevc", "video/hevc", true, listOf(P.HEVCProfileMain)),
                VideoDecoder("m.hw.avc", "video/avc", true, listOf(P.AVCProfileHigh)),
            )

        assertThat(report.decoders.map { it.name })
            .containsExactly("m.hw.avc", "a.hw.hevc", "c.hw.hevc", "b.sw.hevc", "b.hw.av1")
            .inOrder()
        assertThat(report.decoders.first { it.name == "b.sw.hevc" }.hardware).isFalse()
    }

    @Test
    fun `names the display HDR types`() {
        val report =
            collect(
                hdr =
                    listOf(
                        Hdr.HDR_TYPE_HDR10,
                        Hdr.HDR_TYPE_HLG,
                        Hdr.HDR_TYPE_DOLBY_VISION,
                        Hdr.HDR_TYPE_HDR10_PLUS,
                        99,
                    )
            )

        assertThat(report.displayHdrTypes)
            .containsExactly("HDR10", "HLG", "Dolby Vision", "HDR10+", "type-99")
            .inOrder()
    }

    @Test
    fun `an empty source gives an empty report that still encodes`() {
        val report = collect()

        assertThat(report).isEqualTo(DecodersReport(emptyList(), emptyList()))
        assertThat(Json.encodeToString(report)).contains("\"decoders\"")
    }
}
