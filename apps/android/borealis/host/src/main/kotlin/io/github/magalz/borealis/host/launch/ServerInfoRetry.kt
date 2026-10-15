package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.function.BooleanSupplier
import org.xmlpull.v1.XmlPullParserException

/** One `GET /serverinfo`, made by the upstream client. */
fun interface ServerInfoReader {
    @Throws(IOException::class, XmlPullParserException::class) fun read(): String
}

/**
 * Reads `/serverinfo` at stream start and retries a read timeout. While LuminalShine builds a
 * virtual display for another client's `/launch` it answers no HTTPS request for about 14 s
 * (luminalshine#202), so the second client's first read times out; waiting and asking again lets it
 * join, or reach the conflict dialog, instead of failing with "error 0". Only `/serverinfo` is
 * retried here: `/launch`, `/resume`, `/cancel`, `/quit` and pairing are not idempotent.
 */
object ServerInfoRetry {
    const val MAX_ATTEMPTS = 3

    /** No new attempt starts once this much time has passed since the first one began. */
    const val BUDGET_MS = 25_000L

    private const val NANOS_PER_MILLI = 1_000_000L

    /**
     * Returns the answer of [reader]. A [SocketTimeoutException] is retried at once (the timeout
     * itself was the wait) up to [MAX_ATTEMPTS] times, within [BUDGET_MS], and while [cancelled] is
     * false; then the last timeout is rethrown. Any other exception is rethrown at once. Blocks, so
     * call it off the main thread.
     */
    @JvmStatic
    @Throws(IOException::class, XmlPullParserException::class)
    fun read(reader: ServerInfoReader, cancelled: BooleanSupplier): String =
        read(reader, cancelled) { System.nanoTime() / NANOS_PER_MILLI }

    @Throws(IOException::class, XmlPullParserException::class)
    internal fun read(
        reader: ServerInfoReader,
        cancelled: BooleanSupplier,
        clock: () -> Long,
    ): String {
        val startedAt = clock()
        var attempt = 1
        while (true) {
            try {
                return reader.read()
            } catch (e: SocketTimeoutException) {
                if (
                    attempt >= MAX_ATTEMPTS ||
                        cancelled.asBoolean ||
                        clock() - startedAt > BUDGET_MS
                ) {
                    throw e
                }
                attempt++
                BorealisLog.i(
                    LogTag.LAUNCH,
                    "server info timed out, retrying",
                    "attempt" to attempt,
                    "max" to MAX_ATTEMPTS,
                )
            }
        }
    }
}
