package io.github.magalz.borealis.diagnostics.audit

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.Test

class AuditDeduplicatorTest {
    private val dedup = AuditDeduplicator()

    /** Asks like the store does: store the event and remember it only when it was stored. */
    private fun offer(event: AuditEvent): Boolean {
        val store = dedup.shouldStore(event)
        if (store) dedup.markStored(event)
        return store
    }

    /** An event of a type that repeats on every `/serverinfo` poll. */
    private fun repeating(
        at: Long = 0,
        type: AuditType = AuditType.CERTIFICATE_CHANGED,
        host: String? = "den-pc",
        detail: String? = "detail",
        outcome: AuditOutcome = AuditOutcome.FAILURE,
    ) = event(at = at, type = type, host = host, detail = detail, outcome = outcome)

    @Test
    fun `a repeating event is stored once per day`() {
        assertThat(offer(repeating(at = 0))).isTrue()
        assertThat(offer(repeating(at = 1))).isFalse()
        assertThat(offer(repeating(at = 10 * MINUTE))).isFalse()
        assertThat(offer(repeating(at = DAY - 1))).isFalse()
    }

    @Test
    fun `it is stored again once a day has passed`() {
        offer(repeating(at = 0))

        assertThat(offer(repeating(at = DAY))).isTrue()
    }

    @Test
    fun `a suppressed repeat does not extend the window`() {
        offer(repeating(at = 0))
        offer(repeating(at = DAY - MINUTE))

        assertThat(offer(repeating(at = DAY))).isTrue()
    }

    @Test
    fun `both polled types are deduplicated`() {
        for (type in listOf(AuditType.CERTIFICATE_CHANGED, AuditType.TLS_FAILURE)) {
            assertThat(offer(repeating(at = 0, type = type))).isTrue()
            assertThat(offer(repeating(at = 1, type = type))).isFalse()
        }
    }

    @Test
    fun `no other type is ever deduplicated`() {
        val others = AuditType.entries - setOf(AuditType.CERTIFICATE_CHANGED, AuditType.TLS_FAILURE)
        assertThat(others).isNotEmpty()

        for (type in others) {
            assertThat(offer(event(at = 0, type = type))).isTrue()
            assertThat(offer(event(at = 1, type = type))).isTrue()
        }
        assertThat(dedup.size).isEqualTo(0)
    }

    @Test
    fun `another host or detail is a different event`() {
        offer(repeating(at = 0))

        assertThat(offer(repeating(at = 1, host = "other-pc"))).isTrue()
        assertThat(offer(repeating(at = 1, detail = "other"))).isTrue()
        assertThat(offer(repeating(at = 1, host = null))).isTrue()
        assertThat(offer(repeating(at = 1, type = AuditType.TLS_FAILURE))).isTrue()
    }

    @Test
    fun `the outcome and the time do not make an event different`() {
        offer(repeating(at = 0))

        assertThat(offer(repeating(at = 5, outcome = AuditOutcome.SUCCESS))).isFalse()
    }

    @Test
    fun `an event that was asked about but not stored is not remembered`() {
        assertThat(dedup.shouldStore(repeating(at = 0))).isTrue()
        assertThat(dedup.shouldStore(repeating(at = 1))).isTrue()
    }

    @Test
    fun `a clock that went backwards stores the event`() {
        offer(repeating(at = DAY))

        assertThat(offer(repeating(at = 0))).isTrue()
    }

    @Test
    fun `clearing forgets what was stored`() {
        offer(repeating(at = 0))

        dedup.clear()

        assertThat(offer(repeating(at = 1))).isTrue()
    }

    @Test
    fun `expired entries are dropped so the window stays small`() {
        for (i in 0 until 50) offer(repeating(at = 0, detail = "d$i"))

        offer(repeating(at = 2 * DAY, detail = "new"))

        assertThat(dedup.size).isEqualTo(1)
    }

    @Test
    fun `a flood of different events cannot grow the window without bound`() {
        for (i in 0 until 10_000) offer(repeating(at = 0, detail = "d$i"))

        assertThat(dedup.size).isAtMost(AuditDeduplicator.MAX_KEYS)
        assertThat(dedup.size).isGreaterThan(0)
    }
}
