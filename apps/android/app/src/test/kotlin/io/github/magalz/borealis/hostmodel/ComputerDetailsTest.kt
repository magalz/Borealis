package io.github.magalz.borealis.hostmodel

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.host.caps.BorealisHostInfo
import io.github.magalz.borealis.library.testCertificate
import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostFamily
import org.junit.Test

/** Characterizes the host details of the host model as they were before the Kotlin conversion. */
class ComputerDetailsTest {
    private fun tuple(address: String, port: Int = 47989) = AddressTuple(address, port)

    private fun hostInfo(httpsPort: Int = 47984) =
        BorealisHostInfo(
            HostCaps(
                HostFamily.SUNSHINE,
                listOf(7, 1, 431, -1),
                true,
                false,
                false,
                false,
                false,
                emptySet(),
            ),
            httpsPort,
            47989,
        )

    /** Details with every field set, to see what a copy carries over. */
    private fun full(): ComputerDetails =
        ComputerDetails().also {
            it.uuid = "uuid-1"
            it.name = "Den PC"
            it.localAddress = tuple("192.168.1.5", 47989)
            it.remoteAddress = tuple("203.0.113.9", 47990)
            it.manualAddress = tuple("pc.local", 47991)
            it.ipv6Address = tuple("fe80::1", 47992)
            it.macAddress = "AA:BB:CC:DD:EE:FF"
            it.serverCert = testCertificate()
            it.state = ComputerDetails.State.ONLINE
            it.activeAddress = tuple("192.168.1.5", 47993)
            it.httpsPort = 47984
            it.externalPort = 47999
            it.pairState = PairState.PAIRED
            it.runningGameId = 7
            it.rawAppList = "<apps/>"
            it.nvidiaServer = true
            it.borealis = hostInfo()
        }

    @Test
    fun `new details are unknown and have nothing else`() {
        val details = ComputerDetails()

        assertThat(details.state).isEqualTo(ComputerDetails.State.UNKNOWN)
        assertThat(details.uuid).isNull()
        assertThat(details.name).isNull()
        assertThat(details.localAddress).isNull()
        assertThat(details.remoteAddress).isNull()
        assertThat(details.manualAddress).isNull()
        assertThat(details.ipv6Address).isNull()
        assertThat(details.macAddress).isNull()
        assertThat(details.serverCert).isNull()
        assertThat(details.activeAddress).isNull()
        assertThat(details.httpsPort).isEqualTo(0)
        assertThat(details.externalPort).isEqualTo(0)
        assertThat(details.pairState).isNull()
        assertThat(details.runningGameId).isEqualTo(0)
        assertThat(details.rawAppList).isNull()
        assertThat(details.nvidiaServer).isFalse()
        assertThat(details.borealis).isNull()
    }

    @Test
    fun `the states are in the order online, offline, unknown`() {
        assertThat(ComputerDetails.State.values().toList())
            .containsExactly(
                ComputerDetails.State.ONLINE,
                ComputerDetails.State.OFFLINE,
                ComputerDetails.State.UNKNOWN,
            )
            .inOrder()
        assertThat(ComputerDetails.State.valueOf("OFFLINE"))
            .isEqualTo(ComputerDetails.State.OFFLINE)
    }

    @Test
    fun `the copy constructor carries every field over`() {
        val source = full()

        val copy = ComputerDetails(source)

        assertThat(copy.uuid).isEqualTo("uuid-1")
        assertThat(copy.name).isEqualTo("Den PC")
        assertThat(copy.localAddress).isEqualTo(tuple("192.168.1.5", 47989))
        assertThat(copy.remoteAddress).isEqualTo(tuple("203.0.113.9", 47990))
        assertThat(copy.manualAddress).isEqualTo(tuple("pc.local", 47991))
        assertThat(copy.ipv6Address).isEqualTo(tuple("fe80::1", 47992))
        assertThat(copy.macAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(copy.serverCert).isSameInstanceAs(source.serverCert)
        assertThat(copy.state).isEqualTo(ComputerDetails.State.ONLINE)
        assertThat(copy.activeAddress).isEqualTo(tuple("192.168.1.5", 47993))
        assertThat(copy.httpsPort).isEqualTo(47984)
        assertThat(copy.externalPort).isEqualTo(47999)
        assertThat(copy.pairState).isEqualTo(PairState.PAIRED)
        assertThat(copy.runningGameId).isEqualTo(7)
        assertThat(copy.rawAppList).isEqualTo("<apps/>")
        assertThat(copy.nvidiaServer).isTrue()
        assertThat(copy.borealis).isEqualTo(hostInfo())
    }

    @Test
    fun `the copy constructor shares the address tuples with the source`() {
        val source = full()

        val copy = ComputerDetails(source)

        assertThat(copy.localAddress).isSameInstanceAs(source.localAddress)
        assertThat(copy.remoteAddress).isSameInstanceAs(source.remoteAddress)
        assertThat(copy.manualAddress).isSameInstanceAs(source.manualAddress)
        assertThat(copy.ipv6Address).isSameInstanceAs(source.ipv6Address)
        assertThat(copy.activeAddress).isSameInstanceAs(source.activeAddress)
        assertThat(copy.borealis).isSameInstanceAs(source.borealis)
    }

    @Test
    fun `the copy constructor takes the state of the source, even a missing one`() {
        val source = full().also { it.state = null }

        assertThat(ComputerDetails(source).state).isNull()
        assertThat(ComputerDetails(ComputerDetails()).state)
            .isEqualTo(ComputerDetails.State.UNKNOWN)
    }

    @Test
    fun `the copy constructor leaves out a loopback local address and a placeholder MAC`() {
        val source =
            full().also {
                it.localAddress = tuple("127.0.0.1")
                it.macAddress = "00:00:00:00:00:00"
            }

        val copy = ComputerDetails(source)

        assertThat(copy.localAddress).isNull()
        assertThat(copy.macAddress).isNull()
    }

    @Test
    fun `the copy constructor starts from empty details, so a missing field stays empty`() {
        val copy = ComputerDetails(ComputerDetails())

        assertThat(copy.uuid).isNull()
        assertThat(copy.activeAddress).isNull()
        assertThat(copy.remoteAddress).isNull()
        assertThat(copy.httpsPort).isEqualTo(0)
        assertThat(copy.externalPort).isEqualTo(0)
        assertThat(copy.pairState).isNull()
        assertThat(copy.rawAppList).isNull()
        assertThat(copy.nvidiaServer).isFalse()
        assertThat(copy.borealis).isNull()
    }

    @Test
    fun `the external port is guessed first from the external port itself`() {
        val details =
            full().also {
                it.externalPort = 1111
                it.remoteAddress = tuple("203.0.113.9", 2222)
            }

        assertThat(details.guessExternalPort()).isEqualTo(1111)
    }

    @Test
    fun `without an external port the remote address comes first`() {
        val details =
            full().also {
                it.externalPort = 0
                it.remoteAddress = tuple("203.0.113.9", 2222)
                it.activeAddress = tuple("192.168.1.5", 3333)
                it.ipv6Address = tuple("fe80::1", 4444)
                it.localAddress = tuple("192.168.1.5", 5555)
            }

        assertThat(details.guessExternalPort()).isEqualTo(2222)
    }

    @Test
    fun `without a remote address the active address comes next`() {
        val details =
            ComputerDetails().also {
                it.activeAddress = tuple("192.168.1.5", 3333)
                it.ipv6Address = tuple("fe80::1", 4444)
                it.localAddress = tuple("192.168.1.5", 5555)
            }

        assertThat(details.guessExternalPort()).isEqualTo(3333)
    }

    @Test
    fun `without an active address the IPv6 address comes next`() {
        val details =
            ComputerDetails().also {
                it.ipv6Address = tuple("fe80::1", 4444)
                it.localAddress = tuple("192.168.1.5", 5555)
            }

        assertThat(details.guessExternalPort()).isEqualTo(4444)
    }

    @Test
    fun `without an IPv6 address the local address comes last`() {
        val details = ComputerDetails().also { it.localAddress = tuple("192.168.1.5", 5555) }

        assertThat(details.guessExternalPort()).isEqualTo(5555)
    }

    @Test
    fun `with no address at all the default HTTP port is guessed`() {
        val details = ComputerDetails().also { it.manualAddress = tuple("pc.local", 6666) }

        assertThat(details.guessExternalPort()).isEqualTo(47989)
        assertThat(NvHTTP.DEFAULT_HTTP_PORT).isEqualTo(47989)
    }

    @Test
    fun `an update takes the name, the uuid and the state, even when they are missing`() {
        val details = full()

        details.update(ComputerDetails().also { it.state = null })

        assertThat(details.name).isNull()
        assertThat(details.uuid).isNull()
        assertThat(details.state).isNull()
    }

    @Test
    fun `an update takes the plain fields, even when they are empty`() {
        val details = full()

        details.update(ComputerDetails())

        assertThat(details.externalPort).isEqualTo(0)
        assertThat(details.httpsPort).isEqualTo(0)
        assertThat(details.pairState).isNull()
        assertThat(details.runningGameId).isEqualTo(0)
        assertThat(details.nvidiaServer).isFalse()
        assertThat(details.borealis).isNull()
        assertThat(details.rawAppList).isNull()
        assertThat(details.state).isEqualTo(ComputerDetails.State.UNKNOWN)
    }

    @Test
    fun `an update keeps the addresses that the new details do not have`() {
        val details = full()
        val active = details.activeAddress
        val local = details.localAddress
        val remote = details.remoteAddress
        val manual = details.manualAddress
        val ipv6 = details.ipv6Address

        details.update(ComputerDetails())

        assertThat(details.activeAddress).isSameInstanceAs(active)
        assertThat(details.localAddress).isSameInstanceAs(local)
        assertThat(details.remoteAddress).isSameInstanceAs(remote)
        assertThat(details.manualAddress).isSameInstanceAs(manual)
        assertThat(details.ipv6Address).isSameInstanceAs(ipv6)
    }

    @Test
    fun `an update replaces the addresses that the new details have`() {
        val details = full()
        val update =
            ComputerDetails().also {
                it.activeAddress = tuple("10.0.0.2", 1)
                it.localAddress = tuple("10.0.0.3", 2)
                it.remoteAddress = tuple("10.0.0.4", 3)
                it.manualAddress = tuple("new.local", 4)
                it.ipv6Address = tuple("fe80::2", 5)
            }

        details.update(update)

        assertThat(details.activeAddress).isSameInstanceAs(update.activeAddress)
        assertThat(details.localAddress).isSameInstanceAs(update.localAddress)
        assertThat(details.remoteAddress).isSameInstanceAs(update.remoteAddress)
        assertThat(details.manualAddress).isSameInstanceAs(update.manualAddress)
        assertThat(details.ipv6Address).isSameInstanceAs(update.ipv6Address)
    }

    @Test
    fun `an update skips a loopback local address`() {
        for (loopback in listOf("127.0.0.1", "127.5.5.5", "127.example.com")) {
            val details = full()
            val before = details.localAddress

            details.update(ComputerDetails().also { it.localAddress = tuple(loopback) })

            assertThat(details.localAddress).isSameInstanceAs(before)
        }
    }

    @Test
    fun `an update takes a local address that only looks like a loopback one`() {
        for (local in listOf("128.0.0.1", "1270.0.0.1", "::1", "10.127.0.1")) {
            val details = full()
            val update = ComputerDetails().also { it.localAddress = tuple(local) }

            details.update(update)

            assertThat(details.localAddress).isSameInstanceAs(update.localAddress)
        }
    }

    @Test
    fun `an update with a loopback local address sets none on details that had none`() {
        val details = ComputerDetails()

        details.update(ComputerDetails().also { it.localAddress = tuple("127.0.0.1") })

        assertThat(details.localAddress).isNull()
    }

    @Test
    fun `an update replaces the remote address and does not touch its port`() {
        val details = full()
        val update =
            ComputerDetails().also {
                it.remoteAddress = tuple("10.0.0.4", 3)
                it.externalPort = 50000
            }

        details.update(update)

        assertThat(details.remoteAddress).isSameInstanceAs(update.remoteAddress)
        assertThat(details.remoteAddress!!.port).isEqualTo(3)
        assertThat(details.externalPort).isEqualTo(50000)
    }

    @Test
    fun `an update without a remote address moves the known remote port to the external port`() {
        val details = full()
        val known = details.remoteAddress

        details.update(ComputerDetails().also { it.externalPort = 50000 })

        assertThat(details.remoteAddress).isSameInstanceAs(known)
        assertThat(known!!.port).isEqualTo(50000)
        assertThat(known.address).isEqualTo("203.0.113.9")
    }

    @Test
    fun `an update without a remote address or an external port leaves the known remote address`() {
        val details = full()
        val known = details.remoteAddress

        details.update(ComputerDetails())

        assertThat(details.remoteAddress).isSameInstanceAs(known)
        assertThat(known!!.port).isEqualTo(47990)
    }

    @Test
    fun `an update with an external port but no known remote address leaves it missing`() {
        val details = ComputerDetails()

        details.update(ComputerDetails().also { it.externalPort = 50000 })

        assertThat(details.remoteAddress).isNull()
        assertThat(details.externalPort).isEqualTo(50000)
    }

    @Test
    fun `an update takes a MAC address, but not a missing one or the placeholder`() {
        val details = full()

        details.update(ComputerDetails().also { it.macAddress = "11:22:33:44:55:66" })
        assertThat(details.macAddress).isEqualTo("11:22:33:44:55:66")

        details.update(ComputerDetails().also { it.macAddress = null })
        assertThat(details.macAddress).isEqualTo("11:22:33:44:55:66")

        details.update(ComputerDetails().also { it.macAddress = "00:00:00:00:00:00" })
        assertThat(details.macAddress).isEqualTo("11:22:33:44:55:66")
    }

    @Test
    fun `an update takes another form of the placeholder MAC address`() {
        val details = full()

        details.update(ComputerDetails().also { it.macAddress = "00-00-00-00-00-00" })

        assertThat(details.macAddress).isEqualTo("00-00-00-00-00-00")
    }

    @Test
    fun `an update with the placeholder MAC address leaves details without a MAC address`() {
        val details = ComputerDetails()

        details.update(ComputerDetails().also { it.macAddress = "00:00:00:00:00:00" })

        assertThat(details.macAddress).isNull()
    }

    @Test
    fun `an update takes a server certificate, but not a missing one`() {
        val details = ComputerDetails()
        val certificate = testCertificate()

        details.update(ComputerDetails().also { it.serverCert = certificate })
        assertThat(details.serverCert).isSameInstanceAs(certificate)

        details.update(ComputerDetails())
        assertThat(details.serverCert).isSameInstanceAs(certificate)
    }

    @Test
    fun `details show their name, state, addresses and ports`() {
        val details =
            ComputerDetails().also {
                it.name = "Den PC"
                it.state = ComputerDetails.State.ONLINE
                it.activeAddress = tuple("192.168.1.5", 47993)
                it.uuid = "uuid-1"
                it.localAddress = tuple("192.168.1.5", 47989)
                it.remoteAddress = tuple("203.0.113.9", 47990)
                it.ipv6Address = tuple("fe80::1", 47992)
                it.manualAddress = tuple("pc.local", 47991)
                it.macAddress = "AA:BB:CC:DD:EE:FF"
                it.pairState = PairState.PAIRED
                it.runningGameId = 7
                it.httpsPort = 47984
                // Not in the text: the external port, the certificate, the app list, the flags.
                it.externalPort = 47999
                it.serverCert = testCertificate()
                it.rawAppList = "<apps/>"
                it.nvidiaServer = true
                it.borealis = hostInfo()
            }

        assertThat(details.toString())
            .isEqualTo(
                "Name: Den PC\n" +
                    "State: ONLINE\n" +
                    "Active Address: 192.168.1.5:47993\n" +
                    "UUID: uuid-1\n" +
                    "Local Address: 192.168.1.5:47989\n" +
                    "Remote Address: 203.0.113.9:47990\n" +
                    "IPv6 Address: [fe80::1]:47992\n" +
                    "Manual Address: pc.local:47991\n" +
                    "MAC Address: AA:BB:CC:DD:EE:FF\n" +
                    "Pair State: PAIRED\n" +
                    "Running Game ID: 7\n" +
                    "HTTPS Port: 47984\n"
            )
    }

    @Test
    fun `details with nothing set show null for every missing field`() {
        assertThat(ComputerDetails().toString())
            .isEqualTo(
                "Name: null\n" +
                    "State: UNKNOWN\n" +
                    "Active Address: null\n" +
                    "UUID: null\n" +
                    "Local Address: null\n" +
                    "Remote Address: null\n" +
                    "IPv6 Address: null\n" +
                    "Manual Address: null\n" +
                    "MAC Address: null\n" +
                    "Pair State: null\n" +
                    "Running Game ID: 0\n" +
                    "HTTPS Port: 0\n"
            )
    }

    @Test
    fun `details without a state show it as null`() {
        val details = ComputerDetails().also { it.state = null }

        assertThat(details.toString()).contains("\nState: null\n")
    }
}
