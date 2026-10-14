package io.github.magalz.borealis.host.caps

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.Fixtures
import io.github.magalz.borealis.host.launch.FakeHostRules
import io.github.magalz.borealis.host.launch.RecordingLogSink
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostFamily
import io.github.magalz.borealis.nativecore.PyroWaveProfile
import org.junit.Rule
import org.junit.Test

class HostInfoReaderTest {
    @get:Rule val log = RecordingLogSink()

    private val fixture = Fixtures.text("luminalshine/26.09.1/serverinfo-http.xml")

    private fun tag(name: String): String? =
        Regex("<$name>([^<]*)</$name>").find(fixture)?.groupValues?.get(1)

    private val luminalShine =
        HostCaps(
            family = HostFamily.SUNSHINE,
            version = listOf(7, 1, 431, -1),
            hevc = true,
            hevcMain10 = true,
            av1 = true,
            av1Main10 = false,
            yuv444 = false,
            pyroWaveProfiles = setOf(PyroWaveProfile.SDR_420),
        )

    /** Answers [luminalShine] for exactly the fixture values and null for anything else. */
    private fun fixtureRules() =
        FakeHostRules(
            caps = { appVersion, mask ->
                if (appVersion == "7.1.431.-1" && mask == 0x001C0301) luminalShine else null
            }
        )

    @Test
    fun `the LuminalShine fixture gives the host info with both ports`() {
        val reader = HostInfoReader(fixtureRules())

        val info =
            reader.read("host-1", tag("appversion"), tag("ServerCodecModeSupport"), 47984, 47989)

        assertThat(info)
            .isEqualTo(BorealisHostInfo(luminalShine, httpsPort = 47984, httpPort = 47989))
    }

    @Test
    fun `the rules receive the appversion text and the mask as its raw 32-bit pattern`() {
        val rules = fixtureRules()

        HostInfoReader(rules)
            .read("host-1", tag("appversion"), tag("ServerCodecModeSupport"), 47984, 47989)
        HostInfoReader(rules).read("host-1", null, "2147483648", 1, 2)
        HostInfoReader(rules).read("host-1", "1.2.3.4", "4294967295", 1, 2)

        assertThat(rules.parseCalls)
            .containsExactly(
                "7.1.431.-1" to 0x001C0301,
                null to Int.MIN_VALUE,
                "1.2.3.4" to -1,
            )
            .inOrder()
    }

    @Test
    fun `a missing or unreadable codec mask counts as no codecs`() {
        val rules = FakeHostRules(caps = { _, _ -> luminalShine })
        val reader = HostInfoReader(rules)

        reader.read("host-1", "7.1.431.-1", null, 1, 2)
        reader.read("host-1", "7.1.431.-1", "", 1, 2)
        reader.read("host-1", "7.1.431.-1", "not a number", 1, 2)

        assertThat(rules.parseCalls.map { it.second }).containsExactly(0, 0, 0)
    }

    @Test
    fun `without the native library there is no host info`() {
        val unavailable = FakeHostRules()

        assertThat(HostInfoReader(unavailable).read("host-1", "7.1.431.-1", "1", 1, 2)).isNull()
    }

    @Test
    fun `the shared reader gives no host info when the native library cannot load`() {
        assertThat(HostInfoReader.SHARED.read("host-1", "7.1.431.-1", "1", 1, 2)).isNull()
    }

    @Test
    fun `polling the same host again does not log again`() {
        val reader = HostInfoReader(fixtureRules())

        repeat(5) { reader.read("host-1", "7.1.431.-1", "1835777", 47984, 47989) }

        val records = log.records.filter { it.tag == LogTag.HOST }
        assertThat(records).hasSize(1)
        assertThat(records.single().level).isEqualTo(LogLevel.INFO)
        assertThat(records.single().fields)
            .containsAtLeast("family" to "SUNSHINE", "version" to "7.1.431.-1", "hevc" to "true")
    }

    @Test
    fun `a change in the capabilities of a host is logged once more`() {
        val reader =
            HostInfoReader(
                FakeHostRules(
                    caps = { _, mask ->
                        luminalShine.copy(av1 = mask == 1)
                    }
                )
            )

        reader.read("host-1", "7.1.431.-1", "1", 47984, 47989)
        reader.read("host-1", "7.1.431.-1", "1", 47984, 47989)
        reader.read("host-1", "7.1.431.-1", "2", 47984, 47989)
        reader.read("host-1", "7.1.431.-1", "2", 47984, 47989)

        assertThat(log.records.count { it.tag == LogTag.HOST }).isEqualTo(2)
    }

    @Test
    fun `each host is logged once`() {
        val reader = HostInfoReader(FakeHostRules(caps = { _, _ -> luminalShine }))

        reader.read("host-1", "7.1.431.-1", "1", 47984, 47989)
        reader.read("host-2", "7.1.431.-1", "1", 47984, 47989)
        reader.read("host-1", "7.1.431.-1", "1", 47984, 47989)

        assertThat(log.records.count { it.tag == LogTag.HOST }).isEqualTo(2)
    }

    @Test
    fun `a host without an id is logged once as well`() {
        val reader = HostInfoReader(FakeHostRules(caps = { _, _ -> luminalShine }))

        repeat(3) { reader.read(null, "7.1.431.-1", "1", 47984, 47989) }

        assertThat(log.records.count { it.tag == LogTag.HOST }).isEqualTo(1)
    }

    @Test
    fun `rejected input never logs anything per poll`() {
        val reader = HostInfoReader(FakeHostRules())

        repeat(5) { reader.read("host-1", "x".repeat(65), "1", 1, 2) }

        assertThat(log.records.filter { it.tag == LogTag.HOST }).isEmpty()
    }
}
