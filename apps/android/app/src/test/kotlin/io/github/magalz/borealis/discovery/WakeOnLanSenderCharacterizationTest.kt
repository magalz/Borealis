package io.github.magalz.borealis.discovery

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.wol.WakeOnLanSender
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.logging.Level
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Wake-on-LAN packets of the inherited sender: the bytes of the magic packet for a MAC address
 * (a bad one included), the ports and the addresses that it goes to, and what a host that does not
 * resolve does to the others. Sockets that listen on `127.0.0.1` receive the packets.
 *
 * The sender also broadcasts to `255.255.255.255` for every address. That needs a network that
 * these tests cannot rely on, so what it does is not checked here.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
class WakeOnLanSenderCharacterizationTest {
    @get:Rule val log = LimeLogRecords()

    private val loopback = InetAddress.getByName("127.0.0.1")
    private val open = mutableListOf<DatagramSocket>()

    @After
    fun closeSockets() {
        open.forEach { it.close() }
    }

    /** The five wake ports that follow the HTTP port of a host, with a socket on each. */
    private class DynamicPorts(val httpPort: Int, val sockets: List<DatagramSocket>)

    private fun bind(port: Int): DatagramSocket? =
        try {
            DatagramSocket(InetSocketAddress(loopback, port)).also { open += it }
        } catch (ignored: SocketException) {
            null
        }

    /** Ports 47998, 47999, 48000, 48002 and 48010 follow the HTTP port of the host. */
    private fun bindDynamicPorts(): DynamicPorts {
        repeat(BIND_TRIES) { _ ->
            val first = Random.nextInt(FIRST_PORT_MIN, FIRST_PORT_MAX)
            val sockets = DYNAMIC_OFFSETS.map { offset -> bind(first + offset) }
            if (sockets.all { socket -> socket != null }) {
                // The first dynamic port, 47998, is nine above the HTTP port.
                return DynamicPorts(first - DYNAMIC_FIRST_OFFSET, sockets.filterNotNull())
            }
            sockets.filterNotNull().forEach { socket -> socket.close() }
        }
        error("no free ports to listen on")
    }

    private fun DatagramSocket.take(timeoutMs: Int = RECEIVE_MS): ByteArray? =
        try {
            soTimeout = timeoutMs
            val packet = DatagramPacket(ByteArray(BUFFER_BYTES), BUFFER_BYTES)
            receive(packet)
            packet.data.copyOf(packet.length)
        } catch (ignored: SocketTimeoutException) {
            null
        }

    private fun host(
        mac: String?,
        httpPort: Int,
        address: String = "127.0.0.1",
    ): ComputerDetails =
        ComputerDetails().apply {
            macAddress = mac
            localAddress = AddressTuple(address, httpPort)
        }

    /** Six bytes of 0xFF, then the MAC bytes sixteen times. */
    private fun magicPacket(vararg mac: Int): ByteArray =
        ByteArray(6) { 0xFF.toByte() } +
            (1..16).flatMap { mac.map { byte -> byte.toByte() } }.toByteArray()

    @Test
    fun `the magic packet is six 0xFF bytes and sixteen copies of the MAC address`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02:11:22:33:44:55", ports.httpPort))

        val received = ports.sockets.first().take()
        assertThat(received).hasLength(102)
        assertThat(received).isEqualTo(magicPacket(0x02, 0x11, 0x22, 0x33, 0x44, 0x55))
    }

    @Test
    fun `each of the five dynamic ports of the host gets the packet once`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02:11:22:33:44:55", ports.httpPort))

        val expected = magicPacket(0x02, 0x11, 0x22, 0x33, 0x44, 0x55)
        ports.sockets.forEach {
            assertThat(it.take()).isEqualTo(expected)
            assertThat(it.take(NONE_MS)).isNull()
        }
    }

    @Test
    fun `the static port 47009 gets the packet whatever the HTTP port`() {
        val port = bind(STATIC_TOOL_PORT)
        assumeNotNull(port)
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02:11:22:33:44:55", ports.httpPort))

        assertThat(port!!.take()).isEqualTo(magicPacket(0x02, 0x11, 0x22, 0x33, 0x44, 0x55))
    }

    @Test
    fun `the standard Wake-on-LAN port 9 gets the packet whatever the HTTP port`() {
        // Port 9 is privileged on some systems; skip when it cannot be listened on.
        val port = bind(STANDARD_WOL_PORT)
        assumeNotNull(port)
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02:11:22:33:44:55", ports.httpPort))

        assertThat(port!!.take()).isEqualTo(magicPacket(0x02, 0x11, 0x22, 0x33, 0x44, 0x55))
    }

    @Test
    fun `hex groups keep their low eight bits, in either case`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("AA:bb:Cc:dD:EE:ff", ports.httpPort))

        assertThat(ports.sockets.first().take())
            .isEqualTo(magicPacket(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF))
    }

    @Test
    fun `a hex group above 255 wraps to a byte`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("100:1ff:7f:80:0:1", ports.httpPort))

        assertThat(ports.sockets.first().take()).isEqualTo(magicPacket(0x00, 0xFF, 0x7F, 0x80, 0x00, 0x01))
        assertThat(log.messages(Level.WARNING)).isEmpty()
    }

    @Test
    fun `a hex group with a sign is read as parseInt reads it`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("-1:+f:02:03:04:05", ports.httpPort))

        assertThat(ports.sockets.first().take()).isEqualTo(magicPacket(0xFF, 0x0F, 0x02, 0x03, 0x04, 0x05))
    }

    @Test
    fun `a MAC address with a bad group keeps the groups before it, zeroes the rest and warns`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02:11:zz:33:44:55", ports.httpPort))

        assertThat(ports.sockets.first().take()).isEqualTo(magicPacket(0x02, 0x11, 0, 0, 0, 0))
        assertThat(log.messages(Level.WARNING))
            .containsExactly("Malformed MAC address: 02:11:zz:33:44:55 (index: 2)")
    }

    @Test
    fun `a group that holds a space is a bad group`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02: 11:22:33:44:55", ports.httpPort))

        assertThat(ports.sockets.first().take()).isEqualTo(magicPacket(0x02, 0, 0, 0, 0, 0))
        assertThat(log.messages(Level.WARNING))
            .containsExactly("Malformed MAC address: 02: 11:22:33:44:55 (index: 1)")
    }

    @Test
    fun `a MAC address with fewer than six groups is padded with zeros without a warning`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02:11", ports.httpPort))

        assertThat(ports.sockets.first().take()).isEqualTo(magicPacket(0x02, 0x11, 0, 0, 0, 0))
        assertThat(log.messages(Level.WARNING)).isEmpty()
    }

    @Test
    fun `a MAC address with more than six groups uses the first six`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("01:02:03:04:05:06:07:08", ports.httpPort))

        assertThat(ports.sockets.first().take()).isEqualTo(magicPacket(1, 2, 3, 4, 5, 6))
    }

    @Test
    fun `an empty MAC address is sent as six zero bytes without a warning`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("", ports.httpPort))

        assertThat(ports.sockets.first().take()).isEqualTo(magicPacket(0, 0, 0, 0, 0, 0))
        assertThat(log.messages(Level.WARNING)).isEmpty()
    }

    @Test
    fun `a host with no MAC address makes the sender throw before it sends anything`() {
        val ports = bindDynamicPorts()

        assertThrows(NullPointerException::class.java) {
            WakeOnLanSender.sendWolPacket(host(null, ports.httpPort))
        }

        ports.sockets.forEach { assertThat(it.take(NONE_MS)).isNull() }
    }

    @Test
    fun `a host with no address sends nothing and is not an error`() {
        val ports = bindDynamicPorts()
        val nowhere = ComputerDetails().apply { macAddress = "02:11:22:33:44:55" }

        WakeOnLanSender.sendWolPacket(nowhere)

        ports.sockets.forEach { assertThat(it.take(NONE_MS)).isNull() }
    }

    @Test
    fun `the local, remote and manual addresses each get the packets, even when they are the same`() {
        val ports = bindDynamicPorts()
        val details =
            ComputerDetails().apply {
                macAddress = "02:11:22:33:44:55"
                localAddress = AddressTuple("127.0.0.1", ports.httpPort)
                remoteAddress = AddressTuple("127.0.0.1", ports.httpPort)
                manualAddress = AddressTuple("127.0.0.1", ports.httpPort)
            }

        WakeOnLanSender.sendWolPacket(details)

        val expected = magicPacket(0x02, 0x11, 0x22, 0x33, 0x44, 0x55)
        repeat(3) { assertThat(ports.sockets.first().take()).isEqualTo(expected) }
        assertThat(ports.sockets.first().take(NONE_MS)).isNull()
    }

    @Test
    fun `the IPv6 address gets the packets too`() {
        val ipv6Loopback = InetAddress.getByName("::1")
        val first = Random.nextInt(FIRST_PORT_MIN, FIRST_PORT_MAX)
        val socket =
            try {
                DatagramSocket(InetSocketAddress(ipv6Loopback, first)).also { open += it }
            } catch (ignored: SocketException) {
                null
            }
        assumeNotNull(socket)
        val details =
            ComputerDetails().apply {
                macAddress = "02:11:22:33:44:55"
                ipv6Address = AddressTuple("::1", first - DYNAMIC_FIRST_OFFSET)
            }

        WakeOnLanSender.sendWolPacket(details)

        assertThat(socket!!.take()).isEqualTo(magicPacket(0x02, 0x11, 0x22, 0x33, 0x44, 0x55))
    }

    @Test
    fun `a host name is resolved and its addresses get the packets`() {
        assumeTrue(InetAddress.getAllByName("localhost").contains(loopback))
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02:11:22:33:44:55", ports.httpPort, "localhost"))

        assertThat(ports.sockets.first().take())
            .isEqualTo(magicPacket(0x02, 0x11, 0x22, 0x33, 0x44, 0x55))
    }

    @Test
    fun `an address that does not resolve does not stop the other addresses`() {
        val ports = bindDynamicPorts()
        val details =
            ComputerDetails().apply {
                macAddress = "02:11:22:33:44:55"
                localAddress = AddressTuple(UNRESOLVABLE, ports.httpPort)
                remoteAddress = AddressTuple("127.0.0.1", ports.httpPort)
            }

        WakeOnLanSender.sendWolPacket(details)

        val expected = magicPacket(0x02, 0x11, 0x22, 0x33, 0x44, 0x55)
        ports.sockets.forEach { assertThat(it.take()).isEqualTo(expected) }
    }

    @Test
    fun `an HTTP port too high for the dynamic ports makes the sender throw`() {
        // Inherited quirk: the dynamic ports are the HTTP port plus 9 to 21, and a port above 65535
        // is an IllegalArgumentException, not an IOException, so nothing catches it.
        assertThrows(IllegalArgumentException::class.java) {
            WakeOnLanSender.sendWolPacket(host("02:11:22:33:44:55", MAX_PORT))
        }
    }

    private companion object {
        const val BIND_TRIES = 50
        const val FIRST_PORT_MIN = 20_000
        const val FIRST_PORT_MAX = 60_000
        const val DYNAMIC_FIRST_OFFSET = 9
        val DYNAMIC_OFFSETS = listOf(0, 1, 2, 4, 12)
        const val STATIC_TOOL_PORT = 47_009
        const val STANDARD_WOL_PORT = 9
        const val MAX_PORT = 65_535
        const val BUFFER_BYTES = 1_024
        const val RECEIVE_MS = 3_000
        const val NONE_MS = 150

        // An IPv6 address with a scope that no interface has: the JDK refuses it at once, with no lookup.
        const val UNRESOLVABLE = "fe80::1%nonexistent0"
    }
}
