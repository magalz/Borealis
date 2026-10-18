package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Test

class BorealisAuditTest {
    private val events = CopyOnWriteArrayList<AuditEvent>()
    private val redactor = Redactor()
    private val now = 1_700_000_000_000L

    private fun install(sink: AuditSink = AuditSink { events += it }) =
        BorealisAudit.install(sink, redactor, "1.2.3", clock = { now })

    @After fun tearDown() = BorealisAudit.uninstall()

    @Test
    fun `before install an event is dropped without an error`() {
        BorealisAudit.record(AuditType.PAIRING_START, AuditOutcome.SUCCESS, "host", "detail")

        install()

        assertThat(events).isEmpty()
    }

    @Test
    fun `after uninstall events are dropped again`() {
        install()
        BorealisAudit.uninstall()

        BorealisAudit.record(AuditType.PAIRING_START, AuditOutcome.SUCCESS)

        assertThat(events).isEmpty()
    }

    @Test
    fun `an event carries the time, the app version and what the caller gave`() {
        install()

        BorealisAudit.record(AuditType.LAUNCH_ATTEMPT, AuditOutcome.FAILURE, "den-pc", "status=503")

        assertThat(events)
            .containsExactly(
                AuditEvent(
                    timestampMillis = 1_700_000_000_000L,
                    type = AuditType.LAUNCH_ATTEMPT,
                    outcome = AuditOutcome.FAILURE,
                    host = "den-pc",
                    detail = "status=503",
                    appVersion = "1.2.3",
                )
            )
    }

    @Test
    fun `a missing host or detail stays missing`() {
        install()

        BorealisAudit.record(AuditType.SESSION_END, AuditOutcome.SUCCESS)

        assertThat(events.single().host).isNull()
        assertThat(events.single().detail).isNull()
    }

    @Test
    fun `free-text detail is redacted before the sink sees it`() {
        install()

        BorealisAudit.record(
            AuditType.PAIRING_FAILURE,
            AuditOutcome.FAILURE,
            detail = "pin=1234 token=abc123 rikey=00112233445566778899AABBCCDDEEFF",
        )

        val detail = events.single().detail!!
        assertThat(detail).doesNotContain("1234")
        assertThat(detail).doesNotContain("abc123")
        assertThat(detail).doesNotContain("00112233445566778899AABBCCDDEEFF")
    }

    @Test
    fun `an oversized detail is cut`() {
        install()

        BorealisAudit.record(
            AuditType.TLS_FAILURE,
            AuditOutcome.FAILURE,
            detail = "x".repeat(10_000),
        )

        assertThat(events.single().detail!!.length).isLessThan(1_000)
    }

    @Test
    fun `the host follows the mask addresses switch at the time of writing`() {
        install()
        val host = "Den PC (192.168.1.5)"

        BorealisAudit.record(AuditType.PAIRING_START, AuditOutcome.SUCCESS, host)
        redactor.maskAddresses = true
        BorealisAudit.record(AuditType.PAIRING_START, AuditOutcome.SUCCESS, host)

        assertThat(events.map { it.host }).containsExactly(host, "[HOST]").inOrder()
    }

    @Test
    fun `addresses in the detail are masked when the switch is on`() {
        install()
        redactor.maskAddresses = true

        BorealisAudit.record(AuditType.TLS_FAILURE, AuditOutcome.FAILURE, detail = "to 10.0.0.7")

        assertThat(events.single().detail).isEqualTo("to [IP]")
    }

    @Test
    fun `a sink that throws never reaches the caller`() {
        install(AuditSink { error("disk full") })

        BorealisAudit.record(AuditType.PAIRING_START, AuditOutcome.SUCCESS)
    }

    @Test
    fun `no canary secret reaches the sink through the host or the detail`() {
        val canaries = RedactionCorpus.canaries()
        for (masking in listOf(false, true)) {
            events.clear()
            install()
            redactor.maskAddresses = masking

            for (canary in canaries) {
                BorealisAudit.record(
                    AuditType.PAIRING_FAILURE,
                    AuditOutcome.FAILURE,
                    host = canary.input,
                    detail = canary.input,
                )
            }

            assertThat(events).hasSize(canaries.size)
            for ((event, canary) in events.zip(canaries)) {
                assertThat(event.host).doesNotContain(canary.secret)
                assertThat(event.detail).doesNotContain(canary.secret)
            }
        }
    }

    @Test
    fun `an export applies the current switch again to an entry written while it was off`() {
        install()
        val stored =
            AuditEvent(
                1L,
                AuditType.SESSION_START,
                AuditOutcome.SUCCESS,
                "Den PC (192.168.1.5)",
                "to 192.168.1.5",
                "1.2.3",
            )

        redactor.maskAddresses = true
        val exported = BorealisAudit.redactForExport(stored)

        assertThat(exported.host).isEqualTo("[HOST]")
        assertThat(exported.detail).isEqualTo("to [IP]")
        assertThat(exported.copy(host = stored.host, detail = stored.detail)).isEqualTo(stored)
    }

    @Test
    fun `an export redacts even before install`() {
        val stored =
            AuditEvent(1L, AuditType.PAIRING_FAILURE, AuditOutcome.FAILURE, null, "pin=1234", "1")

        assertThat(BorealisAudit.redactForExport(stored).detail).doesNotContain("1234")
    }

    @Test
    fun `a host label joins the name and the address`() {
        assertThat(BorealisAudit.hostLabel("Den PC", "192.168.1.5"))
            .isEqualTo("Den PC (192.168.1.5)")
        assertThat(BorealisAudit.hostLabel("Den PC", null)).isEqualTo("Den PC")
        assertThat(BorealisAudit.hostLabel(null, "192.168.1.5")).isEqualTo("192.168.1.5")
        assertThat(BorealisAudit.hostLabel("", " ")).isNull()
        assertThat(BorealisAudit.hostLabel(null, null)).isNull()
    }
}
