package io.github.magalz.borealis.diagnostics.transfer

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

/** A parsed HTTP/1.1 reply. */
internal class Reply(val status: Int, val headers: Map<String, String>, val body: ByteArray) {
    val text: String
        get() = body.decodeToString()
}

private val HEADER_END = "\r\n\r\n".toByteArray()

/** Reads until the server closes the connection; null if it closed with nothing to parse. */
internal fun Socket.readReply(): Reply? {
    val all = ByteArrayOutputStream()
    try {
        getInputStream().copyTo(all)
    } catch (_: IOException) {
        // A reset after the reply was written still leaves the bytes read so far.
    }
    val bytes = all.toByteArray()
    val split = indexOf(bytes, HEADER_END)
    if (split < 0) return null
    val head = bytes.copyOfRange(0, split).decodeToString().split("\r\n")
    val status = head.first().split(" ")[1].toInt()
    val headers =
        head.drop(1).associate {
            it.substringBefore(":").lowercase() to it.substringAfter(":").trim()
        }
    return Reply(status, headers, bytes.copyOfRange(split + HEADER_END.size, bytes.size))
}

private fun indexOf(haystack: ByteArray, needle: ByteArray): Int =
    (0..haystack.size - needle.size).firstOrNull { start ->
        needle.indices.all { haystack[start + it] == needle[it] }
    } ?: -1

internal fun connect(port: Int): Socket =
    Socket(InetAddress.getByName("127.0.0.1"), port).apply { soTimeout = 5_000 }

internal fun Socket.send(text: String) {
    getOutputStream().write(text.toByteArray())
    getOutputStream().flush()
}

internal fun get(port: Int, path: String, method: String = "GET"): Reply? =
    connect(port).use {
        it.send("$method $path HTTP/1.1\r\nHost: test\r\n\r\n")
        it.readReply()
    }

internal fun portOf(url: String): Int = url.substringAfterLast(":").substringBefore("/").toInt()

internal fun tokenOf(url: String): String = url.substringAfterLast("/")

/** The path of a link, `/<token>`. */
internal fun pathOf(url: String): String = "/" + url.substringAfter("//").substringAfter("/")
