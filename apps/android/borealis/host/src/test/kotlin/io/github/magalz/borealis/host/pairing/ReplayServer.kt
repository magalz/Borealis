package io.github.magalz.borealis.host.pairing

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A raw TCP server that answers each accepted connection with the next scripted [Reply]: it reads
 * the request head, writes the bytes verbatim and then closes or resets the socket. Nothing here
 * speaks HTTP, so stray bytes after a response reach the client exactly as a host would send them.
 */
class ReplayServer(private vararg val replies: Reply) : Closeable {
    /** What to write on one connection and how to end it. */
    class Reply(
        val bytes: ByteArray,
        val closeDelayMillis: Long = 0,
        val reset: Boolean = false,
    )

    private val server = ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())
    private val requestHeads = CopyOnWriteArrayList<String>()
    private val worker = thread(isDaemon = true, name = "replay-server") { serve() }

    val port: Int
        get() = server.localPort

    /** The request heads received so far, one per connection, in accept order. */
    val heads: List<String>
        get() = requestHeads.toList()

    private fun serve() {
        for (reply in replies) {
            val socket =
                try {
                    server.accept()
                } catch (_: IOException) {
                    return
                }
            thread(isDaemon = true) { answer(socket, reply) }
        }
    }

    private fun answer(socket: Socket, reply: Reply) {
        socket.use {
            requestHeads += readHead(it)
            if (reply.reset) it.setSoLinger(true, 0)
            it.getOutputStream().apply {
                write(reply.bytes)
                flush()
            }
            if (reply.closeDelayMillis > 0) Thread.sleep(reply.closeDelayMillis)
        }
    }

    private fun readHead(socket: Socket): String {
        val head = StringBuilder()
        val input = socket.getInputStream()
        while (!head.endsWith("\r\n\r\n")) {
            val next = input.read()
            if (next < 0) break
            head.append(next.toChar())
        }
        return head.toString()
    }

    override fun close() {
        server.close()
        worker.join(JOIN_MILLIS)
    }

    private companion object {
        const val BACKLOG = 50
        const val JOIN_MILLIS = 2_000L
    }
}
