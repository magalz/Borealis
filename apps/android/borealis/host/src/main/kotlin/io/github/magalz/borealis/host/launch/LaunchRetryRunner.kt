package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.nativecore.LaunchVerb

/** The XML `status_code` and message of a failed `/launch` or `/resume`. */
data class HostError(val code: Int, val message: String?)

/** How one launch or resume request ended. */
enum class LaunchOutcome {
    SUCCEEDED,
    FAILED,
    FALLBACK_TO_LAUNCH,
    CANCELLED,
}

class LaunchRetryRunner(
    private val rules: HostRules,
    private val budgetMs: Long = DEFAULT_BUDGET_MS,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    @Suppress("LongParameterList")
    fun run(
        verb: LaunchVerb,
        isCancelled: () -> Boolean,
        onRetry: (LaunchCategory, Int, Int, Int) -> Unit,
        errorOf: (Throwable) -> HostError?,
        attempt: () -> Boolean,
    ): LaunchOutcome = TODO("not implemented")

    companion object {
        const val DEFAULT_BUDGET_MS = 60_000L
        const val SLICE_MS = 250L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
