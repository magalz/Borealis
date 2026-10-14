package io.github.magalz.borealis.host.launch

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
 * What the upstream `NvConnection` calls around `/launch` and `/resume` (host contract 7.5). The
 * Java hook only supplies the request, how to read a host error from its exception, the cancel flag
 * and the progress callback; the rules live in [LaunchRetryRunner].
 */
object LaunchRetries {
    /**
     * Runs the request, retrying what LuminalShine asks to retry. GFE hosts never retry: their
     * errors are rethrown as upstream does. [verb] is the upstream verb, `"launch"` or `"resume"`.
     * [conflictAllowed] says whether an app that is already running may still be resolved with the
     * user ([LaunchOutcome.CONFLICT]); when it is false that answer is rethrown like any failure.
     * Blocks while waiting, so call it off the main thread.
     */
    @JvmStatic
    @Throws(IOException::class, XmlPullParserException::class)
    @Suppress("LongParameterList")
    fun run(
        isGfe: Boolean,
        verb: String,
        attempt: LaunchAttempt,
        errors: HostErrorExtractor,
        cancelled: BooleanSupplier,
        progress: RetryProgress,
        conflictAllowed: BooleanSupplier,
        host: String? = null,
    ): LaunchOutcome =
        run(
            LaunchRetryRunner(NativeHostRules),
            isGfe,
            verb,
            attempt,
            errors,
            cancelled,
            progress,
            conflictAllowed,
            host,
        )

    @Suppress("LongParameterList")
    internal fun run(
        runner: LaunchRetryRunner,
        isGfe: Boolean,
        verb: String,
        attempt: LaunchAttempt,
        errors: HostErrorExtractor,
        cancelled: BooleanSupplier,
        progress: RetryProgress,
        conflictAllowed: BooleanSupplier,
        host: String? = null,
    ): LaunchOutcome {
        if (isGfe) return if (attempt.run()) LaunchOutcome.SUCCEEDED else LaunchOutcome.FAILED
        return runner.run(
            if (verb == RESUME) LaunchVerb.RESUME else LaunchVerb.LAUNCH,
            isCancelled = cancelled::getAsBoolean,
            onRetry = { category, number, max, delayMs ->
                progress.onRetry(category.ordinal, number, max, delayMs)
            },
            errorOf = errors::errorOf,
            conflictAllowed = conflictAllowed::getAsBoolean,
            attempt = attempt::run,
        )
    }

    private const val RESUME = "resume"
}
