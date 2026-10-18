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

    /** `/launch` found another app running: the caller resolves it once, with the user. */
    CONFLICT,
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
    @Suppress("LongParameterList") // The six seams of one run: verb, four callbacks, attempt.
    fun run(
        verb: LaunchVerb,
        isCancelled: () -> Boolean,
        onRetry: (LaunchCategory, Int, Int, Int) -> Unit,
        errorOf: (Throwable) -> HostError?,
        conflictAllowed: () -> Boolean,
        attempt: () -> Boolean,
    ): LaunchOutcome {
        val request = Request(verb, isCancelled, onRetry, errorOf, conflictAllowed)
        while (true) {
            request.next(attempt)?.let {
                return it
            }
        }
    }

    /** The state of one [run]: when it started and how many retries each category has used. */
    private inner class Request(
        private val verb: LaunchVerb,
        private val isCancelled: () -> Boolean,
        private val onRetry: (LaunchCategory, Int, Int, Int) -> Unit,
        private val errorOf: (Throwable) -> HostError?,
        private val conflictAllowed: () -> Boolean,
    ) {
        private val startedAt = clock()
        private val retries = EnumMap<LaunchCategory, Int>(LaunchCategory::class.java)

        /** Makes one attempt; null means that a retry wait went by and the next attempt is due. */
        @Suppress("TooGenericExceptionCaught") // The rule table has to see every throwable.
        fun next(attempt: () -> Boolean): LaunchOutcome? {
            if (isCancelled()) return LaunchOutcome.CANCELLED
            val failure =
                try {
                    return if (attempt()) LaunchOutcome.SUCCEEDED else LaunchOutcome.FAILED
                } catch (e: Throwable) {
                    e
                }
            return if (isCancelled()) LaunchOutcome.CANCELLED else recover(failure)
        }

        private fun recover(failure: Throwable): LaunchOutcome? {
            val decision = decision(failure)
            return when (decision.action) {
                LaunchAction.FALLBACK_TO_LAUNCH -> LaunchOutcome.FALLBACK_TO_LAUNCH
                LaunchAction.FAIL -> throw failure
                LaunchAction.RESOLVE_CONFLICT ->
                    if (conflictAllowed()) LaunchOutcome.CONFLICT else throw failure
                LaunchAction.RETRY -> retry(decision, failure)
            }
        }

        /** What the rules say about [failure]; rethrows it when they have nothing to say. */
        private fun decision(failure: Throwable): LaunchDecision {
            val error = errorOf(failure) ?: throw failure
            // The category is only known after the first answer, so a second call carries the
            // count.
            val first = rules.decideLaunch(verb, error.code, error.message, 0)
            val used = first?.let { retries[it.category] } ?: 0
            val decision =
                if (used == 0) first else rules.decideLaunch(verb, error.code, error.message, used)
            if (decision == null) {
                BorealisLog.e(
                    LogTag.LAUNCH,
                    "host rules unavailable, not retrying",
                    null,
                    "status" to error.code,
                )
                throw failure
            }
            return decision
        }

        private fun retry(decision: LaunchDecision, failure: Throwable): LaunchOutcome? {
            if (clock() - startedAt + decision.delayMs > budgetMs) throw failure
            val number = (retries[decision.category] ?: 0) + 1
            retries[decision.category] = number
            BorealisLog.i(
                LogTag.LAUNCH,
                "host asked to retry",
                "category" to decision.category,
                "attempt" to number,
                "max" to decision.maxAttempts,
                "delay_ms" to decision.delayMs,
            )
            onRetry(decision.category, number, decision.maxAttempts, decision.delayMs)
            return if (wait(decision.delayMs)) null else LaunchOutcome.CANCELLED
        }

        /** Waits in slices; false when cancelled or interrupted before the wait was over. */
        private fun wait(delayMs: Int): Boolean {
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
    }

    companion object {
        /** Total time all retries of one request may take (host contract requirement 7). */
        const val DEFAULT_BUDGET_MS = 60_000L
        const val SLICE_MS = 250L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
