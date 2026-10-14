package io.github.magalz.borealis.host.launch

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

object LaunchRetries {
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
    ): LaunchOutcome = TODO("not implemented")

    @Suppress("LongParameterList")
    internal fun run(
        runner: LaunchRetryRunner,
        isGfe: Boolean,
        verb: String,
        attempt: LaunchAttempt,
        errors: HostErrorExtractor,
        cancelled: BooleanSupplier,
        progress: RetryProgress,
    ): LaunchOutcome = TODO("not implemented")
}
