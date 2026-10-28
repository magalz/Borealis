package io.github.magalz.borealis.discovery

import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.CopyOnWriteArrayList
import javax.jmdns.JmDNS
import javax.jmdns.JmmDNS
import javax.jmdns.NetworkTopologyDiscovery
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.ServiceTypeListener
import javax.jmdns.impl.ServiceInfoImpl
import org.junit.rules.ExternalResource

/** Remembers what discovery reports. */
class RecordingDiscoveryListener : MdnsDiscoveryListener {
    val added = CopyOnWriteArrayList<MdnsComputer>()
    val failures = CopyOnWriteArrayList<Exception>()

    override fun notifyComputerAdded(computer: MdnsComputer) {
        added += computer
    }

    override fun notifyDiscoveryFailure(e: Exception) {
        failures += e
    }
}

/** An IPv4 address from its text; a numeric address is parsed, never looked up. */
fun ip4(text: String): Inet4Address = InetAddress.getByName(text) as Inet4Address

/** An IPv6 address from its text; a numeric address is parsed, never looked up. */
fun ip6(text: String): Inet6Address = InetAddress.getByName(text) as Inet6Address

/**
 * A resolved jmDNS service. Only what the agent reads is real: the name, the port and the
 * addresses.
 */
fun serviceInfo(
    name: String,
    port: Int,
    v4: List<Inet4Address> = emptyList(),
    v6: List<Inet6Address> = emptyList(),
): ServiceInfo =
    object : ServiceInfoImpl("_nvstream._tcp.local.", name, "", port, 0, 0, false, "") {
        override fun getInet4Addresses(): Array<Inet4Address> = v4.toTypedArray()

        override fun getInet6Addresses(): Array<Inet6Address> = v6.toTypedArray()
    }

/** What the agent asked a jmDNS instance for. */
data class InfoRequest(val type: String, val name: String, val timeoutMs: Long)

/**
 * A jmDNS instance that knows the services in [known] and remembers the requests of the agent. Any
 * other call fails, so that an agent that starts to ask something else is noticed.
 */
class FakeJmDns(private val known: Map<String, ServiceInfo> = emptyMap()) : JmDNS() {
    val requests = CopyOnWriteArrayList<InfoRequest>()

    override fun getServiceInfo(type: String, name: String, timeout: Long): ServiceInfo? {
        requests += InfoRequest(type, name, timeout)
        return known[name]
    }

    private fun unused(): Nothing = throw UnsupportedOperationException("not used by the agent")

    override fun getName(): String = unused()

    override fun getHostName(): String = unused()

    override fun getInetAddress(): InetAddress = unused()

    @Suppress("OVERRIDE_DEPRECATION") override fun getInterface(): InetAddress = unused()

    override fun getServiceInfo(type: String, name: String): ServiceInfo = unused()

    override fun getServiceInfo(type: String, name: String, persistent: Boolean): ServiceInfo =
        unused()

    override fun getServiceInfo(
        type: String,
        name: String,
        persistent: Boolean,
        timeout: Long,
    ): ServiceInfo = unused()

    override fun requestServiceInfo(type: String, name: String) = unused()

    override fun requestServiceInfo(type: String, name: String, persistent: Boolean) = unused()

    override fun requestServiceInfo(type: String, name: String, timeout: Long) = unused()

    override fun requestServiceInfo(
        type: String,
        name: String,
        persistent: Boolean,
        timeout: Long,
    ) = unused()

    override fun addServiceTypeListener(listener: ServiceTypeListener) = unused()

    override fun removeServiceTypeListener(listener: ServiceTypeListener) = unused()

    override fun addServiceListener(type: String, listener: ServiceListener) = unused()

    override fun removeServiceListener(type: String, listener: ServiceListener) = unused()

    override fun registerService(info: ServiceInfo) = unused()

    override fun unregisterService(info: ServiceInfo) = unused()

    override fun unregisterAllServices() = unused()

    override fun registerServiceType(type: String): Boolean = unused()

    @Suppress("OVERRIDE_DEPRECATION") override fun printServices() = unused()

    override fun list(type: String): Array<ServiceInfo> = unused()

    override fun list(type: String, timeout: Long): Array<ServiceInfo> = unused()

    override fun listBySubtype(type: String): Map<String, Array<ServiceInfo>> = unused()

    override fun listBySubtype(type: String, timeout: Long): Map<String, Array<ServiceInfo>> =
        unused()

    override fun getDelegate(): JmDNS.Delegate = unused()

    override fun setDelegate(delegate: JmDNS.Delegate): JmDNS.Delegate = unused()

    override fun close() = unused()
}

/** A jmDNS service event with the instance that raised it and the service it names. */
class FakeServiceEvent(private val dns: JmDNS, private val info: ServiceInfo) : ServiceEvent(dns) {
    override fun getDNS(): JmDNS = dns

    override fun getType(): String = info.type

    override fun getName(): String = info.name

    override fun getInfo(): ServiceInfo = info
}

/** The first network interface of this machine that [predicate] accepts, if there is one. */
fun interfaceOf(predicate: (NetworkInterface) -> Boolean): NetworkInterface? =
    NetworkInterface.getNetworkInterfaces()?.toList()?.firstOrNull(predicate)

/**
 * Keeps jmDNS off the network. The agents ask `JmmDNS` for the interfaces to listen on; this rule
 * makes that list empty, so a discovery thread runs its whole loop and opens no socket. Put it on
 * every test that starts a `JmDNSDiscoveryAgent`.
 */
class NoMdnsNetwork : ExternalResource() {
    private var original: NetworkTopologyDiscovery.Factory.ClassDelegate? = null

    private val noInterfaces =
        object : NetworkTopologyDiscovery {
            override fun getInetAddresses(): Array<InetAddress> = emptyArray()

            override fun useInetAddress(
                networkInterface: NetworkInterface,
                interfaceAddress: InetAddress,
            ) = false

            override fun lockInetAddress(interfaceAddress: InetAddress) = Unit

            override fun unlockInetAddress(interfaceAddress: InetAddress) = Unit
        }

    override fun before() {
        // The agent installs its own delegate when it loads; load it first so that ours stays.
        Class.forName(JmDNSDiscoveryAgent::class.java.name, true, javaClass.classLoader)
        original = NetworkTopologyDiscovery.Factory.classDelegate()
        NetworkTopologyDiscovery.Factory.setClassDelegate { noInterfaces }
        // jmDNS keeps the first instance that it makes; this fails before any thread starts if an
        // earlier use made the real one.
        check(NetworkTopologyDiscovery.Factory.getInstance().inetAddresses.isEmpty()) {
            "jmDNS already holds a topology that lists real interfaces"
        }
    }

    override fun after() {
        try {
            JmmDNS.Factory.close()
        } catch (ignored: IOException) {
            // Nothing is left to close.
        }
        NetworkTopologyDiscovery.Factory.setClassDelegate(original)
    }
}
