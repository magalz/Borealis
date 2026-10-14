package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.nativecore.LaunchVerb
import java.util.function.BooleanSupplier
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class LaunchRetriesTest {
    @get:Rule val log = RecordingLogSink()

    private val rules = FakeHostRules()
    private val sleeps = mutableListOf<Long>()
    private val runner = LaunchRetryRunner(rules, clock = { 0L }, sleeper = { sleeps += it })
    private val extractor = HostErrorExtractor { (it as? HostAnswer)?.error }
    private val progress = mutableListOf<List<Int>>()
    private val onProgress = RetryProgress { c, a, m, d -> progress += listOf(c, a, m, d) }
    private val notCancelled = BooleanSupplier { false }

    private fun raising(vararg failures: Throwable): LaunchAttempt {
        val queue = ArrayDeque(failures.toList())
        return LaunchAttempt { queue.removeFirstOrNull()?.let { throw it } ?: true }
    }

    private fun run(
        isGfe: Boolean,
        verb: String,
        attempt: LaunchAttempt,
        cancelled: BooleanSupplier = notCancelled,
        with: LaunchRetryRunner = runner,
    ) = LaunchRetries.run(with, isGfe, verb, attempt, extractor, cancelled, onProgress)

    @Test
    fun `a GFE host bypasses the runner and its errors propagate unchanged`() {
        val failure = HostAnswer(HostError(409, null))

        val thrown = assertThrows(HostAnswer::class.java) { run(true, "launch", raising(failure)) }

        assertThat(thrown).isSameInstanceAs(failure)
        assertThat(rules.decideCalls).isEmpty()
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `a GFE host reports success and refusal like upstream`() {
        assertThat(run(true, "launch", { true })).isEqualTo(LaunchOutcome.SUCCEEDED)
        assertThat(run(true, "resume", { false })).isEqualTo(LaunchOutcome.FAILED)
    }

    @Test
    fun `a non-GFE host retries a pending launch and reports progress with the category ordinal`() {
        val pending = HostAnswer(HostError(409, "Another client launch is already pending"))

        val outcome = run(false, "launch", raising(pending))

        assertThat(outcome).isEqualTo(LaunchOutcome.SUCCEEDED)
        assertThat(progress).containsExactly(listOf(LaunchCategory.PENDING.ordinal, 1, 4, 1000))
    }

    @Test
    fun `a resume with no running app falls back to launch`() {
        val noApp = HostAnswer(HostError(503, "No running app to resume"))

        assertThat(run(false, "resume", raising(noApp))).isEqualTo(LaunchOutcome.FALLBACK_TO_LAUNCH)
        assertThat(rules.decideCalls.single().verb).isEqualTo(LaunchVerb.RESUME)
    }

    @Test
    fun `the verb string decides between resume and launch rules`() {
        val noApp = HostAnswer(HostError(503, "No running app to resume"))

        // The same 503 on /launch is a capture or display failure, not a missing app.
        assertThat(run(false, "launch", raising(noApp))).isEqualTo(LaunchOutcome.SUCCEEDED)

        assertThat(rules.decideCalls.single().verb).isEqualTo(LaunchVerb.LAUNCH)
        assertThat(progress.single()[0]).isEqualTo(LaunchCategory.CAPTURE_OR_DISPLAY.ordinal)
    }

    @Test
    fun `the cancel supplier is read on every check`() {
        var cancelled = false
        val attempt = LaunchAttempt {
            cancelled = true
            throw HostAnswer(HostError(409, null))
        }

        assertThat(run(false, "launch", attempt, { cancelled })).isEqualTo(LaunchOutcome.CANCELLED)
    }

    @Test
    fun `the public entry point rethrows the original error when the native library is missing`() {
        val failure = HostAnswer(HostError(409, null))

        val thrown =
            assertThrows(HostAnswer::class.java) {
                LaunchRetries.run(
                    false,
                    "launch",
                    raising(failure),
                    extractor,
                    notCancelled,
                    onProgress,
                )
            }

        assertThat(thrown).isSameInstanceAs(failure)
        assertThat(log.records.count { it.level == LogLevel.ERROR && it.tag == LogTag.LAUNCH })
            .isAtLeast(1)
    }

    @Test
    fun `the public entry point bypasses the runner for GFE`() {
        val outcome =
            LaunchRetries.run(true, "launch", { true }, extractor, notCancelled, onProgress)

        assertThat(outcome).isEqualTo(LaunchOutcome.SUCCEEDED)
    }
}
