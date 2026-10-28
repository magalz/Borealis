package io.github.magalz.borealis.host.caps

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostFamily
import io.github.magalz.borealis.nativecore.PyroWaveProfile
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

class HostInfoTextTest {
    /**
     * The real strings of `res/<folder>/strings_launch.xml`, formatted like `Context.getString`.
     */
    private fun strings(folder: String): (Int, List<Any>) -> String {
        val nodes =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(File("src/main/res/$folder/strings_launch.xml"))
                .getElementsByTagName("string")
        val byId =
            (0 until nodes.length).associate {
                val element = nodes.item(it) as Element
                R.string::class.java.getField(element.getAttribute("name")).getInt(null) to
                    element.textContent
            }
        return { id, args -> byId.getValue(id).format(Locale.ROOT, *args.toTypedArray()) }
    }

    private val english = strings("values")

    /** A Sunshine host that offers H.264 only, which is what every host offers. */
    private val sunshine =
        HostCaps(
            family = HostFamily.SUNSHINE,
            version = listOf(7, 1, 431, -1),
            hevc = false,
            hevcMain10 = false,
            av1 = false,
            av1Main10 = false,
            yuv444 = false,
            pyroWaveProfiles = emptySet(),
        )

    private fun info(caps: HostCaps) = BorealisHostInfo(caps, httpsPort = 47984, httpPort = 47989)

    private fun block(caps: HostCaps, getString: (Int, List<Any>) -> String = english) =
        HostInfoText.appendTo(getString, "", info(caps)).removePrefix("\n")

    @Test
    fun `a LuminalShine host shows its software, codecs, PyroWave profiles and ports`() {
        val text =
            block(
                sunshine.copy(
                    hevc = true,
                    hevcMain10 = true,
                    av1 = true,
                    yuv444 = true,
                    pyroWaveProfiles = setOf(PyroWaveProfile.HDR_420, PyroWaveProfile.SDR_420),
                )
            )

        assertThat(text)
            .isEqualTo(
                """
                Host capabilities
                Host software: Sunshine-compatible, version 7.1.431.-1
                Video: HEVC, HEVC 10-bit, AV1, 4:4:4
                PyroWave: SDR 4:2:0, HDR 4:2:0
                Ports: HTTP 47989, HTTPS 47984
                """
                    .trimIndent()
            )
    }

    @Test
    fun `every codec flag has its own label`() {
        val text =
            block(
                sunshine.copy(
                    hevc = true,
                    hevcMain10 = true,
                    av1 = true,
                    av1Main10 = true,
                    yuv444 = true,
                )
            )

        assertThat(text).contains("Video: HEVC, HEVC 10-bit, AV1, AV1 10-bit, 4:4:4")
    }

    @Test
    fun `a host without HEVC or AV1 is H264 only`() {
        assertThat(block(sunshine)).contains("Video: H.264 only")
    }

    @Test
    fun `a host without PyroWave says it is off`() {
        assertThat(block(sunshine)).contains("PyroWave: off")
    }

    @Test
    fun `every PyroWave profile has its own label in a fixed order`() {
        val text = block(sunshine.copy(pyroWaveProfiles = PyroWaveProfile.entries.toSet()))

        assertThat(text).contains("PyroWave: SDR 4:2:0, SDR 4:4:4, HDR 4:2:0, HDR 4:4:4")
    }

    @Test
    fun `a GeForce Experience host shows its version`() {
        val text = block(sunshine.copy(family = HostFamily.GFE, version = listOf(3, 28, 0, 677)))

        assertThat(text).contains("Host software: GeForce Experience, version 3.28.0.677")
    }

    @Test
    fun `an unknown host shows no version`() {
        val text = block(sunshine.copy(family = HostFamily.UNKNOWN, version = listOf(0, 0, 0, 0)))

        assertThat(text).contains("Host software: Unknown")
        assertThat(text).doesNotContain("version")
    }

    @Test
    fun `the block follows the details text after a blank line`() {
        val text = HostInfoText.appendTo(english, "Name: PC\nUUID: 1\n", info(sunshine))

        assertThat(text).startsWith("Name: PC\nUUID: 1\n\nHost capabilities\n")
    }

    @Test
    fun `without host info the details text is unchanged`() {
        assertThat(HostInfoText.appendTo(english, "Name: PC\n", null)).isEqualTo("Name: PC\n")
    }
}
