package io.github.magalz.borealis.discovery

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.wol.WakeOnLanSender
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * What [WakeOnLanSender] does with the broadcast address and with a call that sends nothing: the
 * parts that [WakeOnLanSenderCharacterizationTest] leaves out because they depend on the network of
 * the machine. A probe finds out first what this machine does with a packet sent to
 * `255.255.255.255`, and each test runs only where it can tell the code from its absence.
 *
 * Characterization: these tests pass on the code as converted from Java.
 */
class WakeOnLanSenderBroadcastCharacterizationTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val open = mutableListOf<DatagramSocket>()

    // A made-up MAC address of this test, locally administered (02:), and different for every test
    // and every run, so that a packet of another test or run is skipped by the receiving loop.
    private val ownMac =
        intArrayOf(0x02) + IntArray(OWN_MAC_RANDOM_BYTES) { Random.nextInt(BYTE_VALUES) }
    private val ownMacText = ownMac.joinToString(":") { it.toString(HEX_RADIX).padStart(2, '0') }
    private val ownPacket =
        ByteArray(MAC_BYTES) { 0xFF.toByte() } +
            (1..MAC_REPETITIONS).flatMap { ownMac.map { byte -> byte.toByte() } }.toByteArray()

    /** What this machine does with a packet to the broadcast address. */
    private class BroadcastProbe(
        /** The send works: the network has an interface that takes the broadcast. */
        val sends: Boolean,
        /** The source address of each copy that came back to a socket of this machine. */
        val sources: List<InetAddress>,
    )

    private val probe: BroadcastProbe by lazy { probeBroadcast() }

    @After
    fun closeSockets() {
        open.forEach { it.close() }
    }

    private fun probeBroadcast(): BroadcastProbe {
        val broadcast = InetAddress.getByName(BROADCAST)
        DatagramSocket(0).use { receiver ->
            DatagramSocket(0).use { sender ->
                val probeBytes = byteArrayOf(1)
                try {
                    sender.send(
                        DatagramPacket(probeBytes, probeBytes.size, broadcast, receiver.localPort)
                    )
                } catch (ignored: IOException) {
                    return BroadcastProbe(sends = false, sources = emptyList())
                }
                val sources = mutableListOf<InetAddress>()
                var waitMs = PROBE_FIRST_MS
                try {
                    while (true) {
                        receiver.soTimeout = waitMs
                        val packet = DatagramPacket(ByteArray(BUFFER_BYTES), BUFFER_BYTES)
                        receiver.receive(packet)
                        sources += packet.address
                        waitMs = PROBE_MORE_MS
                    }
                } catch (ignored: SocketTimeoutException) {
                    return BroadcastProbe(sends = true, sources = sources)
                }
            }
        }
    }

    /** The five wake ports that follow the HTTP port of a host, with a socket on each. */
    private class DynamicPorts(val httpPort: Int, val sockets: List<DatagramSocket>)

    private fun bind(port: Int, wildcard: Boolean): DatagramSocket? =
        try {
            val address =
                if (wildcard) InetSocketAddress(port) else InetSocketAddress(loopback, port)
            DatagramSocket(address).also { open += it }
        } catch (ignored: SocketException) {
            null
        }

    /**
     * Ports 47998, 47999, 48000, 48002 and 48010 follow the HTTP port of the host. [wildcard] is a
     * socket on every address of the machine, which the broadcast reaches, and otherwise a socket
     * on the loopback address only.
     */
    private fun bindDynamicPorts(wildcard: Boolean): DynamicPorts {
        repeat(BIND_TRIES) { _ ->
            val first = Random.nextInt(FIRST_PORT_MIN, FIRST_PORT_MAX)
            val sockets = DYNAMIC_OFFSETS.map { offset -> bind(first + offset, wildcard) }
            if (sockets.all { socket -> socket != null }) {
                // The first dynamic port, 47998, is nine above the HTTP port.
                return DynamicPorts(first - DYNAMIC_FIRST_OFFSET, sockets.filterNotNull())
            }
            sockets.filterNotNull().forEach { socket -> socket.close() }
        }
        error("no free ports to listen on")
    }

    /**
     * The next packet that holds [expected], or null when none comes within [timeoutMs]. A packet
     * with another payload is not the one of this test and is skipped.
     */
    private fun DatagramSocket.takePacket(
        expected: ByteArray,
        timeoutMs: Int = RECEIVE_MS,
    ): DatagramPacket? {
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
                if (packet.data.copyOf(packet.length).contentEquals(expected)) {
                    return packet
                }
            } catch (ignored: SocketTimeoutException) {
                return null
            }
        }
    }

    private fun details(vararg addresses: AddressTuple?): ComputerDetails =
        ComputerDetails().apply {
            macAddress = ownMacText
            localAddress = addresses.getOrNull(0)
            remoteAddress = addresses.getOrNull(1)
            manualAddress = addresses.getOrNull(2)
            ipv6Address = addresses.getOrNull(3)
        }

    @Test
    fun `all the packets of one call come from one socket`() {
        val ports = bindDynamicPorts(wildcard = false)
        val details =
            details(
                AddressTuple("127.0.0.1", ports.httpPort),
                AddressTuple("127.0.0.1", ports.httpPort),
            )

        WakeOnLanSender.sendWolPacket(details)

        // Two addresses, five ports each: ten packets that come from the same port of this machine.
        val sourcePorts =
            ports.sockets
                .flatMap { socket -> List(2) { checkNotNull(socket.takePacket(ownPacket)) } }
                .map { it.port }
        assertThat(sourcePorts).hasSize(DYNAMIC_OFFSETS.size * 2)
        assertThat(sourcePorts.toSet()).hasSize(1)
    }

    @Test
    fun `a host that does not resolve still gets the packets on the broadcast address, once each`() {
        assumeTrue(probe.sources.size == 1)
        val ports = bindDynamicPorts(wildcard = true)

        WakeOnLanSender.sendWolPacket(details(AddressTuple(unresolvable(0), ports.httpPort)))

        ports.sockets.forEach {
            assertThat(it.takePacket(ownPacket)).isNotNull()
            assertThat(it.takePacket(ownPacket, NONE_MS)).isNull()
        }
    }

    @Test
    fun `the broadcast goes out before the resolved addresses, from the same socket`() {
        assumeTrue(probe.sources.singleOrNull()?.isLoopbackAddress == false)
        val ports = bindDynamicPorts(wildcard = true)

        WakeOnLanSender.sendWolPacket(details(AddressTuple("127.0.0.1", ports.httpPort)))

        // A socket on every address gets both: the broadcast comes from an address of the machine
        // that is not the loopback one, the resolved address from the loopback address itself.
        val packets =
            ports.sockets.map { socket -> List(2) { checkNotNull(socket.takePacket(ownPacket)) } }
        packets.forEach { (broadcast, resolved) ->
            assertThat(broadcast.address.isLoopbackAddress).isFalse()
            assertThat(resolved.address.isLoopbackAddress).isTrue()
        }
        assertThat(packets.flatten().map { it.port }.toSet()).hasSize(1)
        ports.sockets.forEach { assertThat(it.takePacket(ownPacket, NONE_MS)).isNull() }
    }

    @Test
    fun `a host that does not resolve does not stop the call when the broadcast went out`() {
        assumeTrue(probe.sends)

        val failure = runCatching {
            WakeOnLanSender.sendWolPacket(details(AddressTuple(unresolvable(0), PORT)))
        }

        assertThat(failure.exceptionOrNull()).isNull()
    }

    @Test
    fun `when no packet goes out, the call throws the failure that came last`() {
        assumeFalse(probe.sends)
        // The local, remote, manual and IPv6 addresses are tried in this order, and the broadcast
        // fails before each resolution does: the last failure is the one of the IPv6 address.
        val details =
            details(
                AddressTuple(unresolvable(0), PORT),
                AddressTuple(unresolvable(1), PORT),
                AddressTuple(unresolvable(2), PORT),
                AddressTuple(unresolvable(3), PORT),
            )

        val failure =
            assertThrows(UnknownHostException::class.java) {
                WakeOnLanSender.sendWolPacket(details)
            }

        assertThat(failure).hasMessageThat().contains(unresolvable(3).substringAfter('%'))
    }

    private val broadcast = InetAddress.getByName(BROADCAST)

    private fun wake(
        details: ComputerDetails,
        socket: RecordingWolSocket = RecordingWolSocket(),
    ): RecordingWolSocket {
        WakeOnLanSender.sendWolPacket(details) { socket }
        return socket
    }

    /** A send to the broadcast address fails, as on a machine with no network. */
    private fun noBroadcast() =
        RecordingWolSocket { attempt ->
            if (attempt.address == broadcast) IOException("no route for ${attempt.target}") else null
        }

    @Test
    fun `the broadcast goes out before the resolved addresses, one address after the other`() {
        val details =
            details(AddressTuple("127.0.0.1", HTTP_PORT), AddressTuple("127.0.0.2", HTTP_PORT))

        val socket = wake(details)

        // Seven packets for each of the four sends: the static ports, then the five dynamic ones.
        val blocks = socket.attempts.chunked(WAKE_PORTS)
        assertThat(blocks.map { it.map { attempt -> attempt.address.hostAddress }.distinct() })
            .containsExactly(
                listOf(BROADCAST),
                listOf("127.0.0.1"),
                listOf(BROADCAST),
                listOf("127.0.0.2"),
            )
            .inOrder()
        blocks.forEach { block ->
            assertThat(block.map { it.port })
                .containsExactly(9, 47_009, 47_998, 47_999, 48_000, 48_002, 48_010)
                .inOrder()
        }
    }

    @Test
    fun `a host that does not resolve gets the packets on the broadcast address, once each, in the packets`() {
        val socket = wake(details(AddressTuple(unresolvable(0), HTTP_PORT)))

        assertThat(socket.attempts.map { it.address }.distinct()).containsExactly(broadcast)
        assertThat(socket.attempts.map { it.port })
            .containsExactly(9, 47_009, 47_998, 47_999, 48_000, 48_002, 48_010)
            .inOrder()
    }

    @Test
    fun `a host that does not resolve does not stop the call when the broadcast went out, in the packets`() {
        val failure = runCatching {
            wake(details(AddressTuple(unresolvable(0), HTTP_PORT)))
        }

        assertThat(failure.exceptionOrNull()).isNull()
    }

    @Test
    fun `a broadcast that fails does not stop the resolved addresses or make the call fail`() {
        val socket = noBroadcast()

        val failure = runCatching { wake(details(AddressTuple("127.0.0.1", HTTP_PORT)), socket) }

        assertThat(failure.exceptionOrNull()).isNull()
        assertThat(socket.attempts.filter { it.address != broadcast }).hasSize(WAKE_PORTS)
    }

    @Test
    fun `a port that fails does not stop the other ports or make the call fail`() {
        val socket =
            RecordingWolSocket { attempt ->
                if (attempt.port == 9) IOException("refused ${attempt.target}") else null
            }

        val failure = runCatching { wake(details(AddressTuple("127.0.0.1", HTTP_PORT)), socket) }

        assertThat(failure.exceptionOrNull()).isNull()
        assertThat(socket.attempts).hasSize(2 * WAKE_PORTS)
    }

    @Test
    fun `an address that fails entirely does not make the call fail when another address went out`() {
        val socket =
            RecordingWolSocket { attempt ->
                if (attempt.address.hostAddress == "127.0.0.1") {
                    IOException("refused ${attempt.target}")
                } else {
                    null
                }
            }
        val details =
            details(AddressTuple("127.0.0.1", HTTP_PORT), AddressTuple("127.0.0.2", HTTP_PORT))

        val failure = runCatching { wake(details, socket) }

        assertThat(failure.exceptionOrNull()).isNull()
    }

    @Test
    fun `when every send fails, the call throws the failure of the last send`() {
        // Every port of every address fails; each failure names its address and port. The last
        // send is the one to the last port of the second address, and it is the one that comes out.
        val socket = RecordingWolSocket { attempt -> IOException("refused ${attempt.target}") }
        val details =
            details(AddressTuple("127.0.0.1", HTTP_PORT), AddressTuple("127.0.0.2", HTTP_PORT))

        val failure = assertThrows(IOException::class.java) { wake(details, socket) }

        assertThat(failure).hasMessageThat().isEqualTo("refused 127.0.0.2:48010")
        assertThat(socket.attempts).hasSize(4 * WAKE_PORTS)
    }

    @Test
    fun `when only the resolution fails after a broadcast that failed, the call throws the resolution failure`() {
        val socket = noBroadcast()
        val details =
            details(
                AddressTuple(unresolvable(0), HTTP_PORT),
                AddressTuple(unresolvable(1), HTTP_PORT),
                AddressTuple(unresolvable(2), HTTP_PORT),
                AddressTuple(unresolvable(3), HTTP_PORT),
            )

        val failure = assertThrows(UnknownHostException::class.java) { wake(details, socket) }

        assertThat(failure).hasMessageThat().contains(unresolvable(3).substringAfter('%'))
        assertThat(socket.attempts).hasSize(4 * WAKE_PORTS)
    }

    private companion object {
        const val BROADCAST = "255.255.255.255"
        const val HTTP_PORT = 47_989
        const val WAKE_PORTS = 7
        const val PORT = 47_989
        const val BIND_TRIES = 50
        const val FIRST_PORT_MIN = 20_000
        const val FIRST_PORT_MAX = 60_000
        const val DYNAMIC_FIRST_OFFSET = 9
        val DYNAMIC_OFFSETS = listOf(0, 1, 2, 4, 12)
        const val BUFFER_BYTES = 1_024
        const val OWN_MAC_RANDOM_BYTES = 5
        const val BYTE_VALUES = 256
        const val HEX_RADIX = 16
        const val MAC_BYTES = 6
        const val MAC_REPETITIONS = 16
        const val NANOS_PER_MS = 1_000_000L
        const val RECEIVE_MS = 3_000
        const val NONE_MS = 150
        const val PROBE_FIRST_MS = 1_000
        const val PROBE_MORE_MS = 300

        /**
         * An IPv6 address with a scope that no interface has: the JDK refuses it at once, with no
         * lookup, and its message names the interface, so that one failure tells from another.
         */
        fun unresolvable(index: Int) = "fe80::1%nonexistent$index"
    }
}
