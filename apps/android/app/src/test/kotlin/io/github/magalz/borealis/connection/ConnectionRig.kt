package io.github.magalz.borealis.connection

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.crypto.providerOf
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.nvhttp.NvFakeHost
import io.github.magalz.borealis.nvhttp.SeenRequest
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import org.junit.rules.ExternalResource

/** The id and the name of the app that the stream asks for. */
internal const val DESKTOP_ID = 881448767
internal const val DESKTOP_NAME = "Desktop"

/** Another app of the host, which can be the one that already runs. */
internal const val OTHER_ID = 777
internal const val OTHER_NAME = "Other App"

private const val WAIT_MS = 30_000L
private const val POLL_MS = 10L

/** The thread of a start is unnamed: the JVM calls it `Thread-<n>`. */
private val UNNAMED_THREAD = Regex("Thread-\\d+")

/**
 * What the connection tells its listener, in order, as one line per call (see [events]), and the
 * threads that it called from. By default a conflict is answered with [conflictChoice]; a null
 * choice leaves it open, for a test that answers (or cancels) later.
 */
internal class RecordingListener : NvConnectionListener {
    val events = CopyOnWriteArrayList<String>()
    val threads = CopyOnWriteArrayList<Thread>()

    @Volatile var conflictChoice: ConflictChoice? = ConflictChoice.CANCEL

    /** The answer slot of the last conflict. */
    @Volatile var conflictAnswer: ConflictAnswer? = null

    /** Runs on the start thread after the event was recorded. */
    @Volatile var onEvent: (String) -> Unit = {}

    private fun record(event: String) {
        events += event
        onEvent(event)
    }

    override fun stageStarting(stage: String?) {
        threads += Thread.currentThread()
        record("stageStarting $stage")
    }

    override fun stageComplete(stage: String?) = record("stageComplete $stage")

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) =
        record("stageFailed $stage ports=$portFlags error=$errorCode")

    override fun connectionStarted() = record("connectionStarted")

    override fun connectionTerminated(errorCode: Int) = record("connectionTerminated $errorCode")

    override fun connectionStatusUpdate(connectionStatus: Int) =
        record("connectionStatusUpdate $connectionStatus")

    override fun displayMessage(message: String) = record("message $message")

    override fun displayTransientMessage(message: String) = record("transient $message")

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) =
        record("rumble")

    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) =
        record("rumbleTriggers")

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) = record("setHdrMode")

    override fun setMotionEventState(
        controllerNumber: Short,
        motionType: Byte,
        reportRateHz: Short,
    ) = record("setMotionEventState")

    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) =
        record("setControllerLED")

    override fun launchRetrying(category: Int, attempt: Int, maxAttempts: Int, delayMs: Int) =
        record("launchRetrying category=$category attempt=$attempt max=$maxAttempts delay=$delayMs")

    override fun launchStarting(resume: Boolean) = record("launchStarting resume=$resume")

    override fun launchConflict(
        runningAppId: Int,
        runningAppName: String?,
        requestedAppId: Int,
        requestedAppName: String?,
        answer: ConflictAnswer,
    ) {
        conflictAnswer = answer
        record(
            "launchConflict running=$runningAppId/$runningAppName " +
                "requested=$requestedAppId/$requestedAppName"
        )
        conflictChoice?.let { answer.choose(it) }
    }

    /** The events that start with [prefix]. */
    fun events(prefix: String): List<String> = events.filter { it.startsWith(prefix) }

    /** Waits for an event that starts with [prefix] and returns it. */
    fun await(prefix: String): String {
        waitUntil("an event that starts with \"$prefix\"") { events.any { it.startsWith(prefix) } }
        return events.first { it.startsWith(prefix) }
    }

    /** Waits for [count] events that start with [prefix]. */
    fun awaitCount(prefix: String, count: Int) {
        waitUntil("$count events that start with \"$prefix\"") { events(prefix).size >= count }
    }

    /** Waits for every thread that called the listener to end. */
    fun awaitEnded() {
        var seen = 0
        while (seen != threads.size) {
            seen = threads.size
            threads.forEach {
                it.join(WAIT_MS)
                check(!it.isAlive) { "the start thread did not end; events: $events" }
            }
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val limit = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < limit) {
                "Timed out waiting for $what; events: $events"
            }
            Thread.sleep(POLL_MS)
        }
    }
}

/** A decoder that only reports [capabilities]. */
internal class StubDecoder(private val capabilities: Int = 0) : VideoDecoderRenderer() {
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

    override fun getCapabilities() = capabilities

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) = Unit
}

internal class StubAudio : AudioRenderer {
    override fun setup(
        audioConfiguration: MoonBridge.AudioConfiguration,
        sampleRate: Int,
        samplesPerFrame: Int,
    ) = 0

    override fun start() = Unit

    override fun stop() = Unit

    override fun playDecodedAudio(audioData: ShortArray?) = Unit

    override fun cleanup() = Unit
}

/**
 * A GameStream host as the start sees it: `/serverinfo`, `/applist`, `/launch`, `/resume` and
 * `/cancel`, over HTTP and HTTPS. Every field can change between two requests. The replies queued
 * in [launchReplies], [resumeReplies], [cancelReplies] and [serverInfoReplies] go out first, in
 * order; without one the host accepts the request.
 */
internal class HostScript(private val host: NvFakeHost) {
    @Volatile var appVersion = "7.1.431.-1"
    @Volatile var gfeVersion: String? = null
    @Volatile var codecSupport: String? = null
    @Volatile var state = FREE
    @Volatile var currentGame = 0
    @Volatile var paired = true

    @Volatile var apps: Map<Int, String> = mapOf(DESKTOP_ID to DESKTOP_NAME, OTHER_ID to OTHER_NAME)

    val serverInfoReplies = ConcurrentLinkedQueue<HostReply>()
    val launchReplies = ConcurrentLinkedQueue<HostReply>()
    val resumeReplies = ConcurrentLinkedQueue<HostReply>()
    val cancelReplies = ConcurrentLinkedQueue<HostReply>()

    /** Called before each `/serverinfo` is answered, with the number of requests so far. */
    @Volatile var onServerInfo: (Int) -> Unit = {}

    /** Called before each `/launch` is answered. */
    @Volatile var onLaunch: () -> Unit = {}

    private val serverInfoRequests = AtomicInteger()

    init {
        host.handler = ::answer
    }

    /** The host runs the app: `/serverinfo` says it is busy with it. */
    fun running(appId: Int, busyState: String = BUSY) {
        state = busyState
        currentGame = appId
    }

    private fun answer(request: SeenRequest): HostReply =
        when (request.path) {
            "/serverinfo" -> {
                onServerInfo(serverInfoRequests.incrementAndGet())
                serverInfoReplies.poll() ?: HostReply.xml(serverInfo())
            }
            "/applist" -> HostReply.root(appList())
            "/launch" -> {
                onLaunch()
                launchReplies.poll() ?: HostReply.root(session("gamesession"))
            }
            "/resume" -> resumeReplies.poll() ?: HostReply.root(session("resume"))
            "/cancel" ->
                cancelReplies.poll()
                    ?: HostReply.root("<cancel>1</cancel>").also {
                        state = FREE
                        currentGame = 0
                    }
            else -> HostReply.NOT_FOUND
        }

    private fun session(tag: String) =
        "<$tag>1</$tag><sessionUrl0>rtsp://127.0.0.1:48010</sessionUrl0>"

    private fun appList() =
        apps.entries.joinToString("") {
            "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>${it.value}</AppTitle><ID>${it.key}</ID></App>"
        }

    private fun serverInfo(): String =
        """<?xml version="1.0" encoding="utf-8"?><root status_code="200">""" +
            "<hostname>HOST</hostname><appversion>$appVersion</appversion>" +
            "<uniqueid>00000000-0000-0000-0000-000000000001</uniqueid>" +
            "<HttpsPort>${host.httpsPort}</HttpsPort>" +
            "<PairStatus>${if (paired) 1 else 0}</PairStatus>" +
            "<currentgame>$currentGame</currentgame><state>$state</state>" +
            gfeVersion?.let { "<GfeVersion>$it</GfeVersion>" }.orEmpty() +
            codecSupport?.let { "<ServerCodecModeSupport>$it</ServerCodecModeSupport>" }.orEmpty() +
            "</root>"

    companion object {
        const val FREE = "SUNSHINE_SERVER_FREE"
        const val BUSY = "SUNSHINE_SERVER_BUSY"
        const val GFE_FREE = "MJOLNIR_SERVER_FREE"
        const val GFE_BUSY = "MJOLNIR_SERVER_BUSY"
    }
}

/**
 * One start against a fake host: the host, its script, the listener, and the connections that the
 * test builds. It resets the native stand-in and the semaphore that connections share, and at the
 * end waits for the start threads and closes the host.
 */
class ConnectionRig(private val resetPlace: Boolean = true) : ExternalResource() {
    internal val host = NvFakeHost()
    internal val script = HostScript(host)
    internal val listener = RecordingListener()

    private val context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private val listeners = mutableListOf(listener)

    override fun before() {
        ShadowRecordingBridge.reset()
        ShadowMonkeyActivityManager.monkey = false
        if (resetPlace) resetSemaphore()
    }

    override fun after() {
        listeners.forEach { runCatching { it.awaitEnded() } }
        host.close()
        ShadowMonkeyActivityManager.monkey = false
        resetSemaphore()
    }

    /** The configuration of a 1080p60 stream of the Desktop app, on the local network. */
    fun config(
        app: NvApp? = NvApp(DESKTOP_NAME, DESKTOP_ID, false),
        remote: Int = StreamConfiguration.STREAM_CFG_LOCAL,
        width: Int = 1920,
        height: Int = 1080,
        formats: Int = MoonBridge.VIDEO_FORMAT_H264,
        maxPacketSize: Int = 1024,
        audio: MoonBridge.AudioConfiguration? = MoonBridge.AUDIO_CONFIGURATION_STEREO,
        colorSpace: Int = 0,
        colorRange: Int = 0,
        clientRefreshRateX100: Int = 0,
        refreshRate: Int = 60,
        launchRefreshRate: Int = 60,
    ): StreamConfiguration =
        StreamConfiguration.Builder()
            .setApp(app)
            .setRemoteConfiguration(remote)
            .setResolution(width, height)
            .setRefreshRate(refreshRate)
            .setLaunchRefreshRate(launchRefreshRate)
            .setBitrate(20_000)
            .setMaxPacketSize(maxPacketSize)
            .setSupportedVideoFormats(formats)
            .setAudioConfiguration(audio)
            .setColorSpace(colorSpace)
            .setColorRange(colorRange)
            .setClientRefreshRateX100(clientRefreshRateX100)
            .build()

    /** A second listener, for a second connection: the rig waits for its thread at the end too. */
    internal fun newListener(): RecordingListener = RecordingListener().also { listeners += it }

    /** A connection to the fake host, which pins the certificate of [serverCert]. */
    fun connection(
        config: StreamConfiguration? = config(),
        serverCert: X509Certificate? = TestIdentities.hostRsa.certificate,
        port: Int = host.httpPort,
    ): NvConnection =
        NvConnection(
            context,
            ComputerDetails.AddressTuple("127.0.0.1", port),
            host.httpsPort,
            "id",
            config,
            providerOf(TestIdentities.clientRsa),
            serverCert,
        )

    fun start(
        connection: NvConnection,
        decoder: VideoDecoderRenderer = StubDecoder(),
        audio: AudioRenderer? = StubAudio(),
        listener: NvConnectionListener = this.listener,
    ) = connection.start(audio, decoder, listener)

    /** The requests that reached the host, as `scheme path`. */
    fun requests(): List<String> = host.requests.map { "${it.scheme} ${it.path}" }

    /** The paths that reached the host, in order. */
    fun paths(): List<String> = host.requests.map { it.path }

    /** The one request to [path]. */
    internal fun request(path: String): SeenRequest = host.requests.single { it.path == path }

    private fun resetSemaphore() {
        semaphore().drainPermits()
        semaphore().release()
    }

    /** How many starts the semaphore that serialises them lets through now. */
    fun permits(): Int = semaphore().availablePermits()

    private fun semaphore(): Semaphore =
        NvConnection::class.java.getDeclaredField("connectionAllowed").let {
            it.isAccessible = true
            it.get(null) as Semaphore
        }

    companion object {
        /** The threads of a start have no name of their own. */
        fun isUnnamed(thread: Thread) = UNNAMED_THREAD.matches(thread.name)
    }
}
