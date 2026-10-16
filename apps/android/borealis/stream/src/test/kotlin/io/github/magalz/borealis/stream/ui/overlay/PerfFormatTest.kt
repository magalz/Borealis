package io.github.magalz.borealis.stream.ui.overlay

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.LatencyRange
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.aSessionInfo
import java.util.Locale
import org.junit.Test

class PerfFormatTest {
    private val info = aSessionInfo
    private val hdr = aSessionInfo.copy(codec = "HEVC", hdr = true)

    @Test
    fun `the codec label adds the HDR format`() {
        assertThat(PerfFormat.codec(info)).isEqualTo("HEVC")
        assertThat(PerfFormat.codec(hdr)).isEqualTo("HEVC 10-bit HDR")
    }

    @Test
    fun `the video line gives the size and the frame rate`() {
        assertThat(PerfFormat.video(info)).isEqualTo("1920×1080 · 60 fps")
    }

    @Test
    fun `the compact line has the fps, the host time, the network time and the codec`() {
        assertThat(PerfFormat.compact(aPerf(totalFps = 59f), hdr))
            .isEqualTo("59 fps · host 2.0 ms · net 12 ms · HEVC 10-bit HDR")
    }

    @Test
    fun `the compact line shows a dash for every figure when there is no reading`() {
        assertThat(PerfFormat.compact(null, info)).isEqualTo("— fps · host — ms · net — ms · HEVC")
    }

    @Test
    fun `the compact line shows a dash for the host time when the host reports none`() {
        val perf = aPerf().copy(hostLatency = null)

        assertThat(PerfFormat.compact(perf, info))
            .isEqualTo("60 fps · host — ms · net 12 ms · HEVC")
    }

    @Test
    fun `fps rounds to a whole number and times to one decimal`() {
        val perf =
            aPerf(totalFps = 59.6f)
                .copy(hostLatency = LatencyRange(min = 0.96f, avg = 4.04f, max = 6.25f))

        assertThat(PerfFormat.compact(perf, info)).startsWith("60 fps · host 4.0 ms")
        assertThat(PerfFormat.hostProcessing(perf)).isEqualTo("1.0 / 4.0 / 6.3 ms")
    }

    @Test
    fun `the detailed overlay lists the video, the decoder, the host, the network and the decode`() {
        assertThat(PerfFormat.detailed(aPerf(), hdr))
            .containsExactly(
                "1920×1080 · 60 fps · HEVC 10-bit HDR",
                "Decoder c2.qti.hevc.decoder",
                "Host processing 1.0 / 2.0 / 5.0 ms (min/avg/max)",
                "Network 12 ms ± 3 ms · loss 0.5 % · 18.5 Mbps",
                "Decode 4.0 ms",
            )
            .inOrder()
    }

    @Test
    fun `the detailed overlay leaves out the decoder when it is not known`() {
        val lines = PerfFormat.detailed(aPerf(), info.copy(decoder = null))

        assertThat(lines.none { it.startsWith("Decoder") }).isTrue()
        assertThat(lines).hasSize(4)
    }

    @Test
    fun `the detailed overlay leaves out the bitrate when it is not known`() {
        val lines = PerfFormat.detailed(aPerf().copy(bitrateMbps = null), info)

        assertThat(lines).contains("Network 12 ms ± 3 ms · loss 0.5 %")
    }

    @Test
    fun `the detailed overlay shows dashes for the figures when there is no reading`() {
        assertThat(PerfFormat.detailed(null, info))
            .containsExactly(
                "1920×1080 · 60 fps · HEVC",
                "Decoder c2.qti.hevc.decoder",
                "Host processing — ms (min/avg/max)",
                "Network — ms",
                "Decode — ms",
            )
            .inOrder()
    }

    @Test
    fun `the network figure gives the round trip with its variance and the loss`() {
        assertThat(PerfFormat.network(aPerf())).isEqualTo("12 ms ± 3 ms · loss 0.5 %")
        assertThat(PerfFormat.network(null)).isEqualTo("— ms")
    }

    @Test
    fun `the bitrate is shown against the limit without a trailing zero`() {
        assertThat(PerfFormat.bitrate(aPerf().copy(bitrateMbps = 18.24f), info))
            .isEqualTo("18.2 of 20 Mbps")
        assertThat(
                PerfFormat.bitrate(
                    aPerf().copy(bitrateMbps = 5f),
                    info.copy(bitrateLimitKbps = 12_500),
                )
            )
            .isEqualTo("5.0 of 12.5 Mbps")
    }

    @Test
    fun `the bitrate shows a dash when it is not known`() {
        assertThat(PerfFormat.bitrate(aPerf().copy(bitrateMbps = null), info))
            .isEqualTo("— of 20 Mbps")
        assertThat(PerfFormat.bitrate(null, info)).isEqualTo("— of 20 Mbps")
    }

    @Test
    fun `numbers use a decimal point whatever the locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertThat(PerfFormat.hostProcessing(aPerf())).isEqualTo("1.0 / 2.0 / 5.0 ms")
        } finally {
            Locale.setDefault(previous)
        }
    }
}
