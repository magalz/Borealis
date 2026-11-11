package io.github.magalz.borealis.discovery

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.wol.WakeOnLanSender
import io.github.magalz.borealis.testing.LimeLogCapture
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.logging.Level
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException
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
 * Characterization: these tests pass on the Java class and on its Kotlin conversion, except the
 * ones about a port out of range (Sprint 16, S12-05): the Java class and the first conversion threw
 * an `IllegalArgumentException` from the whole wake, and now the port is skipped.
 */
class WakeOnLanSenderCharacterizationTest {
    @get:Rule val log = LimeLogCapture()

    private val loopback = InetAddress.getByName("127.0.0.1")
    private val open = mutableListOf<DatagramSocket>()

    // A made-up MAC address of this test, locally administered (02:), and different for every test
    // and every run. Ports 9 and 47009 are fixed, so another test run on the same machine may send
    // to them while this one listens; the receiving loop skips whatever is not this packet.
    private val ownMac =
        intArrayOf(0x02) + IntArray(OWN_MAC_RANDOM_BYTES) { Random.nextInt(BYTE_VALUES) }
    private val ownMacText = ownMac.joinToString(":") { it.toString(HEX_RADIX).padStart(2, '0') }
    private val ownPacket = magicPacket(*ownMac)

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

    /**
     * An HTTP port whose first four dynamic ports (nine, ten, eleven and thirteen above it) are in
     * range and whose fifth (twenty-one above it) is not, with a socket on each of the four. Null
     * when none of the candidate ports is free to listen on.
     */
    private fun bindPartlyOutOfRangePorts(): DynamicPorts? {
        for (httpPort in PARTLY_OUT_OF_RANGE_HTTP_PORTS) {
            val sockets = IN_RANGE_DYNAMIC_OFFSETS.map { offset -> bind(httpPort + offset) }
            if (sockets.all { socket -> socket != null }) {
                return DynamicPorts(httpPort, sockets.filterNotNull())
            }
            sockets.filterNotNull().forEach { socket -> socket.close() }
        }
        return null
    }

    /**
     * The next packet that holds [expected], or null when none comes within [timeoutMs]. A packet
     * with another payload is not the one of this test and is skipped.
     */
    private fun DatagramSocket.take(expected: ByteArray, timeoutMs: Int = RECEIVE_MS): ByteArray? {
        val deadline = System.nanoTime() + timeoutMs * NANOS_PER_MS
        while (true) {
            val leftMs = (deadline - System.nanoTime()) / NANOS_PER_MS
            if (leftMs <= 0) {
                return null
            }
            try {
                soTimeout = leftMs.toInt()
                val packet = DatagramPacket(ByteArray(BUFFER_BYTES), BUFFER_BYTES)
                receive(packet)
                val payload = packet.data.copyOf(packet.length)
                if (payload.contentEquals(expected)) {
                    return payload
                }
            } catch (ignored: SocketTimeoutException) {
                return null
            }
        }
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

        WakeOnLanSender.sendWolPacket(host(ownMacText, ports.httpPort))

        val received = ports.sockets.first().take(ownPacket)
        assertThat(received).hasLength(102)
        assertThat(received).isEqualTo(ownPacket)
    }

    @Test
    fun `each of the five dynamic ports of the host gets the packet once`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host(ownMacText, ports.httpPort))

        ports.sockets.forEach {
            assertThat(it.take(ownPacket)).isEqualTo(ownPacket)
            assertThat(it.take(ownPacket, NONE_MS)).isNull()
        }
    }

    @Test
    fun `the static port 47009 gets the packet whatever the HTTP port`() {
        val port = bind(STATIC_TOOL_PORT)
        assumeNotNull(port)
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host(ownMacText, ports.httpPort))

        assertThat(port!!.take(ownPacket)).isEqualTo(ownPacket)
    }

    @Test
    fun `the standard Wake-on-LAN port 9 gets the packet whatever the HTTP port`() {
        // Port 9 is privileged on some systems; skip when it cannot be listened on.
        val port = bind(STANDARD_WOL_PORT)
        assumeNotNull(port)
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host(ownMacText, ports.httpPort))

        assertThat(port!!.take(ownPacket)).isEqualTo(ownPacket)
    }

    @Test
    fun `hex groups keep their low eight bits, in either case`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("AA:bb:Cc:dD:EE:ff", ports.httpPort))

        val expected = magicPacket(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF)
        assertThat(ports.sockets.first().take(expected)).isEqualTo(expected)
    }

    @Test
    fun `a hex group above 255 wraps to a byte`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("100:1ff:7f:80:0:1", ports.httpPort))

        val expected = magicPacket(0x00, 0xFF, 0x7F, 0x80, 0x00, 0x01)
        assertThat(ports.sockets.first().take(expected)).isEqualTo(expected)
        assertThat(log.messages(Level.WARNING)).isEmpty()
    }

    @Test
    fun `a hex group with a sign is read as parseInt reads it`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("-1:+f:02:03:04:05", ports.httpPort))

        val expected = magicPacket(0xFF, 0x0F, 0x02, 0x03, 0x04, 0x05)
        assertThat(ports.sockets.first().take(expected)).isEqualTo(expected)
    }

    @Test
    fun `a MAC address with a bad group keeps the groups before it, zeroes the rest and warns`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02:11:zz:33:44:55", ports.httpPort))

        val expected = magicPacket(0x02, 0x11, 0, 0, 0, 0)
        assertThat(ports.sockets.first().take(expected)).isEqualTo(expected)
        assertThat(log.messages(Level.WARNING))
            .containsExactly("Malformed MAC address: 02:11:zz:33:44:55 (index: 2)")
    }

    @Test
    fun `a group that holds a space is a bad group`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02: 11:22:33:44:55", ports.httpPort))

        val expected = magicPacket(0x02, 0, 0, 0, 0, 0)
        assertThat(ports.sockets.first().take(expected)).isEqualTo(expected)
        assertThat(log.messages(Level.WARNING))
            .containsExactly("Malformed MAC address: 02: 11:22:33:44:55 (index: 1)")
    }

    @Test
    fun `a MAC address with fewer than six groups is padded with zeros without a warning`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("02:11", ports.httpPort))

        val expected = magicPacket(0x02, 0x11, 0, 0, 0, 0)
        assertThat(ports.sockets.first().take(expected)).isEqualTo(expected)
        assertThat(log.messages(Level.WARNING)).isEmpty()
    }

    @Test
    fun `a MAC address with more than six groups uses the first six`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("01:02:03:04:05:06:07:08", ports.httpPort))

        val expected = magicPacket(1, 2, 3, 4, 5, 6)
        assertThat(ports.sockets.first().take(expected)).isEqualTo(expected)
    }

    @Test
    fun `an empty MAC address is sent as six zero bytes without a warning`() {
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host("", ports.httpPort))

        val expected = magicPacket(0, 0, 0, 0, 0, 0)
        assertThat(ports.sockets.first().take(expected)).isEqualTo(expected)
        assertThat(log.messages(Level.WARNING)).isEmpty()
    }

    @Test
    fun `a host with no MAC address makes the sender throw before it sends anything`() {
        val ports = bindDynamicPorts()

        assertThrows(NullPointerException::class.java) {
            WakeOnLanSender.sendWolPacket(host(null, ports.httpPort))
        }

        ports.sockets.forEach { assertThat(it.take(ownPacket, NONE_MS)).isNull() }
    }

    @Test
    fun `a host with no address sends nothing and is not an error`() {
        val ports = bindDynamicPorts()
        val nowhere = ComputerDetails().apply { macAddress = ownMacText }

        WakeOnLanSender.sendWolPacket(nowhere)

        ports.sockets.forEach { assertThat(it.take(ownPacket, NONE_MS)).isNull() }
    }

    @Test
    fun `the local, remote and manual addresses each get the packets, even when they are the same, in the packets`() {
        val ports = bindDynamicPorts()
        val details =
            ComputerDetails().apply {
                macAddress = ownMacText
                localAddress = AddressTuple("127.0.0.1", ports.httpPort)
                remoteAddress = AddressTuple("127.0.0.1", ports.httpPort)
                manualAddress = AddressTuple("127.0.0.1", ports.httpPort)
            }

        WakeOnLanSender.sendWolPacket(details)

        repeat(3) { assertThat(ports.sockets.first().take(ownPacket)).isEqualTo(ownPacket) }
        assertThat(ports.sockets.first().take(ownPacket, NONE_MS)).isNull()
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
                macAddress = ownMacText
                ipv6Address = AddressTuple("::1", first - DYNAMIC_FIRST_OFFSET)
            }

        WakeOnLanSender.sendWolPacket(details)

        assertThat(socket!!.take(ownPacket)).isEqualTo(ownPacket)
    }

    @Test
    fun `a host name is resolved and its addresses get the packets`() {
        assumeTrue(InetAddress.getAllByName("localhost").contains(loopback))
        val ports = bindDynamicPorts()

        WakeOnLanSender.sendWolPacket(host(ownMacText, ports.httpPort, "localhost"))

        assertThat(ports.sockets.first().take(ownPacket)).isEqualTo(ownPacket)
    }

    @Test
    fun `an address that does not resolve does not stop the other addresses`() {
        val ports = bindDynamicPorts()
        val details =
            ComputerDetails().apply {
                macAddress = ownMacText
                localAddress = AddressTuple(UNRESOLVABLE, ports.httpPort)
                remoteAddress = AddressTuple("127.0.0.1", ports.httpPort)
            }

        WakeOnLanSender.sendWolPacket(details)

        ports.sockets.forEach { assertThat(it.take(ownPacket)).isEqualTo(ownPacket) }
    }

    @Test
    fun `an HTTP port too high for the dynamic ports sends the static ports and does not throw`() {
        // The dynamic ports are the HTTP port plus 9 to 21, so all of them are above 65535 here.
        val port = bind(STATIC_TOOL_PORT)
        assumeNotNull(port)

        val failure = runCatching { WakeOnLanSender.sendWolPacket(host(ownMacText, MAX_PORT)) }

        assertThat(failure.exceptionOrNull()).isNull()
        assertThat(port!!.take(ownPacket)).isEqualTo(ownPacket)
    }

    @Test
    fun `a dynamic port above 65535 is skipped and the ones in range still get the packet`() {
        val ports =
            bindPartlyOutOfRangePorts()
                ?: throw AssumptionViolatedException("no free ports near 65535 to listen on")
        val skipped = ports.httpPort + LAST_DYNAMIC_OFFSET

        val failure = runCatching {
            WakeOnLanSender.sendWolPacket(host(ownMacText, ports.httpPort))
        }

        assertThat(failure.exceptionOrNull()).isNull()
        ports.sockets.forEach { assertThat(it.take(ownPacket)).isEqualTo(ownPacket) }
        assertThat(log.messages(Level.WARNING))
            .contains("Wake-on-LAN: skipping port $skipped, it is outside 1..65535")
    }

    @Test
    fun `an address with a port out of range does not stop the other addresses`() {
        val ports = bindDynamicPorts()
        val details =
            ComputerDetails().apply {
                macAddress = ownMacText
                localAddress = AddressTuple("127.0.0.1", MAX_PORT)
                remoteAddress = AddressTuple("127.0.0.1", ports.httpPort)
            }

        val failure = runCatching { WakeOnLanSender.sendWolPacket(details) }

        assertThat(failure.exceptionOrNull()).isNull()
        ports.sockets.forEach { assertThat(it.take(ownPacket)).isEqualTo(ownPacket) }
    }

    private val broadcast = InetAddress.getByName("255.255.255.255")

    private fun wake(
        details: ComputerDetails,
        socket: RecordingWolSocket = RecordingWolSocket(),
    ): RecordingWolSocket {
        WakeOnLanSender.sendWolPacket(details) { socket }
        return socket
    }

    private fun RecordingWolSocket.portsOf(address: InetAddress): List<Int> =
        attempts.filter { it.address == address }.map { it.port }

    private fun RecordingWolSocket.distinctPayloads(): List<List<Byte>> =
        attempts.map { it.payload.toList() }.distinct()

    @Test
    fun `every packet is the magic packet, six 0xFF bytes and sixteen copies of the MAC address`() {
        val socket = wake(host(ownMacText, HTTP_PORT))

        assertThat(socket.attempts).isNotEmpty()
        assertThat(socket.attempts.map { it.payload.size }.distinct()).containsExactly(102)
        assertThat(socket.distinctPayloads()).containsExactly(ownPacket.toList())
    }

    @Test
    fun `an address gets the packets on the static ports and then on the five dynamic ports`() {
        val socket = wake(host(ownMacText, HTTP_PORT))

        assertThat(socket.portsOf(loopback))
            .containsExactly(9, 47_009, 47_998, 47_999, 48_000, 48_002, 48_010)
            .inOrder()
        assertThat(socket.portsOf(broadcast))
            .containsExactly(9, 47_009, 47_998, 47_999, 48_000, 48_002, 48_010)
            .inOrder()
    }

    @Test
    fun `the static ports 9 and 47009 get the packets whatever the HTTP port`() {
        val socket = wake(host(ownMacText, OTHER_HTTP_PORT))

        assertThat(socket.portsOf(loopback)).containsAtLeast(9, 47_009)
    }

    @Test
    fun `the dynamic ports follow the HTTP port of the host`() {
        val socket = wake(host(ownMacText, OTHER_HTTP_PORT))

        assertThat(socket.portsOf(loopback))
            .containsExactly(9, 47_009, 50_009, 50_010, 50_011, 50_013, 50_021)
            .inOrder()
    }

    @Test
    fun `the packets of one call go out through one socket, which is closed at the end`() {
        var opened = 0
        val socket = RecordingWolSocket()
        val details =
            ComputerDetails().apply {
                macAddress = ownMacText
                localAddress = AddressTuple("127.0.0.1", HTTP_PORT)
                remoteAddress = AddressTuple("127.0.0.2", HTTP_PORT)
            }

        WakeOnLanSender.sendWolPacket(details) {
            opened++
            socket
        }

        assertThat(opened).isEqualTo(1)
        assertThat(socket.attempts).hasSize(4 * 7)
        assertThat(socket.isClosed).isTrue()
    }

    @Test
    fun `hex groups are read in either case and keep their low eight bits, in every packet`() {
        val socket = wake(host("AA:bb:Cc:dD:EE:ff", HTTP_PORT))

        assertThat(socket.distinctPayloads())
            .containsExactly(magicPacket(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF).toList())
    }

    @Test
    fun `a hex group above 255 wraps to a byte without a warning, in every packet`() {
        val socket = wake(host("100:1ff:7f:80:0:1", HTTP_PORT))

        assertThat(socket.distinctPayloads())
            .containsExactly(magicPacket(0x00, 0xFF, 0x7F, 0x80, 0x00, 0x01).toList())
        assertThat(log.messages(Level.WARNING)).isEmpty()
    }

    @Test
    fun `a hex group with a sign is read as parseInt reads it, in every packet`() {
        val socket = wake(host("-1:+f:02:03:04:05", HTTP_PORT))

        assertThat(socket.distinctPayloads())
            .containsExactly(magicPacket(0xFF, 0x0F, 0x02, 0x03, 0x04, 0x05).toList())
    }

    @Test
    fun `a bad group keeps the groups before it and zeroes the rest, with a warning, in every packet`() {
        val socket = wake(host("02:11:zz:33:44:55", HTTP_PORT))

        assertThat(socket.distinctPayloads())
            .containsExactly(magicPacket(0x02, 0x11, 0, 0, 0, 0).toList())
        assertThat(log.messages(Level.WARNING))
            .containsExactly("Malformed MAC address: 02:11:zz:33:44:55 (index: 2)")
    }

    @Test
    fun `a group with a space is a bad group, in every packet`() {
        val socket = wake(host("02: 11:22:33:44:55", HTTP_PORT))

        assertThat(socket.distinctPayloads())
            .containsExactly(magicPacket(0x02, 0, 0, 0, 0, 0).toList())
        assertThat(log.messages(Level.WARNING))
            .containsExactly("Malformed MAC address: 02: 11:22:33:44:55 (index: 1)")
    }

    @Test
    fun `fewer than six groups are padded with zeros without a warning, in every packet`() {
        val socket = wake(host("02:11", HTTP_PORT))

        assertThat(socket.distinctPayloads())
            .containsExactly(magicPacket(0x02, 0x11, 0, 0, 0, 0).toList())
        assertThat(log.messages(Level.WARNING)).isEmpty()
    }

    @Test
    fun `more than six groups use the first six, in every packet`() {
        val socket = wake(host("01:02:03:04:05:06:07:08", HTTP_PORT))

        assertThat(socket.distinctPayloads()).containsExactly(magicPacket(1, 2, 3, 4, 5, 6).toList())
    }

    @Test
    fun `an empty MAC address goes out as six zero bytes without a warning, in every packet`() {
        val socket = wake(host("", HTTP_PORT))

        assertThat(socket.distinctPayloads()).containsExactly(magicPacket(0, 0, 0, 0, 0, 0).toList())
        assertThat(log.messages(Level.WARNING)).isEmpty()
    }

    @Test
    fun `no MAC address makes the sender throw with nothing sent`() {
        val socket = RecordingWolSocket()

        assertThrows(NullPointerException::class.java) { wake(host(null, HTTP_PORT), socket) }

        assertThat(socket.attempts).isEmpty()
    }

    @Test
    fun `a host with no address sends nothing, with no error`() {
        val socket = wake(ComputerDetails().apply { macAddress = ownMacText })

        assertThat(socket.attempts).isEmpty()
    }

    @Test
    fun `the local, remote and manual addresses each get the packets, even when they are the same`() {
        val details =
            ComputerDetails().apply {
                macAddress = ownMacText
                localAddress = AddressTuple("127.0.0.1", HTTP_PORT)
                remoteAddress = AddressTuple("127.0.0.1", HTTP_PORT)
                manualAddress = AddressTuple("127.0.0.1", HTTP_PORT)
            }

        val socket = wake(details)

        assertThat(socket.portsOf(loopback).filter { it == 47_998 }).hasSize(3)
        assertThat(socket.portsOf(loopback)).hasSize(3 * 7)
    }

    @Test
    fun `the IPv6 address gets the packets too, in the packets`() {
        val details =
            ComputerDetails().apply {
                macAddress = ownMacText
                ipv6Address = AddressTuple("::1", HTTP_PORT)
            }

        val socket = wake(details)

        assertThat(socket.portsOf(InetAddress.getByName("::1")))
            .containsExactly(9, 47_009, 47_998, 47_999, 48_000, 48_002, 48_010)
            .inOrder()
    }

    @Test
    fun `a host name is resolved and its addresses get the packets, in the packets`() {
        assumeTrue(InetAddress.getAllByName("localhost").contains(loopback))

        val socket = wake(host(ownMacText, HTTP_PORT, "localhost"))

        assertThat(socket.portsOf(loopback)).contains(47_998)
    }

    @Test
    fun `an address that does not resolve does not stop the other addresses, in the packets`() {
        val details =
            ComputerDetails().apply {
                macAddress = ownMacText
                localAddress = AddressTuple(UNRESOLVABLE, HTTP_PORT)
                remoteAddress = AddressTuple("127.0.0.1", HTTP_PORT)
            }

        val socket = wake(details)

        assertThat(socket.portsOf(loopback)).hasSize(7)
    }

    @Test
    fun `an HTTP port too high for the dynamic ports sends the static ports, with no error`() {
        // The dynamic ports are the HTTP port plus 9 to 21, so all of them are above 65535 here.
        val socket = wake(host(ownMacText, MAX_PORT))

        assertThat(socket.portsOf(loopback)).containsExactly(9, 47_009).inOrder()
    }

    @Test
    fun `a dynamic port above 65535 is skipped with a warning and the others still go out`() {
        // With an HTTP port of 65515 the first four dynamic ports are in range and the fifth, 65536,
        // is not.
        val socket = wake(host(ownMacText, PARTLY_OUT_OF_RANGE_HTTP_PORT))

        assertThat(socket.portsOf(loopback))
            .containsExactly(9, 47_009, 65_524, 65_525, 65_526, 65_528)
            .inOrder()
        assertThat(log.messages(Level.WARNING))
            .contains("Wake-on-LAN: skipping port 65536, it is outside 1..65535")
    }

    @Test
    fun `an address with a port out of range does not stop the other addresses, in the packets`() {
        val details =
            ComputerDetails().apply {
                macAddress = ownMacText
                localAddress = AddressTuple("127.0.0.1", MAX_PORT)
                remoteAddress = AddressTuple("127.0.0.2", HTTP_PORT)
            }

        val socket = wake(details)

        assertThat(socket.portsOf(InetAddress.getByName("127.0.0.2")))
            .containsExactly(9, 47_009, 47_998, 47_999, 48_000, 48_002, 48_010)
            .inOrder()
    }

    @Test
    fun `the socket of a call is bound to an ephemeral port on every address and may broadcast`() {
        WakeOnLanSender.openWolSocket().use { socket ->
            assertThat(socket.isBound).isTrue()
            assertThat(socket.isConnected).isFalse()
            assertThat(socket.localPort).isGreaterThan(0)
            assertThat(socket.localAddress.isAnyLocalAddress).isTrue()
            assertThat(socket.broadcast).isTrue()
        }
    }

    @Test
    fun `the socket of a call sends a packet from its own port`() {
        val receiver = bind(0)
        assumeNotNull(receiver)

        WakeOnLanSender.openWolSocket().use { socket ->
            val bytes = ownPacket
            socket.send(DatagramPacket(bytes, bytes.size, loopback, receiver!!.localPort))

            val packet = DatagramPacket(ByteArray(BUFFER_BYTES), BUFFER_BYTES)
            receiver.soTimeout = RECEIVE_MS
            receiver.receive(packet)
            assertThat(packet.data.copyOf(packet.length)).isEqualTo(bytes)
            assertThat(packet.port).isEqualTo(socket.localPort)
        }
    }

    private companion object {
        const val HTTP_PORT = 47_989
        const val OTHER_HTTP_PORT = 50_000
        const val PARTLY_OUT_OF_RANGE_HTTP_PORT = 65_515
        const val BIND_TRIES = 50
        const val FIRST_PORT_MIN = 20_000
        const val FIRST_PORT_MAX = 60_000
        const val DYNAMIC_FIRST_OFFSET = 9
        val DYNAMIC_OFFSETS = listOf(0, 1, 2, 4, 12)

        // The dynamic ports are the HTTP port plus 9, 10, 11, 13 and 21. With an HTTP port from
        // 65515 to 65522 the first four are in range and the last is not.
        val IN_RANGE_DYNAMIC_OFFSETS = listOf(9, 10, 11, 13)
        const val LAST_DYNAMIC_OFFSET = 21
        val PARTLY_OUT_OF_RANGE_HTTP_PORTS = 65_515..65_522
        const val STATIC_TOOL_PORT = 47_009
        const val STANDARD_WOL_PORT = 9
        const val MAX_PORT = 65_535
        const val BUFFER_BYTES = 1_024
        const val OWN_MAC_RANDOM_BYTES = 5
        const val BYTE_VALUES = 256
        const val HEX_RADIX = 16
        const val NANOS_PER_MS = 1_000_000L
        const val RECEIVE_MS = 3_000
        const val NONE_MS = 150

        // An IPv6 address with a scope that no interface has: the JDK refuses it at once, with no
        // lookup.
        const val UNRESOLVABLE = "fe80::1%nonexistent0"
    }
}

/** One packet that the sender tried to send, as it was when it went to the socket. */
internal class AttemptedWolPacket(val address: InetAddress, val port: Int, val payload: ByteArray) {
    /** `address:port`, for the message of a failure that tells one send from another. */
    val target: String
        get() = "${address.hostAddress}:$port"
}

/**
 * A socket that sends nothing. It keeps every packet that the sender hands to it, in order, and
 * throws the [IOException] that [failure] returns for a packet, if any. The socket stays unbound:
 * no port is taken and no packet leaves the process.
 */
internal class RecordingWolSocket(
    private val failure: (AttemptedWolPacket) -> IOException? = { null }
) : DatagramSocket(null as SocketAddress?) {
    val attempts = mutableListOf<AttemptedWolPacket>()

    override fun send(packet: DatagramPacket) {
        val attempt =
            AttemptedWolPacket(
                packet.address,
                packet.port,
                packet.data.copyOfRange(packet.offset, packet.offset + packet.length),
            )
        attempts += attempt
        failure(attempt)?.let { throw it }
    }
}
