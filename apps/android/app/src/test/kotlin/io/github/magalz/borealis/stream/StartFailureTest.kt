package io.github.magalz.borealis.stream

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvApp
import com.sun.net.httpserver.HttpServer
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.logging.BorealisLog
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What `NvConnection` tells its listener while a start fails: the step, the texts, the calls. */
private class RecordingListener : NvConnectionListener {
    val calls = CopyOnWriteArrayList<String>()
    val messages = CopyOnWriteArrayList<String>()
    val failedStages = CopyOnWriteArrayList<String>()
    val failed = CountDownLatch(1)

    override fun stageStarting(stage: String?) {
        calls += "stageStarting"
    }

    override fun stageComplete(stage: String?) {
        calls += "stageComplete"
    }

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        failedStages += stage
        calls += "stageFailed"
        failed.countDown()
    }

    override fun launchStarting(resume: Boolean) {
        calls += if (resume) "launchStarting resume" else "launchStarting launch"
    }

    override fun displayMessage(message: String) {
        messages += message
    }

    override fun displayTransientMessage(message: String) = Unit

    override fun connectionStarted() = Unit

    override fun connectionTerminated(errorCode: Int) = Unit

    override fun connectionStatusUpdate(connectionStatus: Int) = Unit

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) = Unit

    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) =
        Unit

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) = Unit

    override fun setMotionEventState(
        controllerNumber: Short,
        motionType: Byte,
        reportRateHz: Short,
    ) = Unit

    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) = Unit
}

private object NoDecoder : VideoDecoderRenderer() {
    override fun setup(format: Int, width: Int, height: Int, redrawRate: Int) = 0

    override fun start() = Unit

    override fun stop() = Unit

    override fun submitDecodeUnit(
        decodeUnitData: ByteArray?,
        decodeUnitLength: Int,
        decodeUnitType: Int,
        frameNumber: Int,
        frameType: Int,
        frameHostProcessingLatency: Char,
        receiveTimeUs: Long,
        enqueueTimeUs: Long,
    ) = 0

    override fun cleanup() = Unit

    override fun getCapabilities() = 0

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) = Unit
}

@RunWith(RobolectricTestRunner::class)
class StartFailureTest {
    @get:Rule val log = RecordingLog()

    private val listener = RecordingListener()
    private var host: HttpServer? = null

    @After
    fun tearDown() {
        host?.stop(0)
    }

    /**
     * The configuration of a 1080p60 stream of the Desktop app. Built without its constructor,
     * which loads the native library that a unit test does not have.
     */
    private fun streamConfiguration(): StreamConfiguration {
        val unsafe =
            Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").let {
                it.isAccessible = true
                it.get(null) as sun.misc.Unsafe
            }
        val config = unsafe.allocateInstance(StreamConfiguration::class.java) as StreamConfiguration
        val values =
            mapOf(
                "app" to NvApp("Desktop", 881448767, false),
                "remote" to StreamConfiguration.STREAM_CFG_LOCAL,
                "width" to 1920,
                "height" to 1080,
                "refreshRate" to 60,
                "launchRefreshRate" to 60,
                "bitrate" to 20_000,
                "maxPacketSize" to 1024,
            )
        for ((name, value) in values) {
            StreamConfiguration::class.java.getDeclaredField(name).also { it.isAccessible = true }.set(config, value)
        }
        return config
    }

    private val crypto =
        object : LimelightCryptoProvider {
            override val clientCertificate: X509Certificate? = null

            override val clientPrivateKey: PrivateKey? = null

            override val pemEncodedClientCertificate: ByteArray? = null

            override fun encodeBase64String(data: ByteArray): String = ""
        }

    private fun connect(port: Int) {
        val config = streamConfiguration()
        val connection =
            NvConnection(
                ApplicationProvider.getApplicationContext<Application>(),
                ComputerDetails.AddressTuple("127.0.0.1", port),
                port,
                "id",
                config,
                crypto,
                null,
            )
        connection.start(null, NoDecoder, listener)
        assertThat(listener.failed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
    }

    private fun refusedPort(): Int = ServerSocket(0).use { it.localPort }

    private fun serverInfo(paired: Boolean) =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<root status_code=\"200\"><hostname>Desk PC</hostname>" +
            "<appversion>7.1.431.0</appversion><uniqueid>ABC</uniqueid>" +
            "<PairStatus>${if (paired) 1 else 0}</PairStatus><currentgame>0</currentgame>" +
            "<state>SUNSHINE_SERVER_FREE</state></root>"

    private fun serve(paired: Boolean): Int {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/serverinfo") { exchange ->
            val body = serverInfo(paired).toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        host = server
        return server.address.port
    }

    private fun everythingLogged(): String =
        log.records.joinToString("\n") { "${it.message} ${it.fields} ${it.stackTrace}" }

    @Test
    fun `a host that does not answer fails at the serverinfo step, not at the app name`() {
        connect(refusedPort())

        assertThat(listener.failedStages).containsExactly("serverinfo")
    }

    @Test
    fun `the exception text of a failed start is no message for the screen`() {
        connect(refusedPort())

        assertThat(listener.messages).isEmpty()
    }

    @Test
    fun `the exception text of a failed start goes to the log with the address masked`() {
        BorealisLog.setMaskAddresses(true)

        connect(refusedPort())

        assertThat(everythingLogged()).contains("serverinfo")
        assertThat(everythingLogged()).doesNotContain("127.0.0.1")
    }

    @Test
    fun `while the host has not answered the serverinfo the app is not being started`() {
        connect(refusedPort())

        assertThat(listener.calls).doesNotContain("launchStarting launch")
        assertThat(listener.calls).doesNotContain("launchStarting resume")
    }

    @Test
    fun `a fixed text of the start is still sent to the listener, for the error screen`() {
        connect(serve(paired = false))

        assertThat(listener.messages).containsExactly("Device not paired with computer")
    }

    private companion object {
        const val WAIT_SECONDS = 20L
    }
}
