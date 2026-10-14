package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.LaunchAction
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.nativecore.LaunchDecision
import io.github.magalz.borealis.nativecore.LaunchVerb
import java.util.EnumMap

/** The XML `status_code` and message of a failed `/launch` or `/resume`. */
data class HostError(val code: Int, val message: String?)

/** How one launch or resume request ended. */
enum class LaunchOutcome {
    /** The host accepted the request. */
    SUCCEEDED,

    /** The host answered 200 but did not start or resume the app. */
    FAILED,

    /** `/resume` found no running app: the caller launches it instead. */
    FALLBACK_TO_LAUNCH,

    /** The user gave up; nothing is left to report. */
    CANCELLED,
}

/**
 * Makes one `/launch` or `/resume` request and repeats it while LuminalShine asks the client to
 * retry (409 and 503 answers, host contract 7.5). It never calls `/cancel`: that would also cancel
 * another client's pending launch, and the runner has no HTTP access at all.
 *
 * The rules of the core decide per failure ([rules]); the runner owns what the core leaves to the
 * caller: counting the retries per category, the total time [budgetMs] and the waiting. A wait is
 * cut into slices of at most [SLICE_MS] so a cancellation is seen quickly. [clock] (milliseconds)
 * and [sleeper] are the seams for tests.
 *
 * Everything the runner does not retry is rethrown as the original instance, so the callers keep
 * handling the upstream exceptions as before: any throwable `errorOf` does not recognise, every
 * FAIL decision, an exhausted category or budget, and the case that the rules are unavailable (the
 * native library did not load).
 */
class LaunchRetryRunner(
    private val rules: HostRules,
    private val budgetMs: Long = DEFAULT_BUDGET_MS,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    /**
     * Runs [attempt] until it returns, fails for good or is cancelled. [errorOf] turns a throwable
     * into the host's answer, or null when it is not one; [onRetry] is told the category, the
     * number of the retry that is about to start, the retries allowed and the wait in milliseconds.
     * It blocks while waiting, so the main thread must not call it.
     */
    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    fun run(
        verb: LaunchVerb,
        isCancelled: () -> Boolean,
        onRetry: (LaunchCategory, Int, Int, Int) -> Unit,
        errorOf: (Throwable) -> HostError?,
        attempt: () -> Boolean,
    ): LaunchOutcome {
        val startedAt = clock()
        val retries = EnumMap<LaunchCategory, Int>(LaunchCategory::class.java)
        while (true) {
            if (isCancelled()) return LaunchOutcome.CANCELLED
            val failure =
                try {
                    return if (attempt()) LaunchOutcome.SUCCEEDED else LaunchOutcome.FAILED
                } catch (e: Throwable) {
                    e
                }
            if (isCancelled()) return LaunchOutcome.CANCELLED
            val error = errorOf(failure) ?: throw failure
            val decision = decide(verb, error, retries) ?: throw unavailable(failure, error)
            when (decision.action) {
                LaunchAction.FALLBACK_TO_LAUNCH -> return LaunchOutcome.FALLBACK_TO_LAUNCH
                LaunchAction.FAIL -> throw failure
                LaunchAction.RETRY -> {
                    if (clock() - startedAt + decision.delayMs > budgetMs) throw failure
                    val number = (retries[decision.category] ?: 0) + 1
                    retries[decision.category] = number
                    announce(decision, number, onRetry)
                    if (!wait(decision.delayMs, isCancelled)) return LaunchOutcome.CANCELLED
                }
            }
        }
    }

    /** The category is only known after the first answer, so a second call carries the count. */
    private fun decide(
        verb: LaunchVerb,
        error: HostError,
        retries: Map<LaunchCategory, Int>,
    ): LaunchDecision? {
        val first = rules.decideLaunch(verb, error.code, error.message, 0)
        val used = first?.let { retries[it.category] } ?: 0
        return if (used == 0) first else rules.decideLaunch(verb, error.code, error.message, used)
    }

    private fun unavailable(failure: Throwable, error: HostError): Throwable {
        BorealisLog.e(
            LogTag.LAUNCH,
            "host rules unavailable, not retrying",
            null,
            "status" to error.code,
        )
        return failure
    }

    private fun announce(
        decision: LaunchDecision,
        number: Int,
        onRetry: (LaunchCategory, Int, Int, Int) -> Unit,
    ) {
        BorealisLog.i(
            LogTag.LAUNCH,
            "host asked to retry",
            "category" to decision.category,
            "attempt" to number,
            "max" to decision.maxAttempts,
            "delay_ms" to decision.delayMs,
        )
        onRetry(decision.category, number, decision.maxAttempts, decision.delayMs)
    }

    /** Waits [delayMs] in slices; false when cancelled or interrupted before the wait was over. */
    private fun wait(delayMs: Int, isCancelled: () -> Boolean): Boolean {
        var left = delayMs.toLong()
        try {
            while (left > 0) {
                val slice = minOf(SLICE_MS, left)
                sleeper(slice)
                left -= slice
                if (isCancelled()) return false
            }
        } catch (e: InterruptedException) {
            BorealisLog.d(LogTag.LAUNCH, "retry wait interrupted")
            Thread.currentThread().interrupt()
            return false
        }
        return true
    }

    companion object {
        /** Total time all retries of one request may take (host contract requirement 7). */
        const val DEFAULT_BUDGET_MS = 60_000L
        const val SLICE_MS = 250L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
