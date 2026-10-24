package com.limelight.nvstream.http

import io.github.magalz.borealis.host.caps.BorealisHostInfo
import java.security.cert.X509Certificate
import java.util.Objects

class ComputerDetails {
    enum class State {
        ONLINE,
        OFFLINE,
        UNKNOWN,
    }

    class AddressTuple(address: String?, port: Int) {
        @JvmField var address: String
        @JvmField var port: Int

        init {
            requireNotNull(address) { "Address cannot be null" }
            require(port > 0) { "Invalid port" }

            // If this was an escaped IPv6 address, remove the brackets
            this.address =
                if (address.startsWith("[") && address.endsWith("]")) {
                    address.substring(1, address.length - 1)
                } else {
                    address
                }
            this.port = port
        }

        override fun hashCode(): Int = Objects.hash(address, port)

        override fun equals(other: Any?): Boolean {
            if (other !is AddressTuple) {
                return false
            }

            return address == other.address && port == other.port
        }

        override fun toString(): String =
            if (address.contains(":")) {
                // IPv6
                "[$address]:$port"
            } else {
                // IPv4 and hostnames
                "$address:$port"
            }
    }

    // Persistent attributes
    @JvmField var uuid: String? = null
    @JvmField var name: String? = null
    @JvmField var localAddress: AddressTuple? = null
    @JvmField var remoteAddress: AddressTuple? = null
    @JvmField var manualAddress: AddressTuple? = null
    @JvmField var ipv6Address: AddressTuple? = null
    @JvmField var macAddress: String? = null
    @JvmField var serverCert: X509Certificate? = null

    // Transient attributes
    @JvmField var state: State? = null
    @JvmField var activeAddress: AddressTuple? = null
    @JvmField var httpsPort: Int = 0
    @JvmField var externalPort: Int = 0
    @JvmField var pairState: PairingManager.PairState? = null
    @JvmField var runningGameId: Int = 0
    @JvmField var rawAppList: String? = null
    @JvmField var nvidiaServer: Boolean = false
    // BOREALIS: host capabilities (host.caps)
    @JvmField var borealis: BorealisHostInfo? = null

    constructor() {
        // Use defaults
        state = State.UNKNOWN
    }

    constructor(details: ComputerDetails) {
        // Copy details from the other computer
        update(details)
    }

    fun guessExternalPort(): Int =
        if (externalPort != 0) {
            externalPort
        } else {
            remoteAddress?.port
                ?: activeAddress?.port
                ?: ipv6Address?.port
                ?: localAddress?.port
                ?: NvHTTP.DEFAULT_HTTP_PORT
        }

    fun update(details: ComputerDetails) {
        this.state = details.state
        this.name = details.name
        this.uuid = details.uuid
        details.activeAddress?.let { this.activeAddress = it }
        // We can get IPv4 loopback addresses with GS IPv6 Forwarder
        details.localAddress?.let { if (!it.address.startsWith("127.")) this.localAddress = it }
        val remote = details.remoteAddress
        val knownRemote = this.remoteAddress
        if (remote != null) {
            this.remoteAddress = remote
        } else if (knownRemote != null && details.externalPort != 0) {
            // If we have a remote address already (perhaps via STUN) but our updated details
            // don't have a new one (because GFE doesn't send one), propagate the external
            // port to the current remote address. We may have tried to guess it previously.
            knownRemote.port = details.externalPort
        }
        details.manualAddress?.let { this.manualAddress = it }
        details.ipv6Address?.let { this.ipv6Address = it }
        details.macAddress?.let { if (it != "00:00:00:00:00:00") this.macAddress = it }
        details.serverCert?.let { this.serverCert = it }
        this.externalPort = details.externalPort
        this.httpsPort = details.httpsPort
        this.pairState = details.pairState
        this.runningGameId = details.runningGameId
        this.nvidiaServer = details.nvidiaServer
        this.borealis = details.borealis // BOREALIS: host capabilities (host.caps)
        this.rawAppList = details.rawAppList
    }

    override fun toString(): String =
        "Name: $name\n" +
            "State: $state\n" +
            "Active Address: $activeAddress\n" +
            "UUID: $uuid\n" +
            "Local Address: $localAddress\n" +
            "Remote Address: $remoteAddress\n" +
            "IPv6 Address: $ipv6Address\n" +
            "Manual Address: $manualAddress\n" +
            "MAC Address: $macAddress\n" +
            "Pair State: $pairState\n" +
            "Running Game ID: $runningGameId\n" +
            "HTTPS Port: $httpsPort\n"
}
