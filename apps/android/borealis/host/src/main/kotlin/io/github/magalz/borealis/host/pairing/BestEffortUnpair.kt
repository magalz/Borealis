package io.github.magalz.borealis.host.pairing

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The `/unpair` request of the upstream client, made safe for LuminalShine (host contract section 4
 * and requirement 4).
 *
 * LuminalShine does not route `/unpair`. Over HTTP it answers `200 OK` with a body whose root
 * `status_code` is 404 and then writes more bytes on the same connection, so a client that reuses
 * the connection reads them as the start of its next response. The request therefore asks for
 * `Connection: close`, the body is read for the XML `status_code`, and every failure is a plain
 * `false`.
 */
object BestEffortUnpair {
    private const val STATUS_OK = 200
    private const val MAX_BODY_BYTES = 16L * 1024

    /** The attributes of the root start tag, after an optional XML declaration. */
    private val ROOT_TAG = Regex("""\A\s*(?:<\?xml[^>]*\?>\s*)?<root\b([^>]*)>""")
    private val STATUS_CODE = Regex("""\sstatus_code\s*=\s*(["'])(\d{1,5})\1""")

    /** True only when the host answered 2xx and the XML root reports `status_code="200"`. */
    @JvmStatic
    fun unpair(client: OkHttpClient, url: HttpUrl): Boolean {
        val outcome =
            try {
                val request = Request.Builder().url(url).header("Connection", "close").get().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        statusCode(response.peekBody(MAX_BODY_BYTES).string())
                    } else {
                        Outcome.HttpFailure(response.code)
                    }
                }
            } catch (e: IOException) {
                Outcome.Unreachable(e.javaClass.simpleName)
            }
        when (outcome) {
            Outcome.Accepted -> BorealisLog.i(LogTag.PAIR, "unpair accepted by the host")
            is Outcome.HttpFailure ->
                BorealisLog.w(LogTag.PAIR, "unpair refused", "http" to outcome.code)
            is Outcome.Rejected ->
                BorealisLog.w(LogTag.PAIR, "unpair not confirmed", "status" to outcome.status)
            is Outcome.Unreachable ->
                BorealisLog.w(LogTag.PAIR, "unpair failed", "error" to outcome.error)
        }
        return outcome == Outcome.Accepted
    }

    private fun statusCode(body: String): Outcome {
        val attributes = ROOT_TAG.find(body)?.groupValues?.get(1)
        val status = attributes?.let { STATUS_CODE.find(it) }?.groupValues?.get(2)?.toInt()
        return if (status == STATUS_OK) Outcome.Accepted else Outcome.Rejected(status)
    }

    private sealed interface Outcome {
        data object Accepted : Outcome

        data class HttpFailure(val code: Int) : Outcome

        data class Rejected(val status: Int?) : Outcome

        data class Unreachable(val error: String) : Outcome
    }
}
