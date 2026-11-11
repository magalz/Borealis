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
import java.util.logging.Level
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Wake-on-LAN packets of the inherited sender: the bytes of the magic packet for a MAC address
 * (a bad one included), the ports and the addresses that it goes to, and what a host that does not
 * resolve does to the others. A [RecordingWolSocket] takes the packets, so nothing goes out to the
 * network; the one test that sends through the real socket of the sender sends to `127.0.0.1` only.
 * What the sender does with the broadcast address is checked in
 * [WakeOnLanSenderBroadcastCharacterizationTest].
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion, except the
 * ones about a port out of range (Sprint 16, S12-05): the Java class and the first conversion threw
 * an `IllegalArgumentException` from the whole wake, and now the port is skipped.
 */
class WakeOnLanSenderCharacterizationTest {
    @get:Rule val log = LimeLogCapture()

    private val loopback = InetAddress.getByName("127.0.0.1")

    // A made-up MAC address of this test, locally administered (02:), and different for every test
    // and every run.
    private val ownMac =
        intArrayOf(0x02) + IntArray(OWN_MAC_RANDOM_BYTES) { Random.nextInt(BYTE_VALUES) }
    private val ownMacText = ownMac.joinToString(":") { it.toString(HEX_RADIX).padStart(2, '0') }
    private val ownPacket = magicPacket(*ownMac)

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

        assertThat(socket.distinctPayloads())
            .containsExactly(magicPacket(1, 2, 3, 4, 5, 6).toList())
    }

    @Test
    fun `an empty MAC address goes out as six zero bytes without a warning, in every packet`() {
        val socket = wake(host("", HTTP_PORT))

        assertThat(socket.distinctPayloads())
            .containsExactly(magicPacket(0, 0, 0, 0, 0, 0).toList())
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
    fun `the IPv6 address gets the packets too`() {
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
    fun `a host name is resolved and its addresses get the packets`() {
        assumeTrue(InetAddress.getAllByName("localhost").contains(loopback))

        val socket = wake(host(ownMacText, HTTP_PORT, "localhost"))

        assertThat(socket.portsOf(loopback)).contains(47_998)
    }

    @Test
    fun `an address that does not resolve does not stop the other addresses`() {
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
        // With an HTTP port of 65515 the first four dynamic ports are in range; the fifth is 65536.
        val socket = wake(host(ownMacText, PARTLY_OUT_OF_RANGE_HTTP_PORT))

        assertThat(socket.portsOf(loopback))
            .containsExactly(9, 47_009, 65_524, 65_525, 65_526, 65_528)
            .inOrder()
        assertThat(log.messages(Level.WARNING))
            .contains("Wake-on-LAN: skipping port 65536, it is outside 1..65535")
    }

    @Test
    fun `an address with a port out of range does not stop the other addresses`() {
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
        DatagramSocket(InetSocketAddress(loopback, 0)).use { receiver ->
            WakeOnLanSender.openWolSocket().use { socket ->
                val bytes = ownPacket
                socket.send(DatagramPacket(bytes, bytes.size, loopback, receiver.localPort))

                val packet = DatagramPacket(ByteArray(BUFFER_BYTES), BUFFER_BYTES)
                receiver.soTimeout = RECEIVE_MS
                receiver.receive(packet)
                assertThat(packet.data.copyOf(packet.length)).isEqualTo(bytes)
                assertThat(packet.port).isEqualTo(socket.localPort)
            }
        }
    }

    private companion object {
        const val HTTP_PORT = 47_989
        const val OTHER_HTTP_PORT = 50_000
        // The dynamic ports are the HTTP port plus 9, 10, 11, 13 and 21: with this one the first
        // four are in range and the last is not.
        const val PARTLY_OUT_OF_RANGE_HTTP_PORT = 65_515
        const val MAX_PORT = 65_535
        const val BUFFER_BYTES = 1_024
        const val OWN_MAC_RANDOM_BYTES = 5
        const val BYTE_VALUES = 256
        const val HEX_RADIX = 16
        const val RECEIVE_MS = 3_000

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
