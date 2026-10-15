package io.github.magalz.borealis.library

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.PairingManager.PairState
import org.junit.Test

class ComputerDetailsCopyTest {
    private fun full(): ComputerDetails =
        details(
                uuid = "a",
                name = "Den PC",
                state = ComputerDetails.State.ONLINE,
                pairState = PairState.PAIRED,
                runningGameId = 7,
                mac = "AA:BB:CC:DD:EE:FF",
                rawAppList = APP_LIST,
            )
            .also {
                // A loopback local address is something the upstream copy constructor drops.
                it.localAddress = AddressTuple("127.0.0.1", 47989)
                it.remoteAddress = AddressTuple("203.0.113.9", 47989)
                it.manualAddress = AddressTuple("pc.local", 47989)
                it.ipv6Address = AddressTuple("fe80::1", 47989)
                it.serverCert = testCertificate()
                it.externalPort = 47999
                it.nvidiaServer = true
            }

    @Test
    fun `a snapshot has every field of the details`() {
        val source = full()

        val copy = source.snapshot()

        assertThat(copy).isNotSameInstanceAs(source)
        assertThat(copy.uuid).isEqualTo("a")
        assertThat(copy.name).isEqualTo("Den PC")
        assertThat(copy.state).isEqualTo(ComputerDetails.State.ONLINE)
        assertThat(copy.pairState).isEqualTo(PairState.PAIRED)
        assertThat(copy.runningGameId).isEqualTo(7)
        assertThat(copy.macAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(copy.rawAppList).isEqualTo(APP_LIST)
        assertThat(copy.activeAddress).isEqualTo(source.activeAddress)
        assertThat(copy.localAddress).isEqualTo(AddressTuple("127.0.0.1", 47989))
        assertThat(copy.remoteAddress).isEqualTo(source.remoteAddress)
        assertThat(copy.manualAddress).isEqualTo(source.manualAddress)
        assertThat(copy.ipv6Address).isEqualTo(source.ipv6Address)
        assertThat(copy.serverCert).isEqualTo(source.serverCert)
        assertThat(copy.httpsPort).isEqualTo(47984)
        assertThat(copy.externalPort).isEqualTo(47999)
        assertThat(copy.nvidiaServer).isTrue()
    }

    @Test
    fun `the address tuples of a snapshot are not the ones that the service mutates`() {
        val source = full()

        val copy = source.snapshot()
        source.activeAddress!!.port = 1
        source.localAddress!!.port = 2
        source.remoteAddress!!.port = 3
        source.manualAddress!!.port = 4
        source.ipv6Address!!.port = 5

        assertThat(copy.activeAddress!!.port).isEqualTo(47989)
        assertThat(copy.localAddress!!.port).isEqualTo(47989)
        assertThat(copy.remoteAddress!!.port).isEqualTo(47989)
        assertThat(copy.manualAddress!!.port).isEqualTo(47989)
        assertThat(copy.ipv6Address!!.port).isEqualTo(47989)
    }

    @Test
    fun `missing addresses stay missing`() {
        val copy = ComputerDetails().snapshot()

        assertThat(copy.activeAddress).isNull()
        assertThat(copy.localAddress).isNull()
        assertThat(copy.state).isEqualTo(ComputerDetails.State.UNKNOWN)
    }
}
