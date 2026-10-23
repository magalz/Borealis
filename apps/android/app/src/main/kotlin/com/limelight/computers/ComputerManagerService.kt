package com.limelight.computers

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.limelight.LimeLog
import com.limelight.binding.PlatformBinding
import com.limelight.discovery.DiscoveryService
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import com.limelight.utils.CacheHelper
import com.limelight.utils.NetHelper
import com.limelight.utils.ServerHelper
import java.io.IOException
import java.io.StringReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import org.xmlpull.v1.XmlPullParserException

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
// Object.wait and Object.notify are not members of Kotlin's Any, so the monitors are cast to
// java.lang.Object, as the Java used them.
@Suppress("TooManyFunctions", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class ComputerManagerService : Service() {
    private val binder = ComputerManagerBinder()

    private lateinit var dbManager: ComputerDatabaseManager
    private val dbRefCount = AtomicInteger(0)

    private lateinit var idManager: IdentityManager
    private val pollingTuples = LinkedList<PollingTuple>()
    private var listener: ComputerManagerListener? = null
    private val activePolls = AtomicInteger(0)
    private var pollingActive = false
    private val defaultNetworkLock: Lock = ReentrantLock()

    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    private var discoveryBinder: DiscoveryService.DiscoveryBinder? = null
    private val discoveryServiceConnection: ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, binder: IBinder) {
                synchronized(this) {
                    val privateBinder = binder as DiscoveryService.DiscoveryBinder

                    // Set us as the event listener
                    privateBinder.setListener(createDiscoveryListener())

                    // Signal a possible waiter that we're all setup
                    discoveryBinder = privateBinder
                    (this as Object).notifyAll()
                }
            }

            override fun onServiceDisconnected(className: ComponentName) {
                discoveryBinder = null
            }
        }

    // Returns true if the details object was modified
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
    @Throws(InterruptedException::class)
    private fun runPoll(details: ComputerDetails, newPc: Boolean, offlineCount: Int): Boolean {
        if (!getLocalDatabaseReference()) {
            return false
        }

        val pollTriesBeforeOffline =
            if (details.state == ComputerDetails.State.UNKNOWN) {
                INITIAL_POLL_TRIES
            } else {
                OFFLINE_POLL_TRIES
            }

        activePolls.incrementAndGet()

        // Poll the machine
        try {
            if (!pollComputer(details)) {
                if (!newPc && offlineCount < pollTriesBeforeOffline) {
                    // Return without calling the listener
                    releaseLocalDatabaseReference()
                    return false
                }

                details.state = ComputerDetails.State.OFFLINE
            }
        } catch (e: InterruptedException) {
            releaseLocalDatabaseReference()
            throw e
        } finally {
            activePolls.decrementAndGet()
        }

        // If it's online, update our persistent state
        if (details.state == ComputerDetails.State.ONLINE) {
            val existingComputer = dbManager.getComputerByUUID(details.uuid)

            // Check if it's in the database because it could have been
            // removed after this was issued
            if (!newPc && existingComputer == null) {
                // It's gone
                releaseLocalDatabaseReference()
                return false
            }

            // If we already have an entry for this computer in the DB, we must
            // combine the existing data with this new data (which may be partially available
            // due to detecting the PC via mDNS) without the saved external address. If we
            // write to the DB without doing this first, we can overwrite our existing data.
            if (existingComputer != null) {
                existingComputer.update(details)
                dbManager.updateComputer(existingComputer)
            } else {
                try {
                    // If the active address is a site-local address (RFC 1918),
                    // then use STUN to populate the external address field if
                    // it's not set already.
                    if (details.remoteAddress == null) {
                        // The Java dereferenced the active address here, so a host without one
                        // threw a NullPointerException.
                        val addr =
                            InetAddress.getByName(checkNotNull(details.activeAddress).address)
                        if (addr.isSiteLocalAddress) {
                            populateExternalAddress(details)
                        }
                    }
                } catch (ignored: UnknownHostException) {}

                dbManager.updateComputer(details)
            }
        }

        // Don't call the listener if this is a failed lookup of a new PC
        if (!newPc || details.state == ComputerDetails.State.ONLINE) {
            listener?.notifyComputerUpdated(details)
        }

        releaseLocalDatabaseReference()
        return true
    }

    private fun createPollingThread(tuple: PollingTuple): Thread {
        val t =
            object : Thread() {
                override fun run() {
                    var offlineCount = 0
                    while (!isInterrupted && pollingActive && tuple.thread === this) {
                        try {
                            // Only allow one request to the machine at a time
                            synchronized(tuple.networkLock) {
                                // Check if this poll has modified the details
                                if (!runPoll(tuple.computer, false, offlineCount)) {
                                    LimeLog.warning(
                                        "${tuple.computer.name} is offline (try $offlineCount)"
                                    )
                                    offlineCount++
                                } else {
                                    tuple.lastSuccessfulPollMs = SystemClock.elapsedRealtime()
                                    offlineCount = 0
                                }
                            }

                            // Wait until the next polling interval
                            Thread.sleep(SERVERINFO_POLLING_PERIOD_MS.toLong())
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                }
            }
        t.name = "Polling thread for " + tuple.computer.name
        return t
    }

    inner class ComputerManagerBinder : Binder() {
        fun startPolling(listener: ComputerManagerListener) {
            // Polling is active
            pollingActive = true

            // Set the listener
            this@ComputerManagerService.listener = listener

            // Start mDNS autodiscovery too
            checkNotNull(discoveryBinder).startDiscovery(MDNS_QUERY_PERIOD_MS)

            synchronized(pollingTuples) {
                for (tuple in pollingTuples) {
                    // Enforce the poll data TTL
                    if (
                        SystemClock.elapsedRealtime() - tuple.lastSuccessfulPollMs >
                            POLL_DATA_TTL_MS
                    ) {
                        LimeLog.info("Timing out polled state for " + tuple.computer.name)
                        tuple.computer.state = ComputerDetails.State.UNKNOWN
                    }

                    // Report this computer initially
                    listener.notifyComputerUpdated(tuple.computer)

                    // This polling thread might already be there
                    if (tuple.thread == null) {
                        val polling = createPollingThread(tuple)
                        tuple.thread = polling
                        polling.start()
                    }
                }
            }
        }

        // The stack trace goes to the standard error stream, as in the inherited Java. Logging it
        // instead is a behaviour change (Sprint 12 backlog).
        @Suppress("PrintStackTrace")
        fun waitForReady() {
            synchronized(discoveryServiceConnection) {
                try {
                    while (discoveryBinder == null) {
                        // Wait for the bind notification
                        (discoveryServiceConnection as Object).wait(DISCOVERY_WAIT_MS)
                    }
                } catch (e: InterruptedException) {
                    e.printStackTrace()

                    // InterruptedException clears the thread's interrupt status. Since we can't
                    // handle that here, we will re-interrupt the thread to set the interrupt
                    // status back to true.
                    Thread.currentThread().interrupt()
                }
            }
        }

        // The stack trace goes to the standard error stream, as in the inherited Java. Logging it
        // instead is a behaviour change (Sprint 12 backlog).
        @Suppress("PrintStackTrace")
        fun waitForPollingStopped() {
            while (activePolls.get() != 0) {
                try {
                    Thread.sleep(POLLING_STOPPED_WAIT_MS)
                } catch (e: InterruptedException) {
                    e.printStackTrace()

                    // InterruptedException clears the thread's interrupt status. Since we can't
                    // handle that here, we will re-interrupt the thread to set the interrupt
                    // status back to true.
                    Thread.currentThread().interrupt()
                }
            }
        }

        @Throws(InterruptedException::class)
        fun addComputerBlocking(fakeDetails: ComputerDetails): Boolean =
            this@ComputerManagerService.addComputerBlocking(fakeDetails)

        fun removeComputer(computer: ComputerDetails) {
            this@ComputerManagerService.removeComputer(computer)
        }

        fun stopPolling() {
            // Just call the unbind handler to cleanup
            this@ComputerManagerService.onUnbind(null)
        }

        fun createAppListPoller(computer: ComputerDetails): ApplistPoller = ApplistPoller(computer)

        val uniqueId: String
            get() = idManager.uniqueId

        fun getComputer(uuid: String): ComputerDetails? {
            synchronized(pollingTuples) {
                for (tuple in pollingTuples) {
                    if (uuid == tuple.computer.uuid) {
                        return tuple.computer
                    }
                }
            }

            return null
        }

        fun invalidateStateForComputer(uuid: String) {
            synchronized(pollingTuples) {
                for (tuple in pollingTuples) {
                    if (uuid == tuple.computer.uuid) {
                        // We need the network lock to prevent a concurrent poll
                        // from wiping this change out
                        synchronized(tuple.networkLock) {
                            tuple.computer.state = ComputerDetails.State.UNKNOWN
                        }
                    }
                }
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Stop mDNS autodiscovery
        discoveryBinder?.stopDiscovery()

        // Stop polling
        pollingActive = false
        synchronized(pollingTuples) {
            for (tuple in pollingTuples) {
                val polling = tuple.thread
                if (polling != null) {
                    // Interrupt and remove the thread
                    polling.interrupt()
                    tuple.thread = null
                }
            }
        }

        // Remove the listener
        listener = null

        return false
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    // The Java called getAllNetworks, which is deprecated since Android 12 and is still the call
    // that finds the network under a VPN.
    @Suppress("DEPRECATION", "NestedBlockDepth")
    private fun populateExternalAddress(details: ComputerDetails) {
        var boundToNetwork = false
        val activeNetworkIsVpn = NetHelper.isActiveNetworkVpn(this)
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check if we're currently connected to a VPN which may send our
        // STUN request from an unexpected interface
        if (activeNetworkIsVpn) {
            // Acquire the default network lock since we could be changing global process state
            defaultNetworkLock.lock()

            // On Lollipop or later, we can bind our process to the underlying interface
            // to ensure our STUN request goes out on that interface or not at all (which is
            // preferable to getting a VPN endpoint address back).
            val networks = connMgr.allNetworks
            for (net in networks) {
                val netCaps = connMgr.getNetworkCapabilities(net)
                if (netCaps != null) {
                    if (
                        !netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                            !netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    ) {
                        // This network looks like an underlying multicast-capable transport,
                        // so let's guess that it's probably where our mDNS response came from.
                        if (connMgr.bindProcessToNetwork(net)) {
                            boundToNetwork = true
                            break
                        }
                    }
                }
            }

            // Perform the STUN request if we're not on a VPN or if we bound to a network
            if (!activeNetworkIsVpn || boundToNetwork) {
                val stunResolvedAddress =
                    NvConnection.findExternalAddressForMdns(STUN_SERVER, STUN_PORT)
                if (stunResolvedAddress != null) {
                    // We don't know for sure what the external port is, so we will have to guess.
                    // When we contact the PC (if we haven't already), it will update the port.
                    details.remoteAddress =
                        ComputerDetails.AddressTuple(
                            stunResolvedAddress,
                            details.guessExternalPort(),
                        )
                }
            }

            // Unbind from the network
            if (boundToNetwork) {
                connMgr.bindProcessToNetwork(null)
            }

            // Unlock the network state
            if (activeNetworkIsVpn) {
                defaultNetworkLock.unlock()
            }
        }
    }

    // The stack traces go to the standard error stream, as in the inherited Java. Logging them
    // instead is a behaviour change (Sprint 12 backlog).
    @Suppress("PrintStackTrace")
    private fun createDiscoveryListener(): MdnsDiscoveryListener =
        object : MdnsDiscoveryListener {
            override fun notifyComputerAdded(computer: MdnsComputer) {
                val details = ComputerDetails()

                // Populate the computer template with mDNS info
                val localAddress = computer.localAddress
                if (localAddress != null) {
                    details.localAddress =
                        ComputerDetails.AddressTuple(localAddress.hostAddress, computer.port)

                    // Since we're on the same network, we can use STUN to find
                    // our WAN address, which is also very likely the WAN address
                    // of the PC. We can use this later to connect remotely.
                    if (localAddress is Inet4Address) {
                        populateExternalAddress(details)
                    }
                }
                val ipv6Address = computer.ipv6Address
                if (ipv6Address != null) {
                    details.ipv6Address =
                        ComputerDetails.AddressTuple(ipv6Address.hostAddress, computer.port)
                }

                try {
                    // Kick off a blocking serverinfo poll on this machine
                    if (!addComputerBlocking(details)) {
                        LimeLog.warning("Auto-discovered PC failed to respond: $details")
                    }
                } catch (e: InterruptedException) {
                    e.printStackTrace()

                    // InterruptedException clears the thread's interrupt status. Since we can't
                    // handle that here, we will re-interrupt the thread to set the interrupt
                    // status back to true.
                    Thread.currentThread().interrupt()
                }
            }

            override fun notifyDiscoveryFailure(e: Exception) {
                LimeLog.severe("mDNS discovery failed")
                e.printStackTrace()
            }
        }

    private fun addTuple(details: ComputerDetails) {
        synchronized(pollingTuples) {
            for (tuple in pollingTuples) {
                // Check if this is the same computer. The Java called equals on the saved id, so
                // a saved computer without one threw a NullPointerException.
                if (checkNotNull(tuple.computer.uuid) == details.uuid) {
                    // Update the saved computer with potentially new details
                    tuple.computer.update(details)

                    // Start a polling thread if polling is active
                    if (pollingActive && tuple.thread == null) {
                        val polling = createPollingThread(tuple)
                        tuple.thread = polling
                        polling.start()
                    }

                    // Found an entry so we're done
                    return
                }
            }

            // If we got here, we didn't find an entry
            val tuple = PollingTuple(details, null)
            if (pollingActive) {
                tuple.thread = createPollingThread(tuple)
            }
            pollingTuples.add(tuple)
            tuple.thread?.start()
        }
    }

    @Throws(InterruptedException::class)
    fun addComputerBlocking(fakeDetails: ComputerDetails): Boolean {
        // Block while we try to fill the details

        // We cannot use runPoll() here because it will attempt to persist the state of the machine
        // in the database, which would be bad because we don't have our pinned cert loaded yet.
        if (pollComputer(fakeDetails)) {
            // See if we have record of this PC to pull its pinned cert
            synchronized(pollingTuples) {
                for (tuple in pollingTuples) {
                    if (checkNotNull(tuple.computer.uuid) == fakeDetails.uuid) {
                        fakeDetails.serverCert = tuple.computer.serverCert
                        break
                    }
                }
            }

            // Poll again, possibly with the pinned cert, to get accurate pairing information.
            // This will insert the host into the database too.
            runPoll(fakeDetails, true, 0)
        }

        // If the machine is reachable, it was successful
        if (fakeDetails.state == ComputerDetails.State.ONLINE) {
            LimeLog.info("New PC (${fakeDetails.name}) is UUID ${fakeDetails.uuid}")

            // Start a polling thread for this machine
            addTuple(fakeDetails)
            return true
        } else {
            return false
        }
    }

    fun removeComputer(computer: ComputerDetails) {
        if (!getLocalDatabaseReference()) {
            return
        }

        // Remove it from the database
        dbManager.deleteComputer(computer)

        synchronized(pollingTuples) {
            // Remove the computer from the computer list
            for (tuple in pollingTuples) {
                if (checkNotNull(tuple.computer.uuid) == computer.uuid) {
                    val polling = tuple.thread
                    if (polling != null) {
                        // Interrupt the thread on this entry
                        polling.interrupt()
                        tuple.thread = null
                    }
                    pollingTuples.remove(tuple)
                    break
                }
            }
        }

        releaseLocalDatabaseReference()
    }

    private fun getLocalDatabaseReference(): Boolean {
        if (dbRefCount.get() == 0) {
            return false
        }

        dbRefCount.incrementAndGet()
        return true
    }

    private fun releaseLocalDatabaseReference() {
        if (dbRefCount.decrementAndGet() == 0) {
            dbManager.close()
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    // The stack trace goes to the standard error stream, as in the inherited Java. Logging it
    // instead is a behaviour change.
    @Suppress("PrintStackTrace", "ReturnCount")
    private fun tryPollIp(
        details: ComputerDetails,
        address: ComputerDetails.AddressTuple,
    ): ComputerDetails? {
        try {
            // If the current address's port number matches the active address's port number, we
            // can also assume the HTTPS port will also match. This assumption is currently safe
            // because Sunshine sets all ports as offsets from the base HTTP port and doesn't allow
            // custom HttpsPort responses for WAN vs LAN.
            val activeAddress = details.activeAddress
            val portMatchesActiveAddress =
                details.state == ComputerDetails.State.ONLINE &&
                    activeAddress != null &&
                    address.port == activeAddress.port

            val http =
                NvHTTP(
                    address,
                    if (portMatchesActiveAddress) details.httpsPort else 0,
                    idManager.uniqueId,
                    details.serverCert,
                    PlatformBinding.getCryptoProvider(this@ComputerManagerService),
                )

            // If this PC is currently online at this address, extend the timeouts to allow more
            // time for the PC to respond.
            val isLikelyOnline =
                details.state == ComputerDetails.State.ONLINE && address == details.activeAddress

            val newDetails = http.getComputerDetails(isLikelyOnline)

            // Check if this is the PC we expected
            if (newDetails.uuid == null) {
                LimeLog.severe("Polling returned no UUID!")
                return null
            } else if (details.uuid != null && details.uuid != newDetails.uuid) {
                // details.uuid can be null on initial PC add
                // We got the wrong PC!
                LimeLog.info("Polling returned the wrong PC!")
                return null
            }

            return newDetails
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
            return null
        } catch (ignored: IOException) {
            return null
        }
    }

    private class ParallelPollTuple(
        val address: ComputerDetails.AddressTuple?,
        val existingDetails: ComputerDetails,
    ) {
        var complete = false
        var pollingThread: Thread? = null
        var returnedDetails: ComputerDetails? = null

        fun interrupt() {
            pollingThread?.interrupt()
        }
    }

    private fun startParallelPollThread(
        tuple: ParallelPollTuple,
        uniqueAddresses: HashSet<ComputerDetails.AddressTuple>,
    ) {
        // Don't bother starting a polling thread for an address that doesn't exist
        // or if the address has already been polled with an earlier tuple
        val address = tuple.address
        if (address == null || !uniqueAddresses.add(address)) {
            tuple.complete = true
            tuple.returnedDetails = null
            return
        }

        val polling =
            object : Thread() {
                override fun run() {
                    val details = tryPollIp(tuple.existingDetails, address)

                    synchronized(tuple) {
                        tuple.complete = true // Done
                        tuple.returnedDetails = details // Polling result

                        (tuple as Object).notify()
                    }
                }
            }
        tuple.pollingThread = polling
        polling.name = "Parallel Poll - $address - ${tuple.existingDetails.name}"
        polling.start()
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    @Throws(InterruptedException::class)
    private fun parallelPollPc(details: ComputerDetails): ComputerDetails? {
        val localInfo = ParallelPollTuple(details.localAddress, details)
        val manualInfo = ParallelPollTuple(details.manualAddress, details)
        val remoteInfo = ParallelPollTuple(details.remoteAddress, details)
        val ipv6Info = ParallelPollTuple(details.ipv6Address, details)

        // These must be started in order of precedence for the deduplication algorithm
        // to result in the correct behavior.
        val uniqueAddresses = HashSet<ComputerDetails.AddressTuple>()
        startParallelPollThread(localInfo, uniqueAddresses)
        startParallelPollThread(manualInfo, uniqueAddresses)
        startParallelPollThread(remoteInfo, uniqueAddresses)
        startParallelPollThread(ipv6Info, uniqueAddresses)

        try {
            // Check local first
            synchronized(localInfo) {
                while (!localInfo.complete) {
                    (localInfo as Object).wait()
                }

                val returned = localInfo.returnedDetails
                if (returned != null) {
                    returned.activeAddress = localInfo.address
                    return returned
                }
            }

            // Now manual
            synchronized(manualInfo) {
                while (!manualInfo.complete) {
                    (manualInfo as Object).wait()
                }

                val returned = manualInfo.returnedDetails
                if (returned != null) {
                    returned.activeAddress = manualInfo.address
                    return returned
                }
            }

            // Now remote IPv4
            synchronized(remoteInfo) {
                while (!remoteInfo.complete) {
                    (remoteInfo as Object).wait()
                }

                val returned = remoteInfo.returnedDetails
                if (returned != null) {
                    returned.activeAddress = remoteInfo.address
                    return returned
                }
            }

            // Now global IPv6
            synchronized(ipv6Info) {
                while (!ipv6Info.complete) {
                    (ipv6Info as Object).wait()
                }

                val returned = ipv6Info.returnedDetails
                if (returned != null) {
                    returned.activeAddress = ipv6Info.address
                    return returned
                }
            }
        } finally {
            // Stop any further polling if we've found a working address or we've been
            // interrupted by an attempt to stop polling.
            localInfo.interrupt()
            manualInfo.interrupt()
            remoteInfo.interrupt()
            ipv6Info.interrupt()
        }

        return null
    }

    @Throws(InterruptedException::class)
    private fun pollComputer(details: ComputerDetails): Boolean {
        // Poll all addresses in parallel to speed up the process
        LimeLog.info(
            "Starting parallel poll for ${details.name} (${details.localAddress}, " +
                "${details.remoteAddress}, ${details.manualAddress}, ${details.ipv6Address})"
        )
        val polledDetails = parallelPollPc(details)
        LimeLog.info("Parallel poll for ${details.name} returned address: ${details.activeAddress}")

        if (polledDetails != null) {
            details.update(polledDetails)
            return true
        } else {
            return false
        }
    }

    override fun onCreate() {
        // Bind to the discovery service
        bindService(
            Intent(this, DiscoveryService::class.java),
            discoveryServiceConnection,
            Context.BIND_AUTO_CREATE,
        )

        // Lookup or generate this device's UID
        idManager = IdentityManager(this)

        // Initialize the DB
        dbManager = ComputerDatabaseManager(this)
        dbRefCount.set(1)

        // Grab known machines into our computer list
        if (!getLocalDatabaseReference()) {
            return
        }

        for (computer in dbManager.getAllComputers()) {
            // Add tuples for each computer
            addTuple(computer)
        }

        releaseLocalDatabaseReference()

        // Monitor for network changes to invalidate our PC state
        networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    LimeLog.info("Resetting PC state for new available network")
                    synchronized(pollingTuples) {
                        for (tuple in pollingTuples) {
                            tuple.computer.state = ComputerDetails.State.UNKNOWN
                            listener?.notifyComputerUpdated(tuple.computer)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    LimeLog.info("Offlining PCs due to network loss")
                    synchronized(pollingTuples) {
                        for (tuple in pollingTuples) {
                            tuple.computer.state = ComputerDetails.State.OFFLINE
                            listener?.notifyComputerUpdated(tuple.computer)
                        }
                    }
                }
            }

        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connMgr.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onDestroy() {
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connMgr.unregisterNetworkCallback(networkCallback)

        if (discoveryBinder != null) {
            // Unbind from the discovery service
            unbindService(discoveryServiceConnection)
        }

        // FIXME Should await termination here but we have timeout issues in HttpURLConnection

        // Remove the initial DB reference
        releaseLocalDatabaseReference()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class ApplistPoller(private val computer: ComputerDetails) {
        private var thread: Thread? = null
        private val pollEvent = Any()
        private var receivedAppList = false

        fun pollNow() {
            synchronized(pollEvent) { (pollEvent as Object).notify() }
        }

        private fun waitPollingDelay(): Boolean {
            try {
                synchronized(pollEvent) {
                    if (receivedAppList) {
                        // If we've already reported an app list successfully,
                        // wait the full polling period
                        (pollEvent as Object).wait(APPLIST_POLLING_PERIOD_MS.toLong())
                    } else {
                        // If we've failed to get an app list so far, retry much earlier
                        (pollEvent as Object).wait(APPLIST_FAILED_POLLING_RETRY_MS.toLong())
                    }
                }
            } catch (ignored: InterruptedException) {
                return false
            }

            val polling = thread
            return polling != null && !polling.isInterrupted
        }

        private fun getPollingTuple(details: ComputerDetails): PollingTuple? {
            synchronized(pollingTuples) {
                for (tuple in pollingTuples) {
                    // The Java called equals on the id of the computer, which the poller has
                    // checked, so a computer without one threw a NullPointerException.
                    if (checkNotNull(details.uuid) == tuple.computer.uuid) {
                        return tuple
                    }
                }
            }

            return null
        }

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
        // The stack traces go to the standard error stream, as in the inherited Java. Logging them
        // instead is a behaviour change.
        @Suppress(
            "LongMethod",
            "CyclomaticComplexMethod",
            "NestedBlockDepth",
            "PrintStackTrace",
            "LoopWithTooManyJumpStatements",
        )
        fun start() {
            val polling =
                object : Thread() {
                    override fun run() {
                        var emptyAppListResponses = 0
                        do {
                            // Can't poll if it's not online or paired
                            if (
                                computer.state != ComputerDetails.State.ONLINE ||
                                    computer.pairState != PairingManager.PairState.PAIRED
                            ) {
                                listener?.notifyComputerUpdated(computer)
                                continue
                            }

                            // Can't poll if there's no UUID yet
                            if (computer.uuid == null) {
                                continue
                            }

                            val tuple = getPollingTuple(computer)

                            try {
                                val http =
                                    NvHTTP(
                                        ServerHelper.getCurrentAddressFromComputer(computer),
                                        computer.httpsPort,
                                        idManager.uniqueId,
                                        computer.serverCert,
                                        PlatformBinding.getCryptoProvider(
                                            this@ComputerManagerService
                                        ),
                                    )

                                val appList =
                                    if (tuple != null) {
                                        // If we're polling this machine too, grab the network lock
                                        // while doing the app list request to prevent other
                                        // requests from being issued in the meantime.
                                        synchronized(tuple.networkLock) { http.getAppListRaw() }
                                    } else {
                                        // No polling is happening now, so we just call it directly
                                        http.getAppListRaw()
                                    }

                                val list = NvHTTP.getAppListByReader(StringReader(appList))
                                if (list.isEmpty()) {
                                    LimeLog.warning("Empty app list received from ${computer.uuid}")

                                    // The app list might actually be empty, so if we get an empty
                                    // response a few times in a row, we'll go ahead and believe it.
                                    emptyAppListResponses++
                                }
                                if (
                                    appList.isNotEmpty() &&
                                        (list.isNotEmpty() ||
                                            emptyAppListResponses >= EMPTY_LIST_THRESHOLD)
                                ) {
                                    // Open the cache file
                                    try {
                                        CacheHelper.openCacheFileForOutput(
                                                cacheDir,
                                                "applist",
                                                computer.uuid,
                                            )
                                            .use { cacheOut ->
                                                CacheHelper.writeStringToOutputStream(
                                                    cacheOut,
                                                    appList,
                                                )
                                            }
                                    } catch (e: IOException) {
                                        e.printStackTrace()
                                    }

                                    // Reset empty count if it wasn't empty this time
                                    if (list.isNotEmpty()) {
                                        emptyAppListResponses = 0
                                    }

                                    // Update the computer
                                    computer.rawAppList = appList
                                    receivedAppList = true

                                    // Notify that the app list has been updated
                                    // and ensure that the thread is still active
                                    val notified = listener
                                    if (notified != null && this@ApplistPoller.thread != null) {
                                        notified.notifyComputerUpdated(computer)
                                    }
                                } else if (appList.isEmpty()) {
                                    LimeLog.warning("Null app list received from ${computer.uuid}")
                                }
                            } catch (e: IOException) {
                                e.printStackTrace()
                            } catch (e: XmlPullParserException) {
                                e.printStackTrace()
                            }
                        } while (waitPollingDelay())
                    }
                }
            thread = polling
            polling.name = "App list polling thread for " + computer.name
            polling.start()
        }

        fun stop() {
            val polling = thread
            if (polling != null) {
                polling.interrupt()

                // Don't join here because we might be blocked on network I/O

                thread = null
            }
        }
    }

    private companion object {
        private const val SERVERINFO_POLLING_PERIOD_MS = 1500
        private const val APPLIST_POLLING_PERIOD_MS = 30000
        private const val APPLIST_FAILED_POLLING_RETRY_MS = 2000
        private const val MDNS_QUERY_PERIOD_MS = 1000
        private const val OFFLINE_POLL_TRIES = 3
        private const val INITIAL_POLL_TRIES = 2
        private const val EMPTY_LIST_THRESHOLD = 3
        private const val POLL_DATA_TTL_MS = 30000

        // The waits of the binder, in milliseconds.
        private const val DISCOVERY_WAIT_MS = 1000L
        private const val POLLING_STOPPED_WAIT_MS = 250L

        private const val STUN_SERVER = "stun.moonlight-stream.org"
        private const val STUN_PORT = 3478
    }
}

internal class PollingTuple(
    @JvmField val computer: ComputerDetails,
    @JvmField var thread: Thread?,
) {
    @JvmField val networkLock = Any()
    @JvmField var lastSuccessfulPollMs: Long = 0
}

internal class ReachabilityTuple(
    @JvmField val computer: ComputerDetails,
    @JvmField val reachableAddress: String,
)
