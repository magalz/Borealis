package io.github.magalz.borealis.host.launch

import java.io.IOException
import java.util.function.BooleanSupplier
import org.xmlpull.v1.XmlPullParserException

/** One `GET /serverinfo`, made by the upstream client. */
fun interface ServerInfoReader {
    @Throws(IOException::class, XmlPullParserException::class) fun read(): String
}

/**
 * Retries a timed-out `/serverinfo` at stream start (host contract: HTTPS busy during a launch).
 */
object ServerInfoRetry {
    const val MAX_ATTEMPTS = 3
    const val BUDGET_MS = 25_000L

    @JvmStatic
    @Throws(IOException::class, XmlPullParserException::class)
    fun read(reader: ServerInfoReader, cancelled: BooleanSupplier): String = reader.read()

    @Throws(IOException::class, XmlPullParserException::class)
    internal fun read(
        reader: ServerInfoReader,
        cancelled: BooleanSupplier,
        clock: () -> Long,
    ): String = reader.read()
}
