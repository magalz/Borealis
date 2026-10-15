package io.github.magalz.borealis.host.pairing

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.RecordingAudit
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test

class BestEffortUnpairAuditTest {
    @get:Rule val audit = RecordingAudit()

    private val client =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .readTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()

    private fun ReplayServer.url(): HttpUrl =
        "http://127.0.0.1:$port/unpair?uniqueid=0123456789ABCDEF".toHttpUrl()

    private fun xml(status: Int, httpLine: String = "HTTP/1.1 200 OK"): ByteArray {
        val body = "<root status_code=\"$status\"/>"
        return "$httpLine\r\nContent-Length: ${body.length}\r\n\r\n$body".toByteArray()
    }

    private fun unpair(reply: ReplayServer.Reply): Boolean =
        ReplayServer(reply).use { BestEffortUnpair.unpair(client, it.url()) }

    @Test
    fun `a host that confirms is recorded as confirmed`() {
        unpair(ReplayServer.Reply(xml(200)))

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.UNPAIR_CONFIRMED)
        assertThat(event.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(event.host).isEqualTo("127.0.0.1")
    }

    @Test
    fun `a host that answers 404 inside a 200 is not confirmed`() {
        unpair(ReplayServer.Reply(xml(404)))

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.UNPAIR_NOT_CONFIRMED)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.detail).isEqualTo("reason=host_status_404")
    }

    @Test
    fun `an http error and an unreadable body say so`() {
        unpair(ReplayServer.Reply(xml(200, httpLine = "HTTP/1.1 500 ERROR")))
        unpair(ReplayServer.Reply("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc".toByteArray()))

        assertThat(audit.events.map { it.detail })
            .containsExactly("reason=http_500", "reason=host_status_unreadable")
            .inOrder()
        assertThat(audit.types().toSet()).containsExactly(AuditType.UNPAIR_NOT_CONFIRMED)
    }

    @Test
    fun `a host that cannot be reached is not confirmed and the error is named`() {
        unpair(ReplayServer.Reply(ByteArray(0), reset = true))

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.UNPAIR_NOT_CONFIRMED)
        assertThat(event.detail).startsWith("reason=unreachable error=")
    }

    @Test
    fun `the unique id in the request url is not recorded`() {
        unpair(ReplayServer.Reply(xml(404)))

        for (event in audit.events) {
            assertThat(event.host).doesNotContain("0123456789ABCDEF")
            assertThat(event.detail).doesNotContain("0123456789ABCDEF")
        }
    }
}
