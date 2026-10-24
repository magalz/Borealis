package io.github.magalz.borealis.computers

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.computers.ComputerManagerListener
import com.limelight.computers.ComputerManagerService
import com.limelight.computers.ComputerManagerService.ComputerManagerBinder
import com.limelight.discovery.DiscoveryService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.nvhttp.NvFakeHost
import java.net.InetAddress
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import org.junit.rules.ExternalResource
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

/** How long a test waits for something that another thread does before it fails. */
internal const val WAIT_MS = 30_000L

private const val POLL_MS = 10L
private const val JOIN_MS = 10_000L
private const val NANOS_PER_MS = 1_000_000L

/** The threads of the service, by the start of their names. */
internal const val POLLING_THREAD = "Polling thread for "
internal const val PARALLEL_THREAD = "Parallel Poll - "
internal const val APP_LIST_THREAD = "App list polling thread for "

/** The service type that the discovery service asks the platform for. */
private const val SERVICE_TYPE = "_nvstream._tcp"

/** Waits until [condition] holds, with a message that names [what] when it does not in time. */
internal fun awaitUntil(what: String, timeoutMs: Long = WAIT_MS, condition: () -> Boolean) {
    val deadline = System.nanoTime() + timeoutMs * NANOS_PER_MS
    while (!condition()) {
        check(System.nanoTime() < deadline) { "timed out waiting for $what" }
        Thread.sleep(POLL_MS)
    }
}

/** The threads that are alive and whose names start with [prefix]. */
internal fun liveThreads(prefix: String): List<Thread> =
    Thread.getAllStackTraces().keys.filter { it.isAlive && it.name.startsWith(prefix) }

/** The address of a fake host, as a poll sees it. */
internal val NvFakeHost.address: AddressTuple
    get() = AddressTuple("127.0.0.1", httpPort)

/** The `/serverinfo` body of a host that answers as [name] with the id [uuid]. */
internal fun hostInfo(
    uuid: String,
    name: String = "DEN-PC",
    httpsPort: Int = 47984,
    paired: Boolean = false,
    mac: String = "02:00:00:00:00:01",
): String =
    """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><hostname>$name</hostname>""" +
        "<appversion>7.1.431.-1</appversion><uniqueid>$uuid</uniqueid>" +
        "<HttpsPort>$httpsPort</HttpsPort><mac>$mac</mac>" +
        "<PairStatus>${if (paired) 1 else 0}</PairStatus><currentgame>0</currentgame>" +
        "<state>SUNSHINE_SERVER_FREE</state></root>"

/** Makes the host answer `/serverinfo` with [info]. */
internal fun NvFakeHost.serves(info: String) = answer("/serverinfo", HostReply.xml(info))

/** How many `/serverinfo` requests the host has read. */
internal fun NvFakeHost.serverInfoRequests(): Int = requests.count { it.path == "/serverinfo" }

/** A host as the saved list holds it. */
internal fun saved(
    uuid: String,
    name: String = "DEN-PC",
    manual: AddressTuple? = null,
    cert: X509Certificate? = null,
): ComputerDetails =
    ComputerDetails().also {
        it.uuid = uuid
        it.name = name
        it.manualAddress = manual
        it.serverCert = cert
    }

/** One call of the listener, as it was when it arrived: the details are the live object. */
internal class Update(val details: ComputerDetails) {
    val state: ComputerDetails.State? = details.state
    val name: String? = details.name
    val rawAppList: String? = details.rawAppList
    val thread: Thread = Thread.currentThread()
}

/** A listener that remembers every call. */
internal class Updates : ComputerManagerListener {
    private val all = CopyOnWriteArrayList<Update>()

    override fun notifyComputerUpdated(details: ComputerDetails) {
        all += Update(details)
    }

    val events: List<Update>
        get() = all.toList()

    /** The calls that were made on [thread]. */
    fun on(thread: Thread): List<Update> = all.filter { it.thread === thread }

    fun awaitState(state: ComputerDetails.State, from: Int = 0) =
        awaitUntil("an update with the state $state") {
            all.drop(from).any { it.state == state }
        }

    /** Waits for a call with [state] that did not come from [caller], after the first [from]. */
    fun awaitOtherThan(caller: Thread, state: ComputerDetails.State, from: Int = 0) =
        awaitUntil("an update with the state $state from another thread") {
            all.drop(from).any { it.thread !== caller && it.state == state }
        }
}

/**
 * The computer manager service and the discovery service that it binds, created as Android does,
 * with fake hosts on the loopback interface. Everything that a test starts is stopped and awaited
 * when it ends, so that the threads of one test never show in the next.
 */
class ManagerRule : ExternalResource() {
    val app: Application = ApplicationProvider.getApplicationContext()
    private val looper = shadowOf(Looper.getMainLooper())
    private val hosts = mutableListOf<NvFakeHost>()
    private val gates = mutableListOf<CountDownLatch>()
    private val pollers = mutableListOf<ComputerManagerService.ApplistPoller>()
    private var controller: ServiceController<ComputerManagerService>? = null
    private var destroyed = false
    private lateinit var discoveryBinder: IBinder

    lateinit var service: ComputerManagerService
        private set

    lateinit var binder: ComputerManagerBinder
        private set

    /** A host that answers `/serverinfo` with [info], when there is one. */
    internal fun host(info: String? = null): NvFakeHost =
        NvFakeHost().also {
            hosts += it
            if (info != null) it.serves(info)
        }

    /** A host that answers `/serverinfo` as [name] with the id [uuid], over HTTP and over TLS. */
    internal fun reachable(uuid: String, name: String = "DEN-PC", paired: Boolean = false) =
        host().also { it.serves(hostInfo(uuid, name, httpsPort = it.httpsPort, paired = paired)) }

    /** The address of a host that was there and is gone: nothing listens on its port. */
    internal fun gone(): AddressTuple =
        host().let {
            val address = it.address
            it.close()
            address
        }

    /** An app list poller of the service for [computer]; the end of the test stops it. */
    fun appListPoller(computer: ComputerDetails): ComputerManagerService.ApplistPoller =
        binder.createAppListPoller(computer).also { pollers += it }

    /** A latch that the end of the test opens, so that a handler that waits on it never hangs. */
    fun gate(): CountDownLatch = CountDownLatch(1).also { gates += it }

    /** Saves [computers] in the database that the service reads when it is created. */
    fun seed(vararg computers: ComputerDetails) {
        val db = ComputerDatabaseManager(app)
        try {
            computers.forEach { db.updateComputer(it) }
        } finally {
            db.close()
        }
    }

    /** The hosts that a second connection reads from the database file. */
    fun savedRows(): List<ComputerDetails> {
        val db = ComputerDatabaseManager(app)
        try {
            return db.getAllComputers()
        } finally {
            db.close()
        }
    }

    /** Creates the service, which binds the discovery service; the platform connects it. */
    fun start() {
        val discovery = Robolectric.buildService(DiscoveryService::class.java).create().get()
        discoveryBinder = discovery.onBind(Intent())
        shadowOf(app)
            .setComponentNameAndServiceForBindService(
                ComponentName(app, DiscoveryService::class.java),
                discoveryBinder,
            )
        val created = Robolectric.buildService(ComputerManagerService::class.java).create()
        controller = created
        service = created.get()
        looper.idle()
        binder = service.onBind(Intent()) as ComputerManagerBinder
    }

    /** The platform loses the connection to the discovery service. */
    fun disconnectDiscovery() {
        shadowOf(app)
            .boundServiceConnections
            .single()
            .onServiceDisconnected(ComponentName(app, DiscoveryService::class.java))
    }

    /** The platform connects the discovery service again. */
    fun connectDiscovery() {
        shadowOf(app)
            .boundServiceConnections
            .single()
            .onServiceConnected(ComponentName(app, DiscoveryService::class.java), discoveryBinder)
    }

    /** Destroys the service, as Android does when nobody uses it any more. */
    fun destroy() {
        destroyed = true
        checkNotNull(controller).destroy()
    }

    /** The discovery listeners that the platform holds for the service type. */
    fun discoveries() =
        shadowOf(app.getSystemService(NsdManager::class.java))
            .getDiscoveryListeners(SERVICE_TYPE)
            .orEmpty()

    /** Has the platform find [name] and resolve it to the IPv4 address [address] and [port]. */
    fun findAndResolve(name: String, address: String, port: Int) {
        val platform = shadowOf(app.getSystemService(NsdManager::class.java))
        val found =
            NsdServiceInfo().apply {
                serviceName = name
                serviceType = SERVICE_TYPE
            }
        discoveries().single().onServiceFound(found)
        val resolved =
            NsdServiceInfo().apply {
                serviceName = name
                this.port = port
                hostAddresses = listOf(InetAddress.getByName(address))
            }
        platform.getServiceInfoCallbacks(found).orEmpty().single().onServiceUpdated(resolved)
    }

    override fun after() {
        pollers.forEach { it.stop() }
        if (::binder.isInitialized) {
            binder.stopPolling()
        }
        gates.forEach { it.countDown() }
        hosts.forEach { it.close() }
        if (controller != null && !destroyed) {
            checkNotNull(controller).destroy()
        }
        for (prefix in listOf(POLLING_THREAD, PARALLEL_THREAD, APP_LIST_THREAD)) {
            liveThreads(prefix).forEach { it.join(JOIN_MS) }
        }
    }
}
