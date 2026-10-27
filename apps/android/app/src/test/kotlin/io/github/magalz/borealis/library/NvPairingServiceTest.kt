package io.github.magalz.borealis.library

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.host.launch.HostError
import io.github.magalz.borealis.host.pairing.PairingFailure
import io.github.magalz.borealis.library.domain.PairingOutcome
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.testing.WorkerDispatcherRule
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParserException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NvPairingServiceTest {
    @get:Rule val audit = RecordingAudit()
    @get:Rule val log = RecordingLog()
    @get:Rule val workers = WorkerDispatcherRule()

    private val binder = FakeCmsBinder()
    private val clients = FakeHostClients(binder)
    private val client = clients.client
    private var failureFor: (HostError?, String?, Boolean) -> PairingFailure = { _, fallback, _ ->
        PairingFailure(fallback, restart = false)
    }

    private fun newService(connection: ComputerManagerConnection, io: CoroutineDispatcher) =
        NvPairingService(
            connection = connection,
            clients = clients,
            texts = FakeTexts(),
            errors = { error, fallback, restarted, _ -> failureFor(error, fallback, restarted) },
            io = io,
        )

    private fun TestScope.service(): NvPairingService {
        val io = UnconfinedTestDispatcher(testScheduler)
        return newService(ComputerManagerConnection(FakeCmsConnector(binder), io), io)
    }

    private fun known(
        uuid: String = "host-1",
        state: ComputerDetails.State = ComputerDetails.State.ONLINE,
        pairState: PairState = PairState.NOT_PAIRED,
        runningGameId: Int = 0,
        address: String? = "192.168.1.5",
    ): ComputerDetails =
        details(
                uuid = uuid,
                state = state,
                pairState = pairState,
                runningGameId = runningGameId,
                address = address,
            )
            .also { binder.computers[uuid] = it }

    private fun auditTypes() = audit.events.map { it.type }

    @Test
    fun `preparing gives a four digit PIN, the Web UI address of the host and a device name`() =
        runTest {
            known()

            val instructions = service().prepare("host-1")!!

            assertThat(instructions.pin).matches("[0-9]{4}")
            assertThat(instructions.webUiUrl).isEqualTo("https://192.168.1.5:47990")
            assertThat(instructions.deviceName.lowercase()).doesNotContain("xbox")
            assertThat(instructions.deviceName.lowercase()).doesNotContain("webos")
            assertThat(instructions.deviceName).isNotEmpty()
        }

    @Test
    fun `preparing a host without an active address has no Web UI address`() = runTest {
        known(address = null)

        assertThat(service().prepare("host-1")!!.webUiUrl).isNull()
    }

    @Test
    fun `preparing an unknown host gives nothing, and makes no request`() = runTest {
        assertThat(service().prepare("nobody")).isNull()
        assertThat(clients.created).isEmpty()
    }

    @Test
    fun `a host that is already paired is paired without a PIN being used`() = runTest {
        known()
        client.pairState = PairState.PAIRED

        val outcome = service().pair("host-1", "1234", restarted = false)

        assertThat(outcome).isEqualTo(PairingOutcome.Paired)
        assertThat(client.pins).isEmpty()
        assertThat(audit.events).isEmpty()
    }

    @Test
    fun `a successful pairing pins the host certificate and invalidates its state`() = runTest {
        val live = known()
        val cert = testCertificate()
        client.pairing = { PairResult(PairState.PAIRED, cert) }

        val outcome = service().pair("host-1", "1234", restarted = false)

        assertThat(outcome).isEqualTo(PairingOutcome.Paired)
        assertThat(client.pins).containsExactly("1234")
        assertThat(live.serverCert).isEqualTo(cert)
        assertThat(binder.invalidated).containsExactly("host-1")
        assertThat(auditTypes())
            .containsExactly(AuditType.PAIRING_START, AuditType.PAIRING_SUCCESS)
            .inOrder()
        assertThat(audit.events.map { it.host }.toSet()).containsExactly("Den PC (192.168.1.5)")
    }

    @Test
    fun `after pairing the host is reported as paired before any poll`() = runTest {
        known()
        client.pairing = { PairResult(PairState.PAIRED, testCertificate()) }
        val io = UnconfinedTestDispatcher(testScheduler)
        val connection = ComputerManagerConnection(FakeCmsConnector(binder), io)

        newService(connection, io).pair("host-1", "1234", restarted = false)

        val published = connection.updates.value.getValue("host-1")
        assertThat(published.pairState).isEqualTo(PairState.PAIRED)
        assertThat(published.state).isEqualTo(ComputerDetails.State.UNKNOWN)
    }

    @Test
    fun `a wrong PIN is reported as such`() = runTest {
        known()
        client.pairing = { PairResult(PairState.PIN_WRONG, null) }

        val outcome = service().pair("host-1", "1234", restarted = false)

        assertThat(outcome).isEqualTo(PairingOutcome.PinWrong)
        assertThat(binder.invalidated).isEmpty()
        assertThat(audit.events.last().detail).isEqualTo("reason=wrong_pin")
    }

    @Test
    fun `a host that is pairing with another device is reported as busy`() = runTest {
        known()
        client.pairing = { PairResult(PairState.ALREADY_IN_PROGRESS, null) }

        assertThat(service().pair("host-1", "1234", restarted = false))
            .isEqualTo(PairingOutcome.AlreadyInProgress)
    }

    @Test
    fun `a refused pairing says so, and says that a running game is the reason when one runs`() =
        runTest {
            client.pairing = { PairResult(PairState.FAILED, null) }
            known(runningGameId = 0)
            assertThat(service().pair("host-1", "1234", restarted = false))
                .isEqualTo(PairingOutcome.Failed("pair failed"))

            known(runningGameId = 5)
            assertThat(service().pair("host-1", "1234", restarted = false))
                .isEqualTo(PairingOutcome.Failed("pair in game"))
        }

    @Test
    fun `a host that is offline is not asked`() = runTest {
        known(state = ComputerDetails.State.OFFLINE)
        assertThat(service().pair("host-1", "1234", restarted = false))
            .isEqualTo(PairingOutcome.HostOffline)

        known(address = null)
        assertThat(service().pair("host-1", "1234", restarted = false))
            .isEqualTo(PairingOutcome.HostOffline)

        assertThat(service().pair("nobody", "1234", restarted = false))
            .isEqualTo(PairingOutcome.HostOffline)
        assertThat(clients.created).isEmpty()
    }

    @Test
    fun `a host that drops its pairing session asks the caller to pair again`() = runTest {
        known()
        client.pairing = { throw HostHttpResponseException(400, "Out of order call") }
        var seen: Triple<HostError?, String?, Boolean>? = null
        failureFor = { error, fallback, restarted ->
            seen = Triple(error, fallback, restarted)
            PairingFailure("session dropped", restart = true)
        }

        val outcome = service().pair("host-1", "1234", restarted = false)

        assertThat(outcome).isEqualTo(PairingOutcome.RestartNeeded)
        val (error, fallback, restarted) = seen!!
        assertThat(error).isEqualTo(HostError(400, "Out of order call"))
        assertThat(fallback).contains("400")
        assertThat(restarted).isFalse()
    }

    @Test
    fun `the second attempt is told that it is one`() = runTest {
        known()
        client.pairing = { throw HostHttpResponseException(410, "reset") }
        var restartedSeen: Boolean? = null
        failureFor = { _, _, restarted ->
            restartedSeen = restarted
            PairingFailure("still dropped", restart = false)
        }

        val outcome = service().pair("host-1", "1234", restarted = true)

        assertThat(restartedSeen).isTrue()
        assertThat(outcome).isEqualTo(PairingOutcome.Failed("still dropped"))
        assertThat(auditTypes()).contains(AuditType.PAIRING_START)
        assertThat(audit.events.first().detail).isEqualTo("attempt=2")
    }

    @Test
    fun `other failures show the text the error mapper gives`() = runTest {
        known()
        client.pairing = { throw IOException("connection reset") }

        val outcome = service().pair("host-1", "1234", restarted = false)

        assertThat(outcome).isEqualTo(PairingOutcome.Failed("connection reset"))
    }

    @Test
    fun `the upstream texts stay for an unknown host and a missing page`() = runTest {
        known()
        client.pairing = { throw UnknownHostException("pc.local") }
        assertThat(service().pair("host-1", "1234", restarted = false))
            .isEqualTo(PairingOutcome.Failed("unknown host"))

        client.pairing = { throw FileNotFoundException("/pair") }
        assertThat(service().pair("host-1", "1234", restarted = false))
            .isEqualTo(PairingOutcome.Failed("not found"))

        client.pairing = { throw XmlPullParserException("garbage") }
        assertThat(service().pair("host-1", "1234", restarted = false))
            .isInstanceOf(PairingOutcome.Failed::class.java)
    }

    @Test
    fun `a client that cannot be built is a failure like any other`() = runTest {
        known()
        clients.failure = IOException("No active address")

        assertThat(service().pair("host-1", "1234", restarted = false))
            .isEqualTo(PairingOutcome.Failed("No active address"))
    }

    @Test
    fun `host polling is paused while pairing and resumes afterwards`() = runTest {
        known()
        val io = UnconfinedTestDispatcher(testScheduler)
        val connection = ComputerManagerConnection(FakeCmsConnector(binder), io)
        val collector = launch(io) { connection.hostUpdates().collect {} }

        newService(connection, io).pair("host-1", "1234", restarted = false)

        assertThat(binder.events)
            .containsExactly(
                "startPolling",
                "stopPolling",
                "waitForPollingStopped",
                "pair",
                "startPolling",
            )
            .inOrder()
        collector.cancelAndJoin()
    }

    @Test
    fun `cancelling a pairing aborts the host request and stores nothing`() = runBlocking {
        val live = known()
        val started = client.blockUntilCancelled()
        val io = workers.dispatcher
        val service = newService(ComputerManagerConnection(FakeCmsConnector(binder), io), io)

        val pairing = async(io) { service.pair("host-1", "1234", restarted = false) }
        awaitStarted(started)
        pairing.cancel()
        try {
            withTimeout(WAIT_MS) { pairing.await() }
            throw AssertionError("the cancelled pairing returned an outcome")
        } catch (expected: CancellationException) {
            // The pairing stopped.
        }

        assertThat(client.cancels).isAtLeast(1)
        assertThat(live.serverCert).isNull()
        assertThat(binder.invalidated).isEmpty()
        assertThat(auditTypes()).doesNotContain(AuditType.PAIRING_SUCCESS)
        assertThat(audit.events.filter { it.outcome == AuditOutcome.FAILURE }).isEmpty()
    }

    @Test
    fun `a cancelled pairing resumes polling`() = runBlocking {
        known()
        val started = client.blockUntilCancelled()
        val io = workers.dispatcher
        val connection = ComputerManagerConnection(FakeCmsConnector(binder), io)
        val service = newService(connection, io)
        val collector = launch(io) { connection.hostUpdates().collect {} }
        withTimeout(WAIT_MS) { while (binder.events.isEmpty()) yield() }

        val pairing = async(io) { service.pair("host-1", "1234", restarted = false) }
        awaitStarted(started)
        pairing.cancelAndJoin()

        assertThat(binder.events.last()).isEqualTo("startPolling")
        collector.cancelAndJoin()
    }

    private suspend fun awaitStarted(started: CountDownLatch) =
        withContext(workers.dispatcher) {
            assertThat(started.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue()
        }

    @Test
    fun `the PIN never reaches the log or the audit trail`() = runTest {
        known()
        val pin = "7391"
        client.pairing = { PairResult(PairState.PIN_WRONG, null) }
        service().pair("host-1", pin, restarted = false)
        client.pairing = { throw HostHttpResponseException(500, "pin $pin was refused") }
        service().pair("host-1", pin, restarted = true)
        client.pairing = { PairResult(PairState.PAIRED, testCertificate()) }
        service().pair("host-1", pin, restarted = false)

        val audited = audit.events.joinToString("\n") { "${it.host} ${it.detail}" }
        val logged = log.records.joinToString("\n") { it.toString() }
        assertThat(audited).doesNotContain(pin)
        assertThat(logged).doesNotContain(pin)
    }

    @Test
    fun `an expired PIN is a pairing failure with the reason pin_expired`() = runTest {
        known()
        val service = service()
        service.pair("host-1", "1234", restarted = false)
        audit.events.clear()

        service.recordExpired("host-1")

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.PAIRING_FAILURE)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.detail).isEqualTo("reason=pin_expired")
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
