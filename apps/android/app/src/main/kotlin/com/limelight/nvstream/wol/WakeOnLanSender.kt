package com.limelight.nvstream.wol

import com.limelight.LimeLog
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress
import java.util.Scanner

object WakeOnLanSender {
    // These ports will always be tried as-is.

    // Standard WOL port (privileged port)
    private const val STANDARD_WOL_PORT = 9

    // Port opened by Moonlight Internet Hosting Tool for WoL (non-privileged port)
    private const val HOSTING_TOOL_WOL_PORT = 47009
    private val STATIC_PORTS_TO_TRY = intArrayOf(STANDARD_WOL_PORT, HOSTING_TOOL_WOL_PORT)

    // These ports will be offset by the base port number (47989) to support alternate ports.
    private const val BASE_PORT = 47989

    // The destination ports of a UDP packet (port 0 is reserved)
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535

    // Ports opened by GFE
    private const val GFE_PORT_47998 = 47998
    private const val GFE_PORT_47999 = 47999
    private const val GFE_PORT_48000 = 48000
    private const val GFE_PORT_48002 = 48002
    private const val GFE_PORT_48010 = 48010
    private val DYNAMIC_PORTS_TO_TRY =
        intArrayOf(GFE_PORT_47998, GFE_PORT_47999, GFE_PORT_48000, GFE_PORT_48002, GFE_PORT_48010)

    private const val MAC_ADDRESS_BYTES = 6
    private const val MAC_ADDRESS_REPETITIONS = 16
    private const val PAYLOAD_BYTES = 102
    private const val HEX_RADIX = 16

    internal fun openWolSocket(): DatagramSocket = DatagramSocket(null as SocketAddress?)

    // The stack traces go to the standard error stream, as in the inherited Java. Logging them
    // instead is a behaviour change (Sprint 18c backlog).
    @Suppress("PrintStackTrace")
    private fun sendPacketsForAddress(
        address: InetAddress,
        httpPort: Int,
        sock: DatagramSocket,
        payload: ByteArray,
    ) {
        var lastException: IOException? = null
        var sentWolPacket = false

        // Try the static ports
        for (port in STATIC_PORTS_TO_TRY) {
            try {
                val dp = DatagramPacket(payload, payload.size)
                dp.address = address
                dp.port = port
                sock.send(dp)
                sentWolPacket = true
            } catch (e: IOException) {
                e.printStackTrace()
                lastException = e
            }
        }

        // Try the dynamic ports
        for (port in DYNAMIC_PORTS_TO_TRY) {
            val dynamicPort = (port - BASE_PORT) + httpPort
            if (dynamicPort !in MIN_PORT..MAX_PORT) {
                // An HTTP port near the top of the range puts this port past it, and setPort throws
                // an IllegalArgumentException, not an IOException. Skip it and keep waking the
                // host.
                LimeLog.warning("Wake-on-LAN: skipping port $dynamicPort, it is outside 1..65535")
                continue
            }

            try {
                val dp = DatagramPacket(payload, payload.size)
                dp.address = address
                dp.port = dynamicPort
                sock.send(dp)
                sentWolPacket = true
            } catch (e: IOException) {
                e.printStackTrace()
                lastException = e
            }
        }

        if (!sentWolPacket) {
            throw checkNotNull(lastException)
        }
    }

    // Converted as is from the inherited Java; the stack traces go to the standard error stream
    // as they did, and logging them instead is a behaviour change (Sprint 18c backlog).
    @JvmStatic
    @Throws(IOException::class)
    @Suppress("PrintStackTrace")
    fun sendWolPacket(computer: ComputerDetails) {
        val payload = createWolPayload(computer)
        var lastException: IOException? = null
        var sentWolPacket = false

        DatagramSocket(0).use { sock ->
            // Try all resolved remote and local addresses and broadcast addresses.
            // The broadcast address is required to avoid stale ARP cache entries
            // making the sleeping machine unreachable.
            val addresses =
                arrayOf(
                    computer.localAddress,
                    computer.remoteAddress,
                    computer.manualAddress,
                    computer.ipv6Address,
                )
            for (address in addresses) {
                if (address == null) {
                    continue
                }

                val sent =
                    sendToAddressTuple(address, sock, payload) { e ->
                        e.printStackTrace()
                        lastException = e
                    }
                if (sent) {
                    sentWolPacket = true
                }
            }
        }

        // Propagate the DNS resolution exception if we didn't
        // manage to get a single packet out to the host.
        val failure = lastException
        if (!sentWolPacket && failure != null) {
            throw failure
        }
    }

    /**
     * Sends the packets to the broadcast address and then to every address that [address] resolves
     * to, all with its port, and says whether at least one went out. Each failure goes to
     * [onFailure], and none stops the sends that follow.
     */
    private fun sendToAddressTuple(
        address: AddressTuple,
        sock: DatagramSocket,
        payload: ByteArray,
        onFailure: (IOException) -> Unit,
    ): Boolean {
        var sent = false

        try {
            sendPacketsForAddress(
                InetAddress.getByName("255.255.255.255"),
                address.port,
                sock,
                payload,
            )
            sent = true
        } catch (e: IOException) {
            onFailure(e)
        }

        try {
            for (resolvedAddress in InetAddress.getAllByName(address.address)) {
                try {
                    sendPacketsForAddress(resolvedAddress, address.port, sock, payload)
                    sent = true
                } catch (e: IOException) {
                    onFailure(e)
                }
            }
        } catch (e: IOException) {
            // We may have addresses that don't resolve on this subnet,
            // but don't throw and exit the whole function if that happens.
            // We'll throw it at the end if we didn't send a single packet.
            onFailure(e)
        }

        return sent
    }

    private fun macStringToBytes(macAddress: String?): ByteArray {
        val macBytes = ByteArray(MAC_ADDRESS_BYTES)

        Scanner(macAddress).useDelimiter(":").use { scan ->
            var i = 0
            while (i < macBytes.size && scan.hasNext()) {
                try {
                    macBytes[i] = scan.next().toInt(HEX_RADIX).toByte()
                } catch (e: NumberFormatException) {
                    LimeLog.warning("Malformed MAC address: $macAddress (index: $i)")
                    break
                }
                i++
            }
            return macBytes
        }
    }

    private fun createWolPayload(computer: ComputerDetails): ByteArray {
        val payload = ByteArray(PAYLOAD_BYTES)
        val macAddress = macStringToBytes(computer.macAddress)
        var i = 0

        // 6 bytes of FF
        while (i < MAC_ADDRESS_BYTES) {
            payload[i] = 0xFF.toByte()
            i++
        }

        // 16 repetitions of the MAC address
        repeat(MAC_ADDRESS_REPETITIONS) {
            System.arraycopy(macAddress, 0, payload, i, macAddress.size)
            i += macAddress.size
        }

        return payload
    }
}
