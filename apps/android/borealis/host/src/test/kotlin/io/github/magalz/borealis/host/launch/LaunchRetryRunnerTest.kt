package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.LaunchAction
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.nativecore.LaunchDecision
import io.github.magalz.borealis.nativecore.LaunchVerb
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

private data class Retry(
    val category: LaunchCategory,
    val attempt: Int,
    val max: Int,
    val delayMs: Int,
)

class LaunchRetryRunnerTest {
    @get:Rule val log = RecordingLogSink()

    private val rules = FakeHostRules()

    /** Fake time: sleeping advances the clock, so the budget and the slices can be observed. */
    private var now = 0L
    private val slices = mutableListOf<Long>()
    private var cancelled = false
    private var cancelAtSlice = Int.MAX_VALUE
    private val retries = mutableListOf<Retry>()

    private fun runner(
        hostRules: HostRules = rules,
        budgetMs: Long = LaunchRetryRunner.DEFAULT_BUDGET_MS,
    ) =
        LaunchRetryRunner(
            hostRules,
            budgetMs,
            clock = { now },
            sleeper = {
                slices += it
                now += it
                if (slices.size >= cancelAtSlice) cancelled = true
            },
        )

    private fun run(
        verb: LaunchVerb = LaunchVerb.LAUNCH,
        runner: LaunchRetryRunner = runner(),
        conflictAllowed: () -> Boolean = { false },
        attempt: () -> Boolean,
    ): LaunchOutcome =
        runner.run(
            verb,
            isCancelled = { cancelled },
            onRetry = { category, number, max, delay ->
                retries += Retry(category, number, max, delay)
            },
            errorOf = { (it as? HostAnswer)?.error },
            conflictAllowed = conflictAllowed,
            attempt = attempt,
        )

    private fun answer(code: Int, message: String? = null) = HostAnswer(HostError(code, message))

    /** An attempt that raises [failures] in order, then answers [last]. */
    private fun scripted(vararg failures: Throwable, last: Boolean = true): () -> Boolean {
        val queue = ArrayDeque(failures.toList())
        return { queue.removeFirstOrNull()?.let { throw it } ?: last }
    }

    private fun retryAfter(delayMs: Int) =
        FakeHostRules(
            decide = { _, _, _, _ ->
                LaunchDecision(LaunchAction.RETRY, delayMs, LaunchCategory.PENDING, 4)
            }
        )

    @Test
    fun `a request that goes through is not retried`() {
        assertThat(run { true }).isEqualTo(LaunchOutcome.SUCCEEDED)
        assertThat(slices).isEmpty()
        assertThat(rules.decideCalls).isEmpty()
    }

    @Test
    fun `a host that declines the app is a failure without retries`() {
        assertThat(run { false }).isEqualTo(LaunchOutcome.FAILED)
        assertThat(rules.decideCalls).isEmpty()
    }

    @Test
    fun `a pending launch is retried after the delays of the core and then succeeds`() {
        val outcome = run(attempt = scripted(answer(409), answer(409)))

        assertThat(outcome).isEqualTo(LaunchOutcome.SUCCEEDED)
        assertThat(slices.sum()).isEqualTo(3000)
        assertThat(retries)
            .containsExactly(
                Retry(LaunchCategory.PENDING, 1, 4, 1000),
                Retry(LaunchCategory.PENDING, 2, 4, 2000),
            )
            .inOrder()
    }

    @Test
    fun `the wait is cut into slices of at most 250 ms`() {
        run(attempt = scripted(answer(409)))

        assertThat(slices).containsExactly(250L, 250L, 250L, 250L).inOrder()
    }

    @Test
    fun `the last slice of a wait is the remainder`() {
        run(runner = runner(retryAfter(600)), attempt = scripted(answer(409)))

        assertThat(slices).containsExactly(250L, 250L, 100L).inOrder()
    }

    @Test
    fun `the last error is rethrown unchanged when the retries of the category run out`() {
        val errors = List(5) { answer(409, "pending $it") }

        val thrown =
            assertThrows(HostAnswer::class.java) { run(attempt = scripted(*errors.toTypedArray())) }

        assertThat(thrown).isSameInstanceAs(errors[4])
        assertThat(retries).hasSize(4)
    }

    @Test
    fun `every other throwable is rethrown unchanged and never reaches the rules`() {
        val failure = IOException("connection reset")

        val thrown = assertThrows(IOException::class.java) { run(attempt = scripted(failure)) }

        assertThat(thrown).isSameInstanceAs(failure)
        assertThat(rules.decideCalls).isEmpty()
        assertThat(slices).isEmpty()
    }

    @Test
    fun `a runtime exception from the request is rethrown unchanged as well`() {
        val failure = IllegalStateException("bug")

        val thrown =
            assertThrows(IllegalStateException::class.java) { run(attempt = scripted(failure)) }

        assertThat(thrown).isSameInstanceAs(failure)
    }

    @Test
    fun `a permanent error is rethrown unchanged without waiting`() {
        val failure = answer(400, "Missing a required launch parameter")

        val thrown = assertThrows(HostAnswer::class.java) { run(attempt = scripted(failure)) }

        assertThat(thrown).isSameInstanceAs(failure)
        assertThat(slices).isEmpty()
        assertThat(retries).isEmpty()
    }

    @Test
    fun `the rules see the verb, the status and the host message`() {
        run(LaunchVerb.RESUME, attempt = scripted(answer(503, "GPU/display stack is not ready")))

        assertThat(rules.decideCalls.first())
            .isEqualTo(
                DecideCall(LaunchVerb.RESUME, 503, "GPU/display stack is not ready", attempts = 0)
            )
    }

    @Test
    fun `retries are counted per category`() {
        val outcome =
            run(
                attempt =
                    scripted(
                        answer(409),
                        answer(503, "The previous stream is still restoring the display"),
                        answer(409),
                    )
            )

        assertThat(outcome).isEqualTo(LaunchOutcome.SUCCEEDED)
        assertThat(retries.map { it.category to it.attempt })
            .containsExactly(
                LaunchCategory.PENDING to 1,
                LaunchCategory.RESTORING_DISPLAY to 1,
                LaunchCategory.PENDING to 2,
            )
            .inOrder()
        // The second pending answer is the second retry of that category: 2000 ms, not 1000 ms.
        assertThat(retries.last().delayMs).isEqualTo(2000)
        assertThat(rules.decideCalls.last().attempts).isEqualTo(1)
    }

    @Test
    fun `no retry starts once the next wait would exceed the total budget`() {
        val errors = List(4) { answer(409, "pending $it") }

        val thrown =
            assertThrows(HostAnswer::class.java) {
                run(
                    runner = runner(budgetMs = 4000),
                    attempt = scripted(*errors.toTypedArray()),
                )
            }

        // 1000 + 2000 fit in 4000 ms; the third wait (3000 ms) would end at 6000 ms.
        assertThat(slices.sum()).isEqualTo(3000)
        assertThat(retries).hasSize(2)
        assertThat(thrown).isSameInstanceAs(errors[2])
    }

    @Test
    fun `the default budget is 60 seconds`() {
        assertThat(LaunchRetryRunner.DEFAULT_BUDGET_MS).isEqualTo(60_000L)
    }

    @Test
    fun `resume without a running app asks the caller to launch instead`() {
        val outcome =
            run(LaunchVerb.RESUME, attempt = scripted(answer(503, "No running app to resume")))

        assertThat(outcome).isEqualTo(LaunchOutcome.FALLBACK_TO_LAUNCH)
        assertThat(slices).isEmpty()
    }

    private val appRunning = "An app is already running on this host"

    @Test
    fun `a running app on launch is a conflict when the caller allows it`() {
        val outcome = run(conflictAllowed = { true }, attempt = scripted(answer(400, appRunning)))

        assertThat(outcome).isEqualTo(LaunchOutcome.CONFLICT)
        assertThat(slices).isEmpty()
        assertThat(retries).isEmpty()
    }

    @Test
    fun `a running app on launch is rethrown unchanged when the caller does not allow it`() {
        val failure = answer(400, appRunning)

        val thrown = assertThrows(HostAnswer::class.java) { run(attempt = scripted(failure)) }

        assertThat(thrown).isSameInstanceAs(failure)
    }

    @Test
    fun `the caller is asked again for every conflict, so a second one can be refused`() {
        var allowed = true
        val failure = answer(400, appRunning)

        val first = run(conflictAllowed = { allowed }, attempt = scripted(failure))
        allowed = false
        val thrown =
            assertThrows(HostAnswer::class.java) {
                run(conflictAllowed = { allowed }, attempt = scripted(failure))
            }

        assertThat(first).isEqualTo(LaunchOutcome.CONFLICT)
        assertThat(thrown).isSameInstanceAs(failure)
    }

    @Test
    fun `the same text on resume is not a conflict`() {
        val failure = answer(400, appRunning)

        val thrown =
            assertThrows(HostAnswer::class.java) {
                run(LaunchVerb.RESUME, conflictAllowed = { true }, attempt = scripted(failure))
            }

        assertThat(thrown).isSameInstanceAs(failure)
    }

    @Test
    fun `without the native rules a running app is rethrown and not resolved`() {
        val failure = answer(400, appRunning)
        val unavailable = FakeHostRules(decide = { _, _, _, _ -> null })

        val thrown =
            assertThrows(HostAnswer::class.java) {
                run(
                    runner = runner(unavailable),
                    conflictAllowed = { true },
                    attempt = scripted(failure),
                )
            }

        assertThat(thrown).isSameInstanceAs(failure)
    }

    @Test
    fun `a request cancelled before it starts is never made`() {
        cancelled = true
        var made = false

        val outcome = run {
            made = true
            true
        }

        assertThat(outcome).isEqualTo(LaunchOutcome.CANCELLED)
        assertThat(made).isFalse()
    }

    @Test
    fun `cancelling during a wait ends it within one slice and returns without throwing`() {
        cancelAtSlice = 2
        var attempts = 0

        val outcome = run {
            attempts++
            throw answer(409)
        }

        assertThat(outcome).isEqualTo(LaunchOutcome.CANCELLED)
        assertThat(slices).hasSize(2)
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `cancelling while a request is in flight wins over its host error`() {
        val outcome = run {
            cancelled = true
            throw answer(409)
        }

        assertThat(outcome).isEqualTo(LaunchOutcome.CANCELLED)
        assertThat(slices).isEmpty()
    }

    @Test
    fun `an interrupted wait is a cancellation that keeps the interrupt flag`() {
        val interrupting =
            LaunchRetryRunner(rules, clock = { now }, sleeper = { throw InterruptedException() })

        try {
            val outcome = run(runner = interrupting, attempt = scripted(answer(409)))

            assertThat(outcome).isEqualTo(LaunchOutcome.CANCELLED)
            assertThat(Thread.currentThread().isInterrupted).isTrue()
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `without the native rules the original error is rethrown and logged once`() {
        val failure = answer(409)
        val unavailable = FakeHostRules(decide = { _, _, _, _ -> null })

        val thrown =
            assertThrows(HostAnswer::class.java) {
                run(runner = runner(unavailable), attempt = scripted(failure))
            }

        assertThat(thrown).isSameInstanceAs(failure)
        val errors = log.records.filter { it.level == LogLevel.ERROR && it.tag == LogTag.LAUNCH }
        assertThat(errors).hasSize(1)
        assertThat(errors.single().fields).containsExactly("status" to "409")
    }

    @Test
    fun `retries are logged with the category and the attempt but not the host message`() {
        val message = "Another client launch is already pending"

        run(attempt = scripted(answer(409, message)))

        val record = log.records.single { it.tag == LogTag.LAUNCH && it.level == LogLevel.INFO }
        assertThat(record.fields)
            .containsAtLeast(
                "category" to "PENDING",
                "attempt" to "1",
                "max" to "4",
                "delay_ms" to "1000",
            )
        assertThat(record.message).doesNotContain(message)
        assertThat(record.fields.map { it.second }).doesNotContain(message)
    }

    @Test
    fun `the default clock and sleeper wait for real`() {
        val real = LaunchRetryRunner(retryAfter(30))
        val start = System.nanoTime()

        val outcome = run(runner = real, attempt = scripted(answer(409)))

        assertThat(outcome).isEqualTo(LaunchOutcome.SUCCEEDED)
        assertThat((System.nanoTime() - start) / 1_000_000).isAtLeast(25)
    }
}
