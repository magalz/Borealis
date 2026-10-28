package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.nativecore.LaunchVerb
import io.github.magalz.borealis.nativecore.NativeHostRules
import java.io.IOException
import java.util.function.BooleanSupplier
import org.xmlpull.v1.XmlPullParserException

/**
 * One `/launch` or `/resume` request, made by the upstream client; false when the host declined.
 */
fun interface LaunchAttempt {
    @Throws(IOException::class, XmlPullParserException::class) fun run(): Boolean
}

/** Recognises the upstream `HostHttpResponseException`; null for every other throwable. */
fun interface HostErrorExtractor {
    fun errorOf(throwable: Throwable): HostError?
}

/** Retry progress for the screen: [category] is the ordinal of `LaunchCategory`. */
fun interface RetryProgress {
    fun onRetry(category: Int, attempt: Int, maxAttempts: Int, delayMs: Int)
}

/**
 * Which request is made, and of which host: [verb] is the upstream verb, `"launch"` or `"resume"`.
 * GFE hosts ([isGfe]) never retry; [host] only labels the audit.
 */
data class LaunchRequest(val isGfe: Boolean, val verb: String, val host: String? = null)

/**
 * What the caller supplies around one request: how to read a host error from its exception
 * ([errors]), the cancel flag ([cancelled]), the retry progress for the screen ([progress]) and
 * whether an app that is already running may still be resolved with the user ([conflictAllowed]).
 */
class LaunchHooks(
    val errors: HostErrorExtractor,
    val cancelled: BooleanSupplier,
    val progress: RetryProgress,
    val conflictAllowed: BooleanSupplier,
)

/**
 * What the upstream `NvConnection` calls around `/launch` and `/resume` (host contract 7.5). The
 * `NvConnection` hook only supplies the [LaunchRequest], the attempt and its [LaunchHooks]; the
 * rules live in [LaunchRetryRunner].
 */
object LaunchRetries {
    /**
     * Runs [attempt], retrying what LuminalShine asks to retry. GFE hosts never retry: their errors
     * are rethrown as upstream does. When [LaunchHooks.conflictAllowed] is false, an app that is
     * already running ([LaunchOutcome.CONFLICT]) is rethrown like any failure instead of being
     * resolved with the user. Blocks while waiting, so call it off the main thread.
     */
    @JvmStatic
    @Throws(IOException::class, XmlPullParserException::class)
    fun run(request: LaunchRequest, attempt: LaunchAttempt, hooks: LaunchHooks): LaunchOutcome =
        run(LaunchRetryRunner(NativeHostRules), request, attempt, hooks)

    internal fun run(
        runner: LaunchRetryRunner,
        request: LaunchRequest,
        attempt: LaunchAttempt,
        hooks: LaunchHooks,
    ): LaunchOutcome {
        val audited = if (request.verb == RESUME) RESUME else LAUNCH
        val outcome =
            try {
                execute(runner, request, attempt, hooks) { category, number, max, delayMs ->
                    LaunchAudit.retry(request.host, audited, category, number, max, delayMs)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                // The audit records any failure, then it is rethrown as it came.
                LaunchAudit.failed(request.host, audited, hooks.errors.errorOf(e), e)
                throw e
            }
        LaunchAudit.attempt(request.host, audited, outcome)
        return outcome
    }

    private fun execute(
        runner: LaunchRetryRunner,
        request: LaunchRequest,
        attempt: LaunchAttempt,
        hooks: LaunchHooks,
        onRetried: (LaunchCategory, Int, Int, Int) -> Unit,
    ): LaunchOutcome {
        if (request.isGfe) {
            return if (attempt.run()) LaunchOutcome.SUCCEEDED else LaunchOutcome.FAILED
        }
        return runner.run(
            if (request.verb == RESUME) LaunchVerb.RESUME else LaunchVerb.LAUNCH,
            isCancelled = hooks.cancelled::getAsBoolean,
            onRetry = { category, number, max, delayMs ->
                onRetried(category, number, max, delayMs)
                hooks.progress.onRetry(category.ordinal, number, max, delayMs)
            },
            errorOf = hooks.errors::errorOf,
            conflictAllowed = hooks.conflictAllowed::getAsBoolean,
            attempt = attempt::run,
        )
    }

    private const val RESUME = "resume"
    private const val LAUNCH = "launch"
}
