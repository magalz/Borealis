package io.github.magalz.borealis.discovery

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.logging.Level
import org.junit.Rule
import org.junit.Test

/**
 * What the base class of the mDNS agents does with the addresses of a host that a subclass
 * resolved: which computers it reports, how it de-duplicates them and how it picks the IPv6
 * address to connect to. A small concrete subclass stands in for the network.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
class MdnsDiscoveryAgentCharacterizationTest {
    @get:Rule val log = LimeLogRecords()

    private val listener = RecordingDiscoveryListener()
    private val agent = ReportingAgent(listener)

    /** A subclass that exposes what a real agent calls when its network resolved a host. */
    private class ReportingAgent(listener: MdnsDiscoveryListener) : MdnsDiscoveryAgent(listener) {
        override fun startDiscovery(discoveryIntervalMs: Int) = Unit

        override fun stopDiscovery() = Unit

        fun report(name: String, port: Int, v4: List<Inet4Address>, v6: List<Inet6Address>) =
            reportNewComputer(name, port, v4.toTypedArray(), v6.toTypedArray())
    }

    private fun report(
        v4: List<Inet4Address> = emptyList(),
        v6: List<Inet6Address> = emptyList(),
        name: String = "Den PC",
        port: Int = 47989,
    ) = agent.report(name, port, v4, v6)

    @Test
    fun `a host with one IPv4 address is reported once, with no IPv6 address`() {
        report(v4 = listOf(ip4("192.168.1.5")))

        assertThat(listener.added)
            .containsExactly(MdnsComputer("Den PC", ip4("192.168.1.5"), null, 47989))
        assertThat(agent.getComputerSet())
            .containsExactly(MdnsComputer("Den PC", ip4("192.168.1.5"), null, 47989))
    }

    @Test
    fun `each IPv4 address of a host is a computer of its own, in the order given`() {
        val global = ip6("2001:db8::1")

        report(v4 = listOf(ip4("192.168.1.5"), ip4("10.0.0.2")), v6 = listOf(global))

        assertThat(listener.added)
            .containsExactly(
                MdnsComputer("Den PC", ip4("192.168.1.5"), global, 47989),
                MdnsComputer("Den PC", ip4("10.0.0.2"), global, 47989),
            )
            .inOrder()
        assertThat(agent.getComputerSet()).hasSize(2)
    }

    @Test
    fun `a host that is reported again is not announced again`() {
        report(v4 = listOf(ip4("192.168.1.5")))
        report(v4 = listOf(ip4("192.168.1.5")))

        assertThat(listener.added).hasSize(1)
        assertThat(agent.getComputerSet()).hasSize(1)
    }

    @Test
    fun `a host that comes back with a new address, port or name is announced again`() {
        report(v4 = listOf(ip4("192.168.1.5")))
        report(v4 = listOf(ip4("192.168.1.6")))
        report(v4 = listOf(ip4("192.168.1.5")), port = 47990)
        report(v4 = listOf(ip4("192.168.1.5")), name = "Other PC")

        assertThat(listener.added).hasSize(4)
        assertThat(agent.getComputerSet()).hasSize(4)
    }

    @Test
    fun `a host that gains a global IPv6 address is announced again`() {
        report(v4 = listOf(ip4("192.168.1.5")))
        report(v4 = listOf(ip4("192.168.1.5")), v6 = listOf(ip6("2001:db8::1")))

        assertThat(listener.added)
            .containsExactly(
                MdnsComputer("Den PC", ip4("192.168.1.5"), null, 47989),
                MdnsComputer("Den PC", ip4("192.168.1.5"), ip6("2001:db8::1"), 47989),
            )
            .inOrder()
    }

    @Test
    fun `a host with an IPv4 address is not reported through its IPv6 addresses`() {
        report(v4 = listOf(ip4("192.168.1.5")), v6 = listOf(ip6("fe80::1"), ip6("2001:db8::1")))

        assertThat(listener.added)
            .containsExactly(MdnsComputer("Den PC", ip4("192.168.1.5"), ip6("2001:db8::1"), 47989))
    }

    @Test
    fun `a host with no IPv4 address is reported with its local and global IPv6 addresses`() {
        report(v6 = listOf(ip6("fe80::1"), ip6("2001:db8::1")))

        assertThat(listener.added)
            .containsExactly(MdnsComputer("Den PC", ip6("fe80::1"), ip6("2001:db8::1"), 47989))
    }

    @Test
    fun `a host with no IPv4 address that is reported again is not announced again`() {
        report(v6 = listOf(ip6("fe80::1"), ip6("2001:db8::1")))
        report(v6 = listOf(ip6("fe80::1"), ip6("2001:db8::1")))

        assertThat(listener.added).hasSize(1)
        assertThat(agent.getComputerSet()).hasSize(1)
    }

    @Test
    fun `a host with only a global IPv6 address has no local address`() {
        report(v6 = listOf(ip6("2001:db8::1")))

        assertThat(listener.added)
            .containsExactly(MdnsComputer("Den PC", null, ip6("2001:db8::1"), 47989))
    }

    @Test
    fun `a host with only a link-local IPv6 address has no global address`() {
        report(v6 = listOf(ip6("fe80::1")))

        assertThat(listener.added)
            .containsExactly(MdnsComputer("Den PC", ip6("fe80::1"), null, 47989))
    }

    @Test
    fun `the local IPv6 address is the first link-local, site-local or unique local one`() {
        report(v6 = listOf(ip6("2001:db8::1"), ip6("fd00::2"), ip6("fe80::3"), ip6("fec0::4")))

        assertThat(listener.added.single().localAddress).isEqualTo(ip6("fd00::2"))
        assertThat(listener.added.single().ipv6Address).isEqualTo(ip6("2001:db8::1"))
    }

    @Test
    fun `a site-local IPv6 address is a local address`() {
        report(v6 = listOf(ip6("fec0::4")))

        assertThat(listener.added.single().localAddress).isEqualTo(ip6("fec0::4"))
        assertThat(listener.added.single().ipv6Address).isNull()
    }

    @Test
    fun `a host with no IPv4 address and no usable IPv6 address is not reported`() {
        report()
        report(v6 = listOf(ip6("::1"), ip6("2002:c000:204::1"), ip6("2001:0:4136:e378::1")))

        assertThat(listener.added).isEmpty()
        assertThat(agent.getComputerSet()).isEmpty()
    }

    @Test
    fun `the global IPv6 address skips the addresses that do not travel well`() {
        report(
            v6 =
                listOf(
                    ip6("fe80::1"),
                    ip6("fec0::2"),
                    ip6("::1"),
                    ip6("2002:c000:204::3"),
                    ip6("2001:0:4136:e378::4"),
                    ip6("fd00::5"),
                    ip6("2001:db8::6"),
                    ip6("2001:db8::7"),
                )
        )

        assertThat(listener.added.single().ipv6Address).isEqualTo(ip6("2001:db8::6"))
    }

    @Test
    fun `an address that begins like Teredo but is not in its block is global`() {
        report(v6 = listOf(ip6("2001:1::1")))

        assertThat(listener.added.single().ipv6Address).isEqualTo(ip6("2001:1::1"))
    }

    @Test
    fun `the global IPv6 address that shares the interface identifier of the link-local one wins`() {
        report(
            v6 =
                listOf(
                    ip6("fe80::211:22ff:fe33:4455"),
                    ip6("2001:db8:1::aaaa:bbbb:cccc:dddd"),
                    ip6("2001:db8:2::211:22ff:fe33:4455"),
                )
        )

        assertThat(listener.added.single().ipv6Address)
            .isEqualTo(ip6("2001:db8:2::211:22ff:fe33:4455"))
    }

    @Test
    fun `a global IPv6 address that shares only half of the interface identifier is not the match`() {
        report(
            v6 =
                listOf(
                    ip6("fe80::1111:2222:3333:4444"),
                    // The low 32 bits are the same as the link-local ones, the high 32 are not.
                    ip6("2001:db8:1::aaaa:bbbb:3333:4444"),
                    // The high 32 bits are the same, the low 32 are not.
                    ip6("2001:db8:2::1111:2222:cccc:dddd"),
                    ip6("2001:db8:3::1111:2222:3333:4444"),
                )
        )

        assertThat(listener.added.single().ipv6Address)
            .isEqualTo(ip6("2001:db8:3::1111:2222:3333:4444"))
    }

    @Test
    fun `the first global IPv6 address wins when none shares the interface identifier`() {
        report(
            v6 =
                listOf(
                    ip6("fe80::211:22ff:fe33:4455"),
                    ip6("2001:db8:1::aaaa:bbbb:cccc:dddd"),
                    ip6("2001:db8:2::1111:2222:3333:4444"),
                )
        )

        assertThat(listener.added.single().ipv6Address)
            .isEqualTo(ip6("2001:db8:1::aaaa:bbbb:cccc:dddd"))
    }

    @Test
    fun `the first global IPv6 address wins when there is no link-local one to match`() {
        report(v6 = listOf(ip6("2001:db8:1::1"), ip6("2001:db8:2::2")))

        assertThat(listener.added.single().ipv6Address).isEqualTo(ip6("2001:db8:1::1"))
    }

    @Test
    fun `the computer set is a copy that the caller may change`() {
        report(v4 = listOf(ip4("192.168.1.5")))

        val snapshot = agent.getComputerSet()
        (snapshot as MutableList<MdnsComputer>).clear()

        assertThat(agent.getComputerSet()).hasSize(1)
    }

    @Test
    fun `the computer set of an agent that found nothing is empty`() {
        assertThat(agent.getComputerSet()).isEmpty()
    }

    @Test
    fun `the number of addresses of a host is logged at info level`() {
        report(v4 = listOf(ip4("192.168.1.5"), ip4("10.0.0.2")), v6 = listOf(ip6("2001:db8::1")))

        assertThat(log.messages(Level.INFO))
            .containsAtLeast(
                "mDNS: Den PC has 2 IPv4 addresses",
                "mDNS: Den PC has 1 IPv6 addresses",
            )
            .inOrder()
    }

    @Test
    fun `each IPv6 address that is not chosen is logged with the reason`() {
        report(
            v6 =
                listOf(
                    ip6("fe80::1"),
                    ip6("fec0::2"),
                    ip6("::1"),
                    ip6("2002:c000:204::3"),
                    ip6("2001:0:4136:e378::4"),
                    ip6("fd00::5"),
                    ip6("2001:db8::6"),
                )
        )

        assertThat(log.messages(Level.INFO))
            .containsAtLeast(
                "Found link-local address: fe80:0:0:0:0:0:0:1",
                "Ignoring non-global address: fe80:0:0:0:0:0:0:1",
                "Ignoring non-global address: fec0:0:0:0:0:0:0:2",
                "Ignoring non-global address: 0:0:0:0:0:0:0:1",
                "Ignoring 6to4 address: 2002:c000:204:0:0:0:0:3",
                "Ignoring Teredo address: 2001:0:4136:e378:0:0:0:4",
                "Ignoring ULA: fd00:0:0:0:0:0:0:5",
                "Ignoring non-matching global address: 2001:db8:0:0:0:0:0:6",
            )
    }

    @Test
    fun `a failure of the listener reaches the caller of the report`() {
        val failing =
            object : MdnsDiscoveryListener {
                override fun notifyComputerAdded(computer: MdnsComputer) {
                    error("listener failed")
                }

                override fun notifyDiscoveryFailure(e: Exception) = Unit
            }

        val error =
            runCatching {
                    ReportingAgent(failing).report("Den PC", 1, listOf(ip4("192.168.1.5")), emptyList())
                }
                .exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().isEqualTo("listener failed")
    }
}
