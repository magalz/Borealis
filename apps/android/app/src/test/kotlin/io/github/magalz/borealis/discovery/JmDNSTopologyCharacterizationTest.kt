package io.github.magalz.borealis.discovery

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import java.net.InetAddress
import javax.jmdns.NetworkTopologyDiscovery
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The network interfaces that jmDNS listens on. The agent replaces the jmDNS rule for them when it
 * loads, because some devices say that an interface has no multicast when it has. The interface
 * checks need interfaces that the machine has, so a test that finds none is skipped.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class JmDNSTopologyCharacterizationTest {
    private val topology = JmDNSDiscoveryAgent.MyNetworkTopologyDiscovery()
    private val anyAddress = InetAddress.getLoopbackAddress()

    @Test
    fun `loading the agent makes jmDNS use the topology of the agent`() {
        Class.forName(JmDNSDiscoveryAgent::class.java.name, true, javaClass.classLoader)

        val made = NetworkTopologyDiscovery.Factory.classDelegate().newNetworkTopologyDiscovery()

        // By name: jmDNS is shared by the Robolectric sandboxes, and each one has its own agent
        // class.
        assertThat(made.javaClass.name)
            .isEqualTo(JmDNSDiscoveryAgent.MyNetworkTopologyDiscovery::class.java.name)
    }

    @Test
    fun `an interface that is up and not a loopback is used`() {
        val up = interfaceOf { it.isUp && !it.isLoopback }
        assumeNotNull(up)

        assertThat(topology.useInetAddress(up, anyAddress)).isTrue()
    }

    @Test
    fun `a loopback interface is not used`() {
        val loopback = interfaceOf { it.isLoopback }
        assumeNotNull(loopback)

        assertThat(topology.useInetAddress(loopback, anyAddress)).isFalse()
    }

    @Test
    fun `an interface that is down is not used`() {
        val down = interfaceOf { !it.isUp }
        assumeNotNull(down)

        assertThat(topology.useInetAddress(down, anyAddress)).isFalse()
    }

    @Test
    fun `a missing interface is not used and does not throw`() {
        assertThat(topology.useInetAddress(null, anyAddress)).isFalse()
    }
}
