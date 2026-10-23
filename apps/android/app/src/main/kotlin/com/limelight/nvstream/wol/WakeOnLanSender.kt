package com.limelight.nvstream.wol

import com.limelight.LimeLog
import com.limelight.nvstream.http.ComputerDetails
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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

    // The stack traces go to the standard error stream, as in the inherited Java. Logging them
    // instead is a behaviour change (Sprint 12 backlog).
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
            try {
                val dp = DatagramPacket(payload, payload.size)
                dp.address = address
                dp.port = (port - BASE_PORT) + httpPort
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

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    // The stack traces go to the standard error stream, as in the inherited Java. Logging them
    // instead is a behaviour change (Sprint 12 backlog).
    @JvmStatic
    @Throws(IOException::class)
    @Suppress("NestedBlockDepth", "PrintStackTrace")
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

                try {
                    sendPacketsForAddress(
                        InetAddress.getByName("255.255.255.255"),
                        address.port,
                        sock,
                        payload,
                    )
                    sentWolPacket = true
                } catch (e: IOException) {
                    e.printStackTrace()
                    lastException = e
                }

                try {
                    for (resolvedAddress in InetAddress.getAllByName(address.address)) {
                        try {
                            sendPacketsForAddress(resolvedAddress, address.port, sock, payload)
                            sentWolPacket = true
                        } catch (e: IOException) {
                            e.printStackTrace()
                            lastException = e
                        }
                    }
                } catch (e: IOException) {
                    // We may have addresses that don't resolve on this subnet,
                    // but don't throw and exit the whole function if that happens.
                    // We'll throw it at the end if we didn't send a single packet.
                    e.printStackTrace()
                    lastException = e
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
