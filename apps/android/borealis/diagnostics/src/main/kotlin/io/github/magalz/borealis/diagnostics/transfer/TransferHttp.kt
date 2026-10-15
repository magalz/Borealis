package io.github.magalz.borealis.diagnostics.transfer

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.Locale

/** The few HTTP/1.1 statuses the transfer server answers with. */
internal enum class HttpStatus(val code: Int, val reason: String) {
    OK(200, "OK"),
    BAD_REQUEST(400, "Bad Request"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    GONE(410, "Gone"),
    HEADER_TOO_LARGE(431, "Request Header Fields Too Large"),
    SERVER_ERROR(500, "Internal Server Error"),
}

/** A request that is answered with [status] and nothing else. */
internal class HttpRejection(val status: HttpStatus, val header: String? = null) : Exception()

/** The target of a GET request, without its query or fragment. */
internal class HttpRequest(val path: String)

internal object TransferHttp {
    const val MAX_HEAD_BYTES = 8 * 1024
    private const val HEAD_END = "\r\n\r\n"
    private const val REQUEST_LINE_PARTS = 3
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val KIB = 1024.0

    /**
     * Reads the request line and headers (never a body) and returns the GET target, or null if the
     * peer closed first. Throws [HttpRejection] for a head that is too large, late, malformed or
     * not a GET. The whole head must arrive within [timeoutMillis], so a trickle of bytes cannot
     * hold the serving thread; a silent socket raises [SocketTimeoutException] on its own.
     */
    fun readRequest(input: InputStream, timeoutMillis: Int): HttpRequest? {
        val head = readHead(input, timeoutMillis) ?: return null
        val parts = head.substringBefore("\r\n").split(' ')
        if (parts.size != REQUEST_LINE_PARTS || !parts[2].startsWith("HTTP/")) {
            throw HttpRejection(HttpStatus.BAD_REQUEST)
        }
        if (parts[0] != "GET") throw HttpRejection(HttpStatus.METHOD_NOT_ALLOWED, "Allow: GET")
        return HttpRequest(parts[1].substringBefore('?').substringBefore('#'))
    }

    private fun readHead(input: InputStream, timeoutMillis: Int): String? {
        val head = StringBuilder()
        val deadline = System.nanoTime() + timeoutMillis * NANOS_PER_MILLI
        while (!head.endsWith(HEAD_END)) {
            if (System.nanoTime() > deadline) throw HttpRejection(HttpStatus.REQUEST_TIMEOUT)
            val next = input.read()
            if (next < 0) return null
            head.append(next.toChar())
            if (head.length > MAX_HEAD_BYTES) throw HttpRejection(HttpStatus.HEADER_TOO_LARGE)
        }
        return head.toString()
    }

    /** The status line and headers, ending with the blank line. */
    fun head(status: HttpStatus, headers: List<String>): ByteArray = buildString {
        append("HTTP/1.1 ${status.code} ${status.reason}\r\n")
        headers.forEach { append(it).append("\r\n") }
        append("Cache-Control: no-store\r\n")
        append("Connection: close\r\n\r\n")
    }
        .toByteArray(Charsets.US_ASCII)

    /** A complete small reply; the body defaults to the reason phrase. */
    @Throws(IOException::class)
    fun reply(
        out: OutputStream,
        status: HttpStatus,
        extraHeaders: List<String> = emptyList(),
        body: ByteArray = status.reason.toByteArray(),
        contentType: String = "text/plain; charset=utf-8",
    ) {
        out.write(
            head(
                status,
                listOf("Content-Type: $contentType", "Content-Length: ${body.size}") + extraHeaders,
            )
        )
        out.write(body)
        out.flush()
    }

    fun formatSize(bytes: Long): String =
        when {
            bytes < KIB -> "$bytes B"
            bytes < KIB * KIB -> String.format(Locale.ROOT, "%.1f KiB", bytes / KIB)
            else -> String.format(Locale.ROOT, "%.1f MiB", bytes / (KIB * KIB))
        }

    /** The page behind the QR code: static, no scripts and nothing loaded from elsewhere. */
    fun pageHtml(name: String, size: Long, downloadPath: String): String =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Borealis diagnostics</title>
        <style>body{font-family:sans-serif;margin:2em}a{font-size:1.4em}</style>
        </head>
        <body>
        <h1>Borealis diagnostic bundle</h1>
        <p>${htmlEscape(name)} (${formatSize(size)})</p>
        <p><a href="${htmlEscape(downloadPath)}">Download</a></p>
        <p>This link works for one download and expires soon.</p>
        </body>
        </html>
        """
            .trimIndent()
}

internal fun htmlEscape(text: String): String = buildString {
    for (char in text) {
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}

private fun isUnsafeInHeader(char: Char) = char == '"' || char == '\\' || char < ' ' || char > '~'

/** A file name that cannot end the quoted `filename` parameter or start a new header line. */
internal fun headerSafeName(name: String): String =
    name.map { if (isUnsafeInHeader(it)) '_' else it }.joinToString("")
