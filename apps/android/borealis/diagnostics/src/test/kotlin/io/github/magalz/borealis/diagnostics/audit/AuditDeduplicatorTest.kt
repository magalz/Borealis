package io.github.magalz.borealis.diagnostics.audit

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.Test

class AuditDeduplicatorTest {
    private val dedup = AuditDeduplicator()

    @Test
    fun `the first event is stored and a repeat inside ten minutes is not`() {
        assertThat(dedup.shouldStore(event(at = 0))).isTrue()
        assertThat(dedup.shouldStore(event(at = 1))).isFalse()
        assertThat(dedup.shouldStore(event(at = 10 * MINUTE - 1))).isFalse()
    }

    @Test
    fun `the same event is stored again once ten minutes have passed`() {
        dedup.shouldStore(event(at = 0))

        assertThat(dedup.shouldStore(event(at = 10 * MINUTE))).isTrue()
    }

    @Test
    fun `a suppressed repeat does not extend the window`() {
        dedup.shouldStore(event(at = 0))
        dedup.shouldStore(event(at = 9 * MINUTE))

        assertThat(dedup.shouldStore(event(at = 10 * MINUTE))).isTrue()
    }

    @Test
    fun `another type, host or detail is a different event`() {
        dedup.shouldStore(event(at = 0))

        assertThat(dedup.shouldStore(event(at = 1, type = AuditType.TLS_FAILURE))).isTrue()
        assertThat(dedup.shouldStore(event(at = 1, host = "other-pc"))).isTrue()
        assertThat(dedup.shouldStore(event(at = 1, detail = "other"))).isTrue()
        assertThat(dedup.shouldStore(event(at = 1, host = null))).isTrue()
    }

    @Test
    fun `the outcome and the time do not make an event different`() {
        dedup.shouldStore(event(at = 0))

        assertThat(dedup.shouldStore(event(at = 5, outcome = AuditOutcome.FAILURE))).isFalse()
    }

    @Test
    fun `a clock that went backwards stores the event`() {
        dedup.shouldStore(event(at = 10 * MINUTE))

        assertThat(dedup.shouldStore(event(at = 0))).isTrue()
    }

    @Test
    fun `clearing forgets what was stored`() {
        dedup.shouldStore(event(at = 0))

        dedup.clear()

        assertThat(dedup.shouldStore(event(at = 1))).isTrue()
    }

    @Test
    fun `expired entries are dropped so the window stays small`() {
        for (i in 0 until 50) dedup.shouldStore(event(at = 0, detail = "d$i"))

        dedup.shouldStore(event(at = 11 * MINUTE, detail = "new"))

        assertThat(dedup.size).isEqualTo(1)
    }

    @Test
    fun `a flood of different events cannot grow the window without bound`() {
        for (i in 0 until 10_000) dedup.shouldStore(event(at = 0, detail = "d$i"))

        assertThat(dedup.size).isAtMost(AuditDeduplicator.MAX_KEYS)
        assertThat(dedup.size).isGreaterThan(0)
    }
}
