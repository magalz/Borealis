package io.github.magalz.borealis.host.caps

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostFamily
import io.github.magalz.borealis.nativecore.PyroWaveProfile
import java.io.File
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
        return { id, args -> byId.getValue(id).format(*args.toTypedArray()) }
    }

    private val english = strings("values")

    private fun caps(
        family: HostFamily = HostFamily.SUNSHINE,
        version: List<Int> = listOf(7, 1, 431, -1),
        hevc: Boolean = false,
        hevcMain10: Boolean = false,
        av1: Boolean = false,
        av1Main10: Boolean = false,
        yuv444: Boolean = false,
        pyroWave: Set<PyroWaveProfile> = emptySet(),
    ) = HostCaps(family, version, hevc, hevcMain10, av1, av1Main10, yuv444, pyroWave)

    private fun info(caps: HostCaps) = BorealisHostInfo(caps, httpsPort = 47984, httpPort = 47989)

    private fun block(caps: HostCaps, getString: (Int, List<Any>) -> String = english) =
        HostInfoText.appendTo(getString, "", info(caps)).removePrefix("\n")

    @Test
    fun `a LuminalShine host shows its software, codecs, PyroWave profiles and ports`() {
        val text =
            block(
                caps(
                    hevc = true,
                    hevcMain10 = true,
                    av1 = true,
                    yuv444 = true,
                    pyroWave = setOf(PyroWaveProfile.HDR_420, PyroWaveProfile.SDR_420),
                )
            )

        assertThat(text)
            .isEqualTo(
                """
                Host capabilities
                Host software: Sunshine-based (LuminalShine), version 7.1.431.-1
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
            block(caps(hevc = true, hevcMain10 = true, av1 = true, av1Main10 = true, yuv444 = true))

        assertThat(text).contains("Video: HEVC, HEVC 10-bit, AV1, AV1 10-bit, 4:4:4")
    }

    @Test
    fun `a host without HEVC or AV1 is H264 only`() {
        assertThat(block(caps())).contains("Video: H.264 only")
    }

    @Test
    fun `a host without PyroWave says it is off`() {
        assertThat(block(caps())).contains("PyroWave: off")
    }

    @Test
    fun `every PyroWave profile has its own label in a fixed order`() {
        val text = block(caps(pyroWave = PyroWaveProfile.entries.toSet()))

        assertThat(text).contains("PyroWave: SDR 4:2:0, SDR 4:4:4, HDR 4:2:0, HDR 4:4:4")
    }

    @Test
    fun `a GeForce Experience host shows its version`() {
        val text = block(caps(family = HostFamily.GFE, version = listOf(3, 28, 0, 677)))

        assertThat(text).contains("Host software: GeForce Experience, version 3.28.0.677")
    }

    @Test
    fun `an unknown host shows no version`() {
        val text = block(caps(family = HostFamily.UNKNOWN, version = listOf(0, 0, 0, 0)))

        assertThat(text).contains("Host software: Unknown")
        assertThat(text).doesNotContain("version")
    }

    @Test
    fun `the block follows the details text after a blank line`() {
        val text = HostInfoText.appendTo(english, "Name: PC\nUUID: 1\n", info(caps()))

        assertThat(text).startsWith("Name: PC\nUUID: 1\n\nHost capabilities\n")
    }

    @Test
    fun `without host info the details text is unchanged`() {
        assertThat(HostInfoText.appendTo(english, "Name: PC\n", null)).isEqualTo("Name: PC\n")
    }

    @Test
    fun `ports are plain digits without a locale grouping separator`() {
        val text = block(caps(), strings("values-pt-rBR"))

        assertThat(text).contains("Portas: HTTP 47989, HTTPS 47984")
    }

    @Test
    fun `the Brazilian Portuguese block uses the translated labels`() {
        val text = block(caps(hevc = true, hevcMain10 = true), strings("values-pt-rBR"))

        assertThat(text)
            .isEqualTo(
                """
                Capacidades do host
                Software do host: Baseado em Sunshine (LuminalShine), versão 7.1.431.-1
                Vídeo: HEVC, HEVC 10 bits
                PyroWave: desativado
                Portas: HTTP 47989, HTTPS 47984
                """
                    .trimIndent()
            )
    }

    @Test
    fun `English and Brazilian Portuguese define the same host info strings`() {
        val names = { folder: String ->
            val nodes =
                DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(File("src/main/res/$folder/strings_launch.xml"))
                    .getElementsByTagName("string")
            (0 until nodes.length)
                .map { (nodes.item(it) as Element).getAttribute("name") }
                .filter { it.startsWith("host_info_") }
                .toSet()
        }

        assertThat(names("values-pt-rBR")).isEqualTo(names("values"))
        assertThat(names("values")).isNotEmpty()
    }
}
