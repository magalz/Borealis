package io.github.magalz.borealis.host.pairing

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.RecordingAudit
import io.github.magalz.borealis.host.launch.FakeHostRules
import io.github.magalz.borealis.host.launch.HostError
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.Rule
import org.junit.Test

class PairingAuditTest {
    @get:Rule val audit = RecordingAudit()

    private val rules = FakeHostRules()
    private val host = "Den PC (192.168.1.5)"

    private fun describe(
        error: HostError?,
        restarted: Boolean = false,
        fallback: String? = "upstream text",
    ) = PairingErrors.describe(rules, error, fallback, restarted, { "text" }, host)

    @Test
    fun `starting a pairing is recorded with the attempt number`() {
        PairingAudit.started(host, restarted = false)
        PairingAudit.started(host, restarted = true)

        assertThat(audit.events.map { Triple(it.type, it.outcome, it.detail) })
            .containsExactly(
                Triple(AuditType.PAIRING_START, AuditOutcome.SUCCESS, "attempt=1"),
                Triple(AuditType.PAIRING_START, AuditOutcome.SUCCESS, "attempt=2"),
            )
            .inOrder()
        assertThat(audit.events.map { it.host }.toSet()).containsExactly(host)
    }

    @Test
    fun `a paired result is a success`() {
        PairingAudit.finished(host, "PAIRED")

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.PAIRING_SUCCESS)
        assertThat(event.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(event.host).isEqualTo(host)
    }

    @Test
    fun `the other results are failures with a reason`() {
        for ((state, reason) in
            listOf(
                "PIN_WRONG" to "reason=wrong_pin",
                "FAILED" to "reason=refused",
                "ALREADY_IN_PROGRESS" to "reason=already_in_progress",
            )) {
            audit.events.clear()

            PairingAudit.finished(host, state)

            val event = audit.events.single()
            assertThat(event.type).isEqualTo(AuditType.PAIRING_FAILURE)
            assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
            assertThat(event.detail).isEqualTo(reason)
        }
    }

    @Test
    fun `a PIN that expired is a failure with the reason pin_expired`() {
        PairingAudit.expired(host)

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.PAIRING_FAILURE)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.detail).isEqualTo("reason=pin_expired")
        assertThat(event.host).isEqualTo(host)
    }

    @Test
    fun `a state that is not a result records nothing`() {
        PairingAudit.finished(host, "NOT_PAIRED")
        PairingAudit.finished(host, "")

        assertThat(audit.events).isEmpty()
    }

    @Test
    fun `a dropped host session records the failure category and the automatic restart`() {
        describe(HostError(400, "Out of order call to getservercert"))

        assertThat(audit.types())
            .containsExactly(AuditType.PAIRING_FAILURE, AuditType.PAIRING_RESTART)
            .inOrder()
        val failure = audit.events[0]
        assertThat(failure.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(failure.detail)
            .isEqualTo("status=400 category=STALE_SESSION action=RESTART_ONCE restarted=false")
        assertThat(audit.events[1].detail).isEqualTo("category=STALE_SESSION")
        assertThat(audit.events.map { it.host }.toSet()).containsExactly(host)
    }

    @Test
    fun `a second dropped session is a failure without another restart`() {
        describe(HostError(410, "Host pairing state was reset"), restarted = true)

        assertThat(audit.types()).containsExactly(AuditType.PAIRING_FAILURE)
        assertThat(audit.events.single().detail)
            .isEqualTo("status=410 category=HOST_RESET action=RESTART_ONCE restarted=true")
    }

    @Test
    fun `a failure that needs no restart is recorded with its category`() {
        describe(HostError(409, "Pairing exchange already in progress"))

        assertThat(audit.types()).containsExactly(AuditType.PAIRING_FAILURE)
        assertThat(audit.events.single().detail)
            .isEqualTo("status=409 category=IN_PROGRESS action=WAIT_HOST_SESSION restarted=false")
    }

    @Test
    fun `an error the rules do not know is an unclassified failure`() {
        describe(HostError(500, "boom"))
        describe(null)

        assertThat(audit.events.map { it.detail })
            .containsExactly(
                "status=500 category=UNKNOWN action=FAIL restarted=false",
                "category=unclassified",
            )
            .inOrder()
    }

    @Test
    fun `the host text and the upstream message never reach the audit trail`() {
        describe(HostError(400, "Invalid uniqueid pin=4321 salt=0123456789ABCDEF"), fallback = "x")
        describe(HostError(500, "token=abcdef0123456789 secret text"), fallback = "secret fallback")

        for (event in audit.events) {
            assertThat(event.detail).doesNotContain("4321")
            assertThat(event.detail).doesNotContain("0123456789ABCDEF")
            assertThat(event.detail).doesNotContain("abcdef0123456789")
            assertThat(event.detail).doesNotContain("secret")
        }
    }
}
