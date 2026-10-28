package io.github.magalz.borealis.library

import android.app.Activity
import android.graphics.Bitmap
import com.limelight.computers.ComputerManagerListener
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditSink
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import io.github.magalz.borealis.logging.Redactor
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.junit.rules.ExternalResource

/** One valid app list: Steam (HDR) and Desktop. */
const val APP_LIST =
    """<?xml version="1.0" encoding="utf-8"?><root status_code="200">""" +
        """<App><IsHdrSupported>1</IsHdrSupported><AppTitle>Steam</AppTitle><ID>7</ID></App>""" +
        """<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Desktop</AppTitle><ID>8</ID></App>""" +
        """</root>"""

private const val TEST_CERTIFICATE =
    """-----BEGIN CERTIFICATE-----
MIIBSDCB76ADAgECAggI6+NbQgGFEjAKBggqhkjOPQQDAjAYMRYwFAYDVQQDEw1i
b3JlYWxpcy10ZXN0MCAXDTI2MTAxNTE2NTczNloYDzIxMjYwOTIxMTY1NzM2WjAY
MRYwFAYDVQQDEw1ib3JlYWxpcy10ZXN0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD
QgAEUqQ1SaEsJ8we4mFQm2TScD8QxlKuO5E69NyFRLMZYkUK3RkBk40afO52dvMZ
gF66NFHBq3oJKx4dYWOlyxS1oaMhMB8wHQYDVR0OBBYEFCps4CGuGitfWdhmS5PI
itIHQF2LMAoGCCqGSM49BAMCA0gAMEUCIEIhjexo71zkeWj9Lv7jkIa0HS34sMof
Aa0IS+FzR4KuAiEApx8Blhap/5Q8wzQHPjTmpq2PMo3YOFXO9b2FcY5GLaU=
-----END CERTIFICATE-----"""

/** A self-signed certificate, only for pinning tests. */
fun testCertificate(): X509Certificate =
    CertificateFactory.getInstance("X.509")
        .generateCertificate(ByteArrayInputStream(TEST_CERTIFICATE.toByteArray()))
        as X509Certificate

fun details(
    uuid: String = "host-1",
    name: String = "Den PC",
    runningGameId: Int = 0,
    rawAppList: String? = null,
    address: String? = "192.168.1.5",
): ComputerDetails =
    ComputerDetails().also {
        it.uuid = uuid
        it.name = name
        it.state = ComputerDetails.State.ONLINE
        it.pairState = PairState.PAIRED
        it.runningGameId = runningGameId
        it.rawAppList = rawAppList
        it.activeAddress = address?.let { host -> AddressTuple(host, 47989) }
        it.httpsPort = 47984
    }

/** The details with what the host reports of itself: [state], [pairState] and [mac]. */
fun ComputerDetails.reported(
    state: ComputerDetails.State = ComputerDetails.State.ONLINE,
    pairState: PairState = PairState.PAIRED,
    mac: String? = null,
): ComputerDetails = also {
    it.state = state
    it.pairState = pairState
    it.macAddress = mac
}

/** Routes the audit facade into [events] for one test. */
class RecordingAudit : ExternalResource(), AuditSink {
    val events = CopyOnWriteArrayList<AuditEvent>()

    override fun before() {
        BorealisAudit.install(this, Redactor(), "test", clock = { 1_700_000_000_000L })
    }

    override fun after() {
        BorealisAudit.uninstall()
    }

    override fun write(event: AuditEvent) {
        events += event
    }
}

/** Routes the log facade into [records] for one test. */
class RecordingLog : ExternalResource(), LogSink {
    val records = CopyOnWriteArrayList<LogRecord>()

    override fun before() {
        BorealisLog.initialize(listOf(this), LogLevel.VERBOSE)
    }

    override fun after() {
        BorealisLog.shutdown()
    }

    override fun write(record: LogRecord) {
        records += record
    }
}

internal class FakeAppListPoller(val computer: ComputerDetails) : AppListPoller {
    var started = 0
    var stopped = 0

    override fun start() {
        started++
    }

    override fun stop() {
        stopped++
    }
}

internal class FakeCmsBinder : CmsBinder {
    private val log = CopyOnWriteArrayList<String>()

    /** The calls that changed polling, in order. */
    val events: List<String>
        get() = log

    var listener: ComputerManagerListener? = null
    override val uniqueId = "unique-id"

    /** The service's own details objects, by uuid. */
    val computers = mutableMapOf<String, ComputerDetails>()
    val invalidated = mutableListOf<String>()
    val removed = mutableListOf<ComputerDetails>()
    val pollers = mutableListOf<FakeAppListPoller>()
    val streams = mutableListOf<Triple<Activity, NvApp, ComputerDetails>>()

    /** Run inside the call of the same name, like something happening on another thread. */
    var onStartPolling: () -> Unit = {}
    var onStopPolling: () -> Unit = {}
    var onCreatePoller: () -> Unit = {}

    /** What polling a new host does to its details; returns whether the host answered. */
    var onAdd: (ComputerDetails) -> Boolean = { true }

    override fun startPolling(listener: ComputerManagerListener) {
        log += "startPolling"
        this.listener = listener
        onStartPolling()
        // Like the service, which reports every known host when polling starts.
        computers.values.forEach { listener.notifyComputerUpdated(it) }
    }

    override fun stopPolling() {
        log += "stopPolling"
        listener = null
        onStopPolling()
    }

    override fun waitForPollingStopped() {
        log += "waitForPollingStopped"
    }

    override fun addComputerBlocking(details: ComputerDetails): Boolean = onAdd(details)

    override fun removeComputer(details: ComputerDetails) {
        removed += details
    }

    override fun getComputer(uuid: String): ComputerDetails? = computers[uuid]

    override fun invalidateStateForComputer(uuid: String) {
        invalidated += uuid
    }

    override fun createAppListPoller(computer: ComputerDetails): AppListPoller =
        FakeAppListPoller(computer).also {
            pollers += it
            onCreatePoller()
        }

    override fun startStream(activity: Activity, app: NvApp, computer: ComputerDetails) {
        streams += Triple(activity, app, computer)
    }

    /** What the service does on a polling thread. */
    fun emit(details: ComputerDetails) {
        checkNotNull(listener) { "polling is not running" }.notifyComputerUpdated(details)
    }

    fun record(event: String) {
        log += event
    }
}

internal class FakeCmsConnector(val binder: FakeCmsBinder = FakeCmsBinder()) : CmsConnector {
    var connects = 0
    var failure: IOException? = null

    override suspend fun connect(): CmsBinder {
        connects++
        failure?.let { throw it }
        return binder
    }
}

internal class FakeHostClient(private val binder: FakeCmsBinder) : HostClient {
    var pairState = PairState.NOT_PAIRED
    var pairing: (String) -> PairResult = { PairResult(PairState.PAIRED, null) }
    var unpairResult = true
    var quit: () -> Boolean = { true }

    /** What the host answers for the art of an app; by default it has none (404). */
    var boxArtAnswer: (Int) -> InputStream = { throw FileNotFoundException("404") }
    val boxArtRequests = mutableListOf<Int>()
    val pins = mutableListOf<String>()
    var unpairs = 0
    var cancels = 0
    private var onCancel: () -> Unit = {}

    override fun pairState(): PairState = pairState

    override fun pair(pin: String): PairResult {
        binder.record("pair")
        pins += pin
        return pairing(pin)
    }

    override fun unpair(): Boolean {
        unpairs++
        return unpairResult
    }

    override fun quitApp(): Boolean = quit()

    override fun boxArt(appId: Int): InputStream {
        boxArtRequests += appId
        return boxArtAnswer(appId)
    }

    override fun cancelPendingRequests() {
        cancels++
        onCancel()
    }

    /** A pairing that blocks like the host does until the user types the PIN, until cancelled. */
    fun blockUntilCancelled(): CountDownLatch {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        pairing = {
            started.countDown()
            cancelled.await(BLOCK_SECONDS, TimeUnit.SECONDS)
            throw IOException("Canceled")
        }
        onCancel = { cancelled.countDown() }
        return started
    }

    private companion object {
        const val BLOCK_SECONDS = 10L
    }
}

internal class FakeHostClients(
    private val binder: FakeCmsBinder,
    val client: FakeHostClient = FakeHostClient(binder),
) : HostClientFactory {
    var failure: IOException? = null
    val created = mutableListOf<ComputerDetails>()

    override fun create(details: ComputerDetails, uniqueId: String): HostClient {
        failure?.let { throw it }
        created += details
        return client
    }
}

internal class FakeTexts : HostTexts {
    override fun unknownHost() = "unknown host"

    override fun notFound() = "not found"

    override fun pairFailed() = "pair failed"

    override fun pairInGame() = "pair in game"
}

internal class FakeBoxArt : BoxArt {
    /** The art of every app, by app id. */
    val art = mutableMapOf<Int, Bitmap>()
    val prefetched = mutableListOf<Pair<String, Int>>()

    override suspend fun bitmap(hostId: String, appId: Int): Bitmap? = art[appId]

    override fun prefetch(hostId: String, appId: Int): Job {
        prefetched += hostId to appId
        return CompletableDeferred(Unit)
    }
}

internal class FakeHiddenAppShortcuts : HiddenAppShortcuts {
    /** One entry per call, in order: "hidden host-1 7" or "shown host-1 7". */
    val calls = mutableListOf<String>()

    /** Thrown by every call after it is recorded, like a launcher that fails. */
    var failure: RuntimeException? = null

    override fun appHidden(hostId: String, appId: Int) {
        calls += "hidden $hostId $appId"
        failure?.let { throw it }
    }

    override fun appShown(hostId: String, appId: Int) {
        calls += "shown $hostId $appId"
        failure?.let { throw it }
    }
}
