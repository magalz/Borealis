package io.github.magalz.borealis.diagnostics.transfer

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.magalz.borealis.logging.BorealisLog
import java.io.File
import java.net.ConnectException
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The state machine behind the Send to phone dialog, with a fake clock and a virtual ticker. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferSessionTest {
    @get:Rule val temp = TemporaryFolder()

    private val now = AtomicLong(1_000L)
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val sessions = mutableListOf<TransferSession>()
    private val zipBytes = ByteArray(40_000) { (it * 31).toByte() }
    private lateinit var zip: File

    @Before
    fun setUp() {
        zip =
            File(temp.root, "borealis-diagnostics-20261014-123456.zip").apply {
                writeBytes(zipBytes)
            }
    }

    @After
    fun tearDown() {
        sessions.forEach { it.stop() }
        BorealisLog.shutdown()
    }

    private fun TestScope.session(address: InetAddress? = loopback) =
        TransferSession({ address }, now::get, backgroundScope).also { sessions += it }

    /** One second of the session's ticker. */
    private fun TestScope.tick() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun TestScope.waiting(session: TransferSession): TransferState.Waiting {
        session.start(zip)
        runCurrent()
        assertThat(session.state.value).isInstanceOf(TransferState.Waiting::class.java)
        return session.state.value as TransferState.Waiting
    }

    /** The server's own threads move on real time: tick until the state is [expected]. */
    private fun TestScope.awaitState(session: TransferSession, expected: TransferState) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (session.state.value != expected && System.nanoTime() < deadline) {
            Thread.sleep(20)
            tick()
        }
        assertThat(session.state.value).isEqualTo(expected)
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
    fun `a new session is idle`() = runTest {
        assertThat(session().state.value).isEqualTo(TransferState.Idle)
    }

    @Test
    fun `with no private IPv4 address the state is NoNetwork and no link is started`() = runTest {
        val session = session(address = null)

        session.start(zip)
        runCurrent()

        assertThat(session.state.value).isEqualTo(TransferState.NoNetwork)
        assertThat(
                Thread.getAllStackTraces().keys.count {
                    it.name.startsWith(OneShotFileServer.THREAD_NAME) && it.isAlive
                }
            )
            .isEqualTo(0)
    }

    @Test
    fun `starting waits with the link, the file and 5 minutes on the clock`() = runTest {
        val waiting = waiting(session())

        assertThat(waiting.link).matches("http://127\\.0\\.0\\.1:\\d{4,5}/[0-9a-f]{32}")
        assertThat(waiting.fileName).isEqualTo(zip.name)
        assertThat(waiting.sizeBytes).isEqualTo(40_000L)
        assertThat(waiting.remainingMillis).isEqualTo(300_000L)
    }

    @Test
    fun `the QR code is a matrix that decodes to the link with error correction M`() = runTest {
        val waiting = waiting(session())

        val qr = waiting.qr
        val pixels =
            IntArray(qr.size * qr.size) { i -> if (qr[i % qr.size, i / qr.size]) BLACK else WHITE }
        val result =
            QRCodeReader()
                .decode(
                    BinaryBitmap(HybridBinarizer(RGBLuminanceSource(qr.size, qr.size, pixels))),
                    mapOf(DecodeHintType.PURE_BARCODE to true),
                )
        assertThat(result.text).isEqualTo(waiting.link)
        assertThat(result.resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL]).isEqualTo("M")
        // The 2 module margin is empty, and it is the same code the dialog draws.
        assertThat(qr[0, 0]).isFalse()
        assertThat(qr[1, qr.size - 1]).isFalse()
        assertThat(qr.size).isEqualTo(TransferQr.encode(waiting.link).width)
    }

    @Test
    fun `the countdown follows the clock`() = runTest {
        val session = session()
        waiting(session)

        now.addAndGet(61_000L)
        tick()

        assertThat(session.state.value).isInstanceOf(TransferState.Waiting::class.java)
        assertThat((session.state.value as TransferState.Waiting).remainingMillis)
            .isEqualTo(239_000L)
    }

    @Test
    fun `after 5 minutes the link expires and is closed`() = runTest {
        val session = session()
        val port = portOf(waiting(session).link)

        now.addAndGet(300_001L)
        awaitState(session, TransferState.Expired)

        assertRefused(port)
    }

    @Test
    fun `one download moves to Downloaded and a second request gets 410`() = runTest {
        val session = session()
        val link = waiting(session).link
        val path = "${pathOf(link)}/${zip.name}"
        val download = connect(portOf(link))
        download.send("GET $path HTTP/1.1\r\n\r\n")
        // Queued while the first download is served, as a retrying browser would be.
        val late = connect(portOf(link))
        late.send("GET $path HTTP/1.1\r\n\r\n")

        val first = download.readReply()!!
        val second = late.readReply()!!
        download.close()
        late.close()
        awaitState(session, TransferState.Downloaded)

        assertThat(first.status).isEqualTo(200)
        assertThat(first.body).isEqualTo(zipBytes)
        assertThat(second.status).isEqualTo(410)
    }

    @Test
    fun `stop closes the server and the state is Stopped`() = runTest {
        val session = session()
        val port = portOf(waiting(session).link)

        session.stop()

        assertThat(session.state.value).isEqualTo(TransferState.Stopped)
        assertRefused(port)
    }

    @Test
    fun `stop after a download keeps Downloaded`() = runTest {
        val session = session()
        val link = waiting(session).link
        get(portOf(link), "${pathOf(link)}/${zip.name}")
        awaitState(session, TransferState.Downloaded)

        session.stop()

        assertThat(session.state.value).isEqualTo(TransferState.Downloaded)
    }

    @Test
    fun `a link that cannot be bound is Failed`() = runTest {
        val session = session(address = InetAddress.getByName("203.0.113.9"))

        session.start(zip)
        runCurrent()

        assertThat(session.state.value).isEqualTo(TransferState.Failed)
    }

    @Test
    fun `starting again closes the old link and opens a new one`() = runTest {
        val session = session()
        val first = waiting(session).link

        val second = waiting(session).link

        assertThat(tokenOf(second)).isNotEqualTo(tokenOf(first))
        assertRefused(portOf(first))
        assertThat(get(portOf(second), pathOf(second))!!.status).isEqualTo(200)
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
