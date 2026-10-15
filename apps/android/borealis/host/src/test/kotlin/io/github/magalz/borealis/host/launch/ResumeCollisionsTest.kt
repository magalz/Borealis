package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import io.github.magalz.borealis.host.RecordingAudit
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.nativecore.LaunchCategory
import java.net.SocketTimeoutException
import org.junit.Rule
import org.junit.Test

class ResumeCollisionsTest {
    @get:Rule val log = RecordingLogSink()
    @get:Rule val audit = RecordingAudit()

    private val host = "192.168.1.5"

    /** Fake time: sleeping advances the clock, so the waits and the window can be seen. */
    private var now = 0L
    private val slices = mutableListOf<Long>()
    private var cancelled = false
    private var onSlice: (Int) -> Unit = {}

    private class Progress(val category: Int, val attempt: Int, val max: Int, val delayMs: Int)

    private val progress = mutableListOf<Progress>()

    private val collisions =
        ResumeCollisions(
            { cancelled },
            { category, attempt, max, delayMs ->
                progress += Progress(category, attempt, max, delayMs)
            },
            { host },
            { now },
            {
                slices += it
                now += it
                onSlice(slices.size)
            },
        )

    private fun serverInfo(takes: Long = 100L) {
        collisions.readServerInfo {
            now += takes
            "<root/>"
        }
    }

    /** A start that read `/serverinfo` fast, settled and had its `/resume` accepted. */
    private fun resumedStart(isGfe: Boolean = false) {
        serverInfo()
        assertThat(collisions.settleBeforeResume()).isTrue()
        collisions.resumeAccepted(isGfe)
    }

    private fun rtspFailure() =
        collisions.onStageFailed(true, ResumeCollisions.RTSP_UNREADABLE_REPLY)

    @Test
    fun `a serverinfo answer under 3 s does not wait before resume`() {
        serverInfo(takes = 2_999L)

        assertThat(collisions.settleBeforeResume()).isTrue()
        assertThat(slices).isEmpty()
    }

    @Test
    fun `a serverinfo answer over 3 s waits 3 s before resume`() {
        serverInfo(takes = 3_000L)

        assertThat(collisions.settleBeforeResume()).isTrue()
        assertThat(slices.sum()).isEqualTo(3_000L)
        assertThat(progress).isEmpty()
    }

    @Test
    fun `a serverinfo read that needed the timeout retry waits before resume`() {
        var calls = 0
        collisions.readServerInfo {
            if (++calls == 1) throw SocketTimeoutException("timed out")
            "<root/>"
        }

        assertThat(collisions.settleBeforeResume()).isTrue()
        assertThat(calls).isEqualTo(2)
        assertThat(slices.sum()).isEqualTo(3_000L)
    }

    @Test
    fun `the settle wait is cut into slices of at most 250 ms`() {
        serverInfo(takes = 6_700L)

        collisions.settleBeforeResume()

        assertThat(slices).hasSize(12)
        assertThat(slices.toSet()).containsExactly(250L)
    }

    @Test
    fun `a cancel during the settle wait returns false`() {
        serverInfo(takes = 6_700L)
        onSlice = { if (it == 2) cancelled = true }

        assertThat(collisions.settleBeforeResume()).isFalse()
        assertThat(slices).hasSize(2)
    }

    @Test
    fun `an interrupt during the settle wait returns false and keeps the interrupt flag`() {
        val interrupting =
            ResumeCollisions(
                { false },
                { _, _, _, _ -> },
                { host },
                { 10_000L },
                { throw InterruptedException() },
            )
        interrupting.readServerInfo { "<root/>" }
        interrupting.resumeAccepted(false)
        interrupting.onStageFailed(true, -1)
        interrupting.restartAfterFailure()
        try {
            assertThat(interrupting.settleBeforeResume()).isFalse()
            assertThat(Thread.currentThread().isInterrupted).isTrue()
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `RTSP error -1 within 5 s of an accepted resume is held and restarts exactly once`() {
        resumedStart()
        now += 120L

        assertThat(rtspFailure()).isEqualTo(StageFailure.HOLD)
        assertThat(collisions.isRestart()).isFalse()
        assertThat(collisions.restartAfterFailure()).isTrue()
        assertThat(collisions.isRestart()).isTrue()
        assertThat(collisions.collided()).isFalse()
        // Only one restart is granted, however often it is asked.
        assertThat(collisions.restartAfterFailure()).isFalse()
    }

    @Test
    fun `the restart waits and reports the pending retry progress even after a fast serverinfo`() {
        resumedStart()
        rtspFailure()
        collisions.restartAfterFailure()

        serverInfo()
        assertThat(collisions.settleBeforeResume()).isTrue()

        assertThat(slices.sum()).isEqualTo(3_000L)
        val shown = progress.single()
        assertThat(shown.category).isEqualTo(LaunchCategory.PENDING.ordinal)
        assertThat(shown.attempt).isEqualTo(1)
        assertThat(shown.max).isEqualTo(1)
        assertThat(shown.delayMs).isEqualTo(3_000)
    }

    @Test
    fun `RTSP error -1 after the restart is reported as a collision`() {
        resumedStart()
        rtspFailure()
        collisions.restartAfterFailure()
        resumedStart()

        assertThat(rtspFailure()).isEqualTo(StageFailure.REPORT_COLLISION)
        assertThat(collisions.collided()).isTrue()
        assertThat(collisions.restartAfterFailure()).isFalse()
        assertThat(log.records.filter { it.level == LogLevel.WARN }).isNotEmpty()
        val last = audit.events.last()
        assertThat(last.type).isEqualTo(AuditType.LAUNCH_ATTEMPT)
        assertThat(last.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(last.host).isEqualTo(host)
        assertThat(last.detail).isEqualTo("verb=resume result=rtsp_failed error=-1")
    }

    @Test
    fun `an RTSP socket error such as 110 is reported unchanged`() {
        resumedStart()

        assertThat(collisions.onStageFailed(true, 110)).isEqualTo(StageFailure.REPORT)
        assertThat(collisions.restartAfterFailure()).isFalse()
        assertThat(collisions.collided()).isFalse()
    }

    @Test
    fun `a failure in another stage is reported unchanged`() {
        resumedStart()

        assertThat(collisions.onStageFailed(false, -1)).isEqualTo(StageFailure.REPORT)
        assertThat(collisions.restartAfterFailure()).isFalse()
    }

    @Test
    fun `an RTSP failure without an accepted resume is reported unchanged`() {
        serverInfo()
        collisions.settleBeforeResume()

        assertThat(rtspFailure()).isEqualTo(StageFailure.REPORT)
        assertThat(collisions.restartAfterFailure()).isFalse()
    }

    @Test
    fun `an RTSP failure after the window is reported unchanged`() {
        resumedStart()
        now += ResumeCollisions.RTSP_FAILURE_WINDOW_MS + 1

        assertThat(rtspFailure()).isEqualTo(StageFailure.REPORT)
        assertThat(collisions.restartAfterFailure()).isFalse()
    }

    @Test
    fun `a GFE host never holds a failure`() {
        resumedStart(isGfe = true)

        assertThat(rtspFailure()).isEqualTo(StageFailure.REPORT)
        assertThat(collisions.restartAfterFailure()).isFalse()
    }

    @Test
    fun `a held failure after a cancel starts no restart`() {
        resumedStart()
        assertThat(rtspFailure()).isEqualTo(StageFailure.HOLD)
        cancelled = true

        assertThat(collisions.restartAfterFailure()).isFalse()
        assertThat(audit.events).isEmpty()
    }

    @Test
    fun `a failure after a cancel is not held`() {
        resumedStart()
        cancelled = true

        assertThat(rtspFailure()).isEqualTo(StageFailure.REPORT)
    }

    @Test
    fun `an automatic restart refuses to quit another running app`() {
        resumedStart()
        rtspFailure()
        collisions.restartAfterFailure()

        assertThat(collisions.refuseQuit()).isFalse()
        assertThat(collisions.collided()).isTrue()
    }

    @Test
    fun `withHint appends the text only after a collision`() {
        val getString = { id: Int -> if (id == R.string.launch_resume_collision) "HINT" else "?" }

        assertThat(ResumeCollisions.withHint(true, "message", getString))
            .isEqualTo("message\n\nHINT")
        assertThat(ResumeCollisions.withHint(false, "message", getString)).isEqualTo("message")
    }

    @Test
    fun `the restart is audited once as LAUNCH_RETRY`() {
        resumedStart()
        now += 120L
        rtspFailure()
        collisions.restartAfterFailure()
        collisions.restartAfterFailure()

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.LAUNCH_RETRY)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.host).isEqualTo(host)
        assertThat(event.detail)
            .isEqualTo("verb=resume reason=rtsp_after_resume error=-1 attempt=1/1 delay_ms=3000")
    }

    @Test
    fun `no wait or restart log line contains the host address`() {
        serverInfo(takes = 4_000L)
        collisions.settleBeforeResume()
        collisions.resumeAccepted(false)
        rtspFailure()
        collisions.restartAfterFailure()
        serverInfo()
        collisions.settleBeforeResume()
        collisions.resumeAccepted(false)
        rtspFailure()

        assertThat(log.records.map { it.message })
            .containsAtLeast(
                "host busy, waiting before resume",
                "rtsp failed after resume, restarting once",
            )
        val text = log.records.joinToString { "${it.message} ${it.fields}" }
        assertThat(text).doesNotContain(host)
    }
}
