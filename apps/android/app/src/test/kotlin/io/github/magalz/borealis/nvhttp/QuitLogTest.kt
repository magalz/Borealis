package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvHTTP
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.logging.LogLevel
import java.io.Closeable
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the time of a Quit goes, in the log: the cancel that the client sends and the check that
 * follows it. Both can take seconds on a host that closes the stream first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class QuitLogTest {
    @get:Rule val log = RecordingLog()

    private val closeables = mutableListOf<Closeable>()

    @After fun tearDown() = closeables.reversed().forEach { it.close() }

    private fun host(): NvFakeHost = NvFakeHost().also { closeables += it }

    private fun client(host: NvFakeHost): NvHTTP =
        clientOf(host, serverCert = TestIdentities.hostRsa.certificate)

    private fun lines(): List<String> = log.records.map { it.message }

    private fun field(message: String, name: String): String? =
        log.records.single { it.message == message }.fields.toMap()[name]

    @Test
    fun `a quit logs the cancel it sends, its answer and the check, in order, at info`() {
        val host = host()
        host.answer("/cancel", HostReply.root("<cancel>1</cancel>"), "https")
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "https")

        client(host).quitApp()

        assertThat(lines())
            .containsExactly("quit cancel sent", "quit cancel answered", "quit check answered")
            .inOrder()
        assertThat(log.records.map { it.level }.toSet()).containsExactly(LogLevel.INFO)
    }

    @Test
    fun `the answers say how many milliseconds they took`() {
        val host = host()
        host.answer("/cancel", HostReply.root("<cancel>1</cancel>"), "https")
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "https")

        client(host).quitApp()

        for (answered in listOf("quit cancel answered", "quit check answered")) {
            assertThat(field(answered, "ms")?.toLongOrNull()).isAtLeast(0L)
        }
        assertThat(log.records.single { it.message == "quit cancel sent" }.fields).isEmpty()
    }

    @Test
    fun `a cancel that the host refuses is answered and no check follows`() {
        val host = host()
        host.answer("/cancel", HostReply.root("<cancel>0</cancel>"), "https")

        client(host).quitApp()

        assertThat(lines()).containsExactly("quit cancel sent", "quit cancel answered").inOrder()
    }

    @Test
    fun `a cancel that leaves an app of another client running still logs its check`() {
        val host = host()
        host.answer("/cancel", HostReply.root("<cancel>1</cancel>"), "https")
        host.answer(
            "/serverinfo",
            HostReply.xml(serverInfoBody("SUNSHINE_SERVER_BUSY", currentGame = 9)),
            "https",
        )

        assertThrows(HostHttpResponseException::class.java) { client(host).quitApp() }

        assertThat(lines())
            .containsExactly("quit cancel sent", "quit cancel answered", "quit check answered")
            .inOrder()
    }

    @Test
    fun `the lines say nothing about the host`() {
        val host = host()
        host.answer("/cancel", HostReply.root("<cancel>1</cancel>"), "https")
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "https")

        client(host).quitApp()

        val text = log.records.joinToString("\n") { "${it.message} ${it.fields}" }
        assertThat(text).doesNotContain("127.0.0.1")
        assertThat(text).doesNotContain(host.httpsPort.toString())
        assertThat(text).doesNotContain(UNIQUE_ID)
    }
}
