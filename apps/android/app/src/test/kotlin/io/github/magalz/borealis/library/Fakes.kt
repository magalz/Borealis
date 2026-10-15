package io.github.magalz.borealis.library

import android.app.Activity
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
import java.io.IOException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

@Suppress("LongParameterList")
fun details(
    uuid: String = "host-1",
    name: String = "Den PC",
    state: ComputerDetails.State = ComputerDetails.State.ONLINE,
    pairState: PairState = PairState.PAIRED,
    runningGameId: Int = 0,
    mac: String? = null,
    rawAppList: String? = null,
    address: String? = "192.168.1.5",
): ComputerDetails =
    ComputerDetails().also {
        it.uuid = uuid
        it.name = name
        it.state = state
        it.pairState = pairState
        it.runningGameId = runningGameId
        it.macAddress = mac
        it.rawAppList = rawAppList
        it.activeAddress = address?.let { host -> AddressTuple(host, 47989) }
        it.httpsPort = 47984
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

    /** What polling a new host does to its details; returns whether the host answered. */
    var onAdd: (ComputerDetails) -> Boolean = { true }

    override fun startPolling(listener: ComputerManagerListener) {
        log += "startPolling"
        this.listener = listener
        // Like the service, which reports every known host when polling starts.
        computers.values.forEach { listener.notifyComputerUpdated(it) }
    }

    override fun stopPolling() {
        log += "stopPolling"
        listener = null
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
        FakeAppListPoller(computer).also { pollers += it }

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

    override suspend fun connect(): CmsBinder {
        connects++
        return binder
    }
}

internal class FakeHostClient(private val binder: FakeCmsBinder) : HostClient {
    var pairState = PairState.NOT_PAIRED
    var pairing: (String) -> PairResult = { PairResult(PairState.PAIRED, null) }
    var unpairResult = true
    var quit: () -> Boolean = { true }
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
