package io.github.magalz.borealis.diagnostics.transfer

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import java.io.File
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OneShotFileServerTest {
    @get:Rule val temp = TemporaryFolder()

    private val now = AtomicLong(1_000L)
    private val servers = mutableListOf<OneShotFileServer>()
    private val loopback = InetAddress.getByName("127.0.0.1")
    private lateinit var zip: File
    private val zipBytes = ByteArray(40_000) { (it * 31).toByte() }

    @Before
    fun setUp() {
        zip =
            File(temp.root, "borealis-diagnostics-20261014-123456.zip").apply {
                writeBytes(zipBytes)
            }
    }

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
        BorealisLog.shutdown()
    }

    private fun server(
        file: File = zip,
        lifetimeMillis: Long = 300_000L,
        readTimeoutMillis: Int = 10_000,
    ): OneShotFileServer =
        OneShotFileServer(
                file,
                loopback,
                clock = now::get,
                lifetimeMillis = lifetimeMillis,
                readTimeoutMillis = readTimeoutMillis,
            )
            .also { servers += it }

    private fun awaitStopped(server: OneShotFileServer) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (server.isRunning && System.nanoTime() < deadline) Thread.sleep(10)
        assertThat(server.isRunning).isFalse()
    }

    private fun assertRefused(port: Int) {
        var refused = false
        try {
            connect(port).close()
        } catch (_: ConnectException) {
            refused = true
        }
        assertThat(refused).isTrue()
    }

    @Test
    fun `the link points at the bound address, an ephemeral port and a 128 bit token`() {
        val server = server()

        val url = server.start()

        assertThat(url).matches("http://127\\.0\\.0\\.1:\\d{4,5}/[0-9a-f]{32}")
        assertThat(portOf(url)).isGreaterThan(0)
        assertThat(server.isRunning).isTrue()
        assertThat(server.status).isEqualTo(TransferStatus.WAITING)
    }

    @Test
    fun `two servers use different tokens`() {
        assertThat(tokenOf(server().start())).isNotEqualTo(tokenOf(server().start()))
    }

    @Test
    fun `the page shows the name and size, loads nothing, and does not use up the download`() {
        val url = server().start()

        val first = get(portOf(url), pathOf(url))!!
        val second = get(portOf(url), pathOf(url))!!

        assertThat(first.status).isEqualTo(200)
        assertThat(first.headers["content-type"]).startsWith("text/html")
        assertThat(first.headers["cache-control"]).isEqualTo("no-store")
        assertThat(first.headers["connection"]).isEqualTo("close")
        assertThat(first.text).contains(zip.name)
        assertThat(first.text).contains("39.1 KiB")
        assertThat(first.text).contains("href=\"${pathOf(url)}/${zip.name}\"")
        assertThat(first.text).doesNotContain("http://")
        assertThat(first.text).doesNotContain("https://")
        assertThat(first.text).doesNotContain("<script")
        assertThat(second.status).isEqualTo(200)
        assertThat(get(portOf(url), "${pathOf(url)}/${zip.name}")!!.status).isEqualTo(200)
    }

    @Test
    fun `the zip route sends the bundle with download headers`() {
        val url = server().start()

        val reply = get(portOf(url), "${pathOf(url)}/${zip.name}")!!

        assertThat(reply.status).isEqualTo(200)
        assertThat(reply.headers["content-type"]).isEqualTo("application/zip")
        assertThat(reply.headers["content-length"]).isEqualTo("40000")
        assertThat(reply.headers["content-disposition"])
            .isEqualTo("attachment; filename=\"${zip.name}\"")
        assertThat(reply.headers["cache-control"]).isEqualTo("no-store")
        assertThat(reply.headers["connection"]).isEqualTo("close")
        assertThat(reply.body).isEqualTo(zipBytes)
    }

    @Test
    fun `after one complete download the zip route answers 410 and the server stops`() {
        val server = server()
        val url = server.start()
        val path = "${pathOf(url)}/${zip.name}"
        // Queued before the first download ends, as a retrying browser or a preview would be.
        val late = connect(portOf(url))

        val first = get(portOf(url), path)!!
        late.send("GET $path HTTP/1.1\r\n\r\n")
        val second = late.readReply()!!
        late.close()

        assertThat(first.status).isEqualTo(200)
        assertThat(second.status).isEqualTo(410)
        assertThat(server.status).isEqualTo(TransferStatus.DOWNLOADED)
        awaitStopped(server)
        assertRefused(portOf(url))
    }

    @Test
    fun `a download the client abandons does not use up the link`() {
        val big = File(temp.root, "big.zip")
        RandomAccessFile(big, "rw").use { it.setLength(24L * 1024 * 1024) }
        val server = server(file = big)
        val url = server.start()
        connect(portOf(url)).apply {
            setSoLinger(true, 0)
            send("GET ${pathOf(url)}/${big.name} HTTP/1.1\r\n\r\n")
            getInputStream().read(ByteArray(1_024))
            close()
        }

        val retry = get(portOf(url), "${pathOf(url)}/${big.name}")!!

        assertThat(retry.status).isEqualTo(200)
        assertThat(retry.body.size).isEqualTo(24 * 1024 * 1024)
    }

    @Test
    fun `a wrong token, name or path answers 404`() {
        val url = server().start()
        val port = portOf(url)
        val wrong = "0".repeat(32)

        assertThat(get(port, "/$wrong")!!.status).isEqualTo(404)
        assertThat(get(port, "/$wrong/${zip.name}")!!.status).isEqualTo(404)
        assertThat(get(port, "${pathOf(url)}/other.zip")!!.status).isEqualTo(404)
        assertThat(get(port, "/")!!.status).isEqualTo(404)
        assertThat(get(port, "${pathOf(url)}/${zip.name}/extra")!!.status).isEqualTo(404)
        // None of them used up the download.
        assertThat(get(port, "${pathOf(url)}/${zip.name}")!!.status).isEqualTo(200)
    }

    @Test
    fun `a query string does not change the route`() {
        val url = server().start()

        assertThat(get(portOf(url), "${pathOf(url)}?utm=1")!!.status).isEqualTo(200)
    }

    @Test
    fun `other methods answer 405 and a malformed request answers 400`() {
        val url = server().start()
        val port = portOf(url)

        val post = get(port, pathOf(url), method = "POST")!!
        val head = get(port, pathOf(url), method = "HEAD")!!
        val garbage =
            connect(port).use {
                it.send("nonsense\r\n\r\n")
                it.readReply()!!
            }

        assertThat(post.status).isEqualTo(405)
        assertThat(post.headers["allow"]).isEqualTo("GET")
        assertThat(head.status).isEqualTo(405)
        assertThat(garbage.status).isEqualTo(400)
    }

    @Test
    fun `oversized headers are refused and the server keeps serving`() {
        val url = server().start()
        val request = "GET /x HTTP/1.1\r\nX: "
        val oversized = request + "a".repeat(8_193 - request.length)

        val reply =
            connect(portOf(url)).use {
                it.send(oversized)
                it.readReply()
            }

        assertThat(reply!!.status).isEqualTo(431)
        assertThat(get(portOf(url), pathOf(url))!!.status).isEqualTo(200)
    }

    @Test
    fun `a client that stays silent is cut off by the read timeout`() {
        val url = server(readTimeoutMillis = 150).start()

        val silent = connect(portOf(url))
        val reply = silent.readReply()
        silent.close()

        assertThat(reply!!.status).isEqualTo(408)
        assertThat(get(portOf(url), pathOf(url))!!.status).isEqualTo(200)
    }

    @Test
    fun `a second connection waits and does not break the first`() {
        val url = server().start()
        val port = portOf(url)
        val first = connect(port)
        first.send("GET ${pathOf(url)} HTTP/1.1\r\n")
        val second = connect(port)
        second.send("GET ${pathOf(url)} HTTP/1.1\r\n\r\n")

        first.send("Host: test\r\n\r\n")
        val firstReply = first.readReply()!!
        val secondReply = second.readReply()!!
        first.close()
        second.close()

        assertThat(firstReply.status).isEqualTo(200)
        assertThat(secondReply.status).isEqualTo(200)
    }

    @Test
    fun `the server stops when the lifetime runs out and then serves nothing`() {
        val server = server(lifetimeMillis = 300_000L)
        val url = server.start()
        assertThat(server.remainingMillis()).isEqualTo(300_000L)

        now.addAndGet(300_001L)
        awaitStopped(server)

        assertThat(server.status).isEqualTo(TransferStatus.EXPIRED)
        assertThat(server.remainingMillis()).isEqualTo(0L)
        assertRefused(portOf(url))
    }

    @Test
    fun `stop closes the socket, ends the thread and can be repeated`() {
        val server = server()
        val url = server.start()
        val threadsBefore = serverThreads()

        server.stop()
        server.stop()

        assertThat(server.isRunning).isFalse()
        assertThat(server.status).isEqualTo(TransferStatus.STOPPED)
        assertThat(serverThreads()).isLessThan(threadsBefore)
        assertRefused(portOf(url))
    }

    @Test
    fun `stop frees a server that is waiting on a stuck client`() {
        val server = server()
        val url = server.start()
        val stuck = connect(portOf(url))
        stuck.send("GET ${pathOf(url)} HTTP/1.1\r\n")
        Thread.sleep(100)

        server.stop()

        assertThat(server.isRunning).isFalse()
        assertThat(stuck.readReply()).isNull()
        stuck.close()
    }

    @Test
    fun `stop before start and a second start are harmless`() {
        val never = server()
        never.stop()
        assertThat(never.status).isEqualTo(TransferStatus.STOPPED)

        val twice = server()
        val url = twice.start()
        assertThat(twice.start()).isEqualTo(url)
        assertThat(twice.isRunning).isTrue()
    }

    @Test
    fun `a download that is already done keeps its status when stop is called`() {
        val server = server()
        val url = server.start()
        get(portOf(url), "${pathOf(url)}/${zip.name}")

        server.stop()

        assertThat(server.status).isEqualTo(TransferStatus.DOWNLOADED)
    }

    @Test
    fun `an unreadable file becomes a 500 and does not use up the link`() {
        val server = server(file = File(temp.root, "missing.zip"))
        val url = server.start()

        val reply = get(portOf(url), "${pathOf(url)}/missing.zip")!!

        assertThat(reply.status).isEqualTo(500)
        assertThat(server.status).isEqualTo(TransferStatus.WAITING)
    }

    @Test
    fun `the log never holds the token or the link`() {
        val records = CopyOnWriteArrayList<LogRecord>()
        BorealisLog.initialize(
            listOf(
                object : LogSink {
                    override fun write(record: LogRecord) {
                        records += record
                    }
                }
            )
        )
        val server = server()
        val url = server.start()
        val port = portOf(url)
        get(port, "/${"0".repeat(32)}")
        get(port, pathOf(url))
        get(port, "${pathOf(url)}/${zip.name}")
        awaitStopped(server)
        server.stop()

        val everything =
            records.joinToString("\n") {
                it.message + " " + it.fields.joinToString { (k, v) -> "$k=$v" } + it.stackTrace
            }
        assertThat(records).isNotEmpty()
        assertThat(everything).doesNotContain(tokenOf(url))
        assertThat(everything).doesNotContain(url)
        assertThat(everything).doesNotContain("0".repeat(32))
        assertThat(everything).contains("started")
        assertThat(everything).contains("status=200")
        assertThat(everything).contains("stopped")
    }

    @Test
    fun `html escaping covers the markup characters`() {
        assertThat(htmlEscape("<a href=\"x\">&'</a>"))
            .isEqualTo("&lt;a href=&quot;x&quot;&gt;&amp;&#39;&lt;/a&gt;")
    }

    @Test
    fun `a file name cannot break out of the Content-Disposition header`() {
        assertThat(headerSafeName("a\"b\\c\r\nd.zip")).isEqualTo("a_b_c__d.zip")
        assertThat(headerSafeName("plain-1.zip")).isEqualTo("plain-1.zip")
    }

    private fun serverThreads(): Int =
        Thread.getAllStackTraces().keys.count {
            it.name == OneShotFileServer.THREAD_NAME && it.isAlive
        }
}
