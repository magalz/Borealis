package io.github.magalz.borealis.discovery

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.wol.WakeOnLanSender
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * What [WakeOnLanSender] does with the broadcast address and with a call that sends nothing: the
 * broadcast goes out before every address, a failed send does not stop the others, and a call where
 * nothing went out throws the failure that came last. A [RecordingWolSocket] takes the packets and
 * fails the ones a test picks, so the result does not depend on the network of the machine and
 * nothing goes out to it.
 *
 * Characterization: these tests pass on the code as converted from Java.
 */
class WakeOnLanSenderBroadcastCharacterizationTest {
    private fun details(vararg addresses: AddressTuple?): ComputerDetails =
        ComputerDetails().apply {
            macAddress = OWN_MAC
            localAddress = addresses.getOrNull(0)
            remoteAddress = addresses.getOrNull(1)
            manualAddress = addresses.getOrNull(2)
            ipv6Address = addresses.getOrNull(3)
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
    private fun noBroadcast() = RecordingWolSocket { attempt ->
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
    fun `a host that does not resolve gets the packets on the broadcast address, once each`() {
        val socket = wake(details(AddressTuple(unresolvable(0), HTTP_PORT)))

        assertThat(socket.attempts.map { it.address }.distinct()).containsExactly(broadcast)
        assertThat(socket.attempts.map { it.port })
            .containsExactly(9, 47_009, 47_998, 47_999, 48_000, 48_002, 48_010)
            .inOrder()
    }

    @Test
    fun `a host that does not resolve does not stop the call when the broadcast went out`() {
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
        val socket = RecordingWolSocket { attempt ->
            if (attempt.port == 9) IOException("refused ${attempt.target}") else null
        }

        val failure = runCatching { wake(details(AddressTuple("127.0.0.1", HTTP_PORT)), socket) }

        assertThat(failure.exceptionOrNull()).isNull()
        assertThat(socket.attempts).hasSize(2 * WAKE_PORTS)
    }

    @Test
    fun `an address that fails entirely does not make the call fail when another address went out`() {
        val socket = RecordingWolSocket { attempt ->
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
        const val OWN_MAC = "02:00:5e:00:00:01"
        const val BROADCAST = "255.255.255.255"
        const val HTTP_PORT = 47_989
        const val WAKE_PORTS = 7

        /**
         * An IPv6 address with a scope that no interface has: the JDK refuses it at once, with no
         * lookup, and its message names the interface, so that one failure tells from another.
         */
        fun unresolvable(index: Int) = "fe80::1%nonexistent$index"
    }
}
