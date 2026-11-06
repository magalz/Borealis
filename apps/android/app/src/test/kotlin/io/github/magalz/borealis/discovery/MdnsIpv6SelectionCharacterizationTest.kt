package io.github.magalz.borealis.discovery

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.mdns.MdnsDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import io.github.magalz.borealis.testing.LimeLogCapture
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.logging.Level
import org.junit.Rule
import org.junit.Test

/**
 * How the base class of the mDNS agents picks the global IPv6 address of a host (Sprint 18a,
 * BORE-79), beyond [MdnsDiscoveryAgentCharacterizationTest]: the exact INFO lines of the two tries,
 * the byte tests of each kind of address that is skipped, and which link-local address and which
 * match count.
 */
class MdnsIpv6SelectionCharacterizationTest {
    @get:Rule val log = LimeLogCapture()

    private val listener = RecordingDiscoveryListener()
    private val agent = ReportingAgent(listener)

    private class ReportingAgent(listener: MdnsDiscoveryListener) : MdnsDiscoveryAgent(listener) {
        override fun startDiscovery(discoveryIntervalMs: Int) = Unit

        override fun stopDiscovery() = Unit

        fun report(v4: List<Inet4Address>, v6: List<Inet6Address>) =
            reportNewComputer("Den PC", 47989, v4.toTypedArray(), v6.toTypedArray())
    }

    /** The lines of the report: the two counts, then what the choice of the IPv6 address logs. */
    private fun infoLines(vararg v6: String): List<String> {
        agent.report(emptyList(), v6.map { ip6(it) })
        return log.messages(Level.INFO)
    }

    /** The global address that a host with an IPv4 address and these IPv6 ones is reported with. */
    private fun globalOf(vararg v6: String): Inet6Address? {
        val reported = RecordingDiscoveryListener()
        ReportingAgent(reported).report(listOf(ip4("192.168.1.5")), v6.map { ip6(it) })
        return reported.added.single().ipv6Address
    }

    private fun host(text: String): String = ip6(text).hostAddress.orEmpty()

    @Test
    fun `a first try that finds no match logs every skipped address again in the second try`() {
        val lines = infoLines("fe80::1", "fd00::5", "2001:db8::6")

        assertThat(lines)
            .containsExactly(
                "mDNS: Den PC has 0 IPv4 addresses",
                "mDNS: Den PC has 3 IPv6 addresses",
                "Found link-local address: ${host("fe80::1")}",
                "Ignoring non-global address: ${host("fe80::1")}",
                "Ignoring ULA: ${host("fd00::5")}",
                "Ignoring non-matching global address: ${host("2001:db8::6")}",
                "Ignoring non-global address: ${host("fe80::1")}",
                "Ignoring ULA: ${host("fd00::5")}",
            )
            .inOrder()
        assertThat(agent.getComputerSet().single().ipv6Address).isEqualTo(ip6("2001:db8::6"))
    }

    @Test
    fun `without a link-local address the first try returns the first global one and logs each skip once`() {
        val lines =
            infoLines("::1", "2002:c000:204::3", "2001:0:4136:e378::4", "fd00::5", "2001:db8::6")

        assertThat(lines.drop(2))
            .containsExactly(
                "Ignoring non-global address: ${host("::1")}",
                "Ignoring 6to4 address: ${host("2002:c000:204::3")}",
                "Ignoring Teredo address: ${host("2001:0:4136:e378::4")}",
                "Ignoring ULA: ${host("fd00::5")}",
            )
            .inOrder()
        assertThat(agent.getComputerSet().single().ipv6Address).isEqualTo(ip6("2001:db8::6"))
    }

    @Test
    fun `when nothing is usable both tries log every skip and no global address is chosen`() {
        val lines = infoLines("::1", "2002:c000:204::3", "2001:0:4136:e378::4")

        val skipped =
            listOf(
                "Ignoring non-global address: ${host("::1")}",
                "Ignoring 6to4 address: ${host("2002:c000:204::3")}",
                "Ignoring Teredo address: ${host("2001:0:4136:e378::4")}",
            )
        assertThat(lines.drop(2)).containsExactlyElementsIn(skipped + skipped).inOrder()
        assertThat(agent.getComputerSet()).isEmpty()
    }

    @Test
    fun `a global address that shares the interface identifier is chosen in the first try`() {
        val lines =
            infoLines(
                "fe80::211:22ff:fe33:4455",
                "2001:db8:1::aaaa:bbbb:cccc:dddd",
                "2001:db8:2::211:22ff:fe33:4455",
            )

        assertThat(lines.drop(2))
            .containsExactly(
                "Found link-local address: ${host("fe80::211:22ff:fe33:4455")}",
                "Ignoring non-global address: ${host("fe80::211:22ff:fe33:4455")}",
                "Ignoring non-matching global address: ${host("2001:db8:1::aaaa:bbbb:cccc:dddd")}",
            )
            .inOrder()
        assertThat(agent.getComputerSet().single().ipv6Address)
            .isEqualTo(ip6("2001:db8:2::211:22ff:fe33:4455"))
    }

    @Test
    fun `only the first link-local address gives the interface identifier to match`() {
        val lines = infoLines("fe80::1111", "fe80::2222", "2001:db8:1::3333", "2001:db8:2::2222")

        // The second link-local address is not looked at: no global address matches the first.
        assertThat(lines.filter { it.startsWith("Found link-local") })
            .containsExactly("Found link-local address: ${host("fe80::1111")}")
        assertThat(agent.getComputerSet().single().ipv6Address).isEqualTo(ip6("2001:db8:1::3333"))
    }

    @Test
    fun `the first of two global addresses that share the interface identifier wins`() {
        assertThat(
                globalOf(
                    "fe80::211:22ff:fe33:4455",
                    "2001:db8:1::211:22ff:fe33:4455",
                    "2001:db8:2::211:22ff:fe33:4455",
                )
            )
            .isEqualTo(ip6("2001:db8:1::211:22ff:fe33:4455"))
    }

    @Test
    fun `the unique local block is fc00 slash 7 and the blocks beside it are global`() {
        assertThat(globalOf("fc00::1", "fd00::1", "fe00::1")).isEqualTo(ip6("fe00::1"))
        assertThat(globalOf("fc00::1", "fd00::1", "fb00::1")).isEqualTo(ip6("fb00::1"))
        assertThat(globalOf("fc00::1")).isNull()
        assertThat(globalOf("fd00::1")).isNull()
    }

    @Test
    fun `an address that begins like 6to4 or Teredo but is not in its block is global`() {
        // 2002::/16 needs the second byte 0x02; 2001::/32 needs the third and fourth bytes zero.
        assertThat(globalOf("2003::1")).isEqualTo(ip6("2003::1"))
        assertThat(globalOf("2001:100::1")).isEqualTo(ip6("2001:100::1"))
        assertThat(globalOf("2001:1::1")).isEqualTo(ip6("2001:1::1"))
        assertThat(globalOf("2001:0:1::1")).isNull()
        assertThat(globalOf("2002::1")).isNull()
    }
}
