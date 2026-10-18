package io.github.magalz.borealis.host.pairing

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import io.github.magalz.borealis.host.launch.FakeHostRules
import io.github.magalz.borealis.host.launch.HostError
import io.github.magalz.borealis.host.launch.RecordingLogSink
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import org.junit.Rule
import org.junit.Test

class PairingErrorsTest {
    @get:Rule val log = RecordingLogSink()

    private val rules = FakeHostRules()

    /** Renders a string as its resource id, so a test sees which text was chosen. */
    private val fake: (Int) -> String = { "string:$it" }

    private fun describe(
        code: Int,
        message: String?,
        restarted: Boolean = false,
        fallback: String? = "upstream text",
    ) =
        PairingErrors.describe(
            PairingEnvironment(rules, fake),
            HostError(code, message),
            fallback,
            restarted,
        )

    private fun text(id: Int) = "string:$id"

    @Test
    fun `a session the host dropped is explained and pairing restarts`() {
        for ((code, message) in
            listOf(
                400 to "Out of order call to getservercert",
                400 to "Invalid uniqueid",
                400 to "Pairing exchange invalidated",
                410 to "Host pairing state was reset",
            )) {
            val failure = describe(code, message)

            assertThat(failure).isEqualTo(PairingFailure(text(R.string.pair_restart_cleared), true))
        }
    }

    @Test
    fun `a second dropped session in the restarted attempt is a plain failure`() {
        for ((code, message) in
            listOf(
                400 to "Out of order call to getservercert",
                400 to "Invalid uniqueid",
                400 to "Pairing exchange invalidated",
                410 to "Host pairing state was reset",
            )) {
            val failure = describe(code, message, restarted = true)

            assertThat(failure)
                .isEqualTo(PairingFailure(text(R.string.pair_restart_failed_again), false))
        }
    }

    @Test
    fun `a session another pairing holds on the host gets its own message and no restart`() {
        assertThat(describe(409, "Pairing exchange already in progress"))
            .isEqualTo(PairingFailure(text(R.string.pair_host_session_in_progress), false))
        assertThat(describe(409, "Pairing request identity mismatch"))
            .isEqualTo(PairingFailure(text(R.string.pair_host_other_device), false))
        assertThat(describe(503, "Too many pending pairing requests"))
            .isEqualTo(PairingFailure(text(R.string.pair_host_too_many_pending), false))
    }

    @Test
    fun `the specific failures get their own message and no restart`() {
        assertThat(describe(403, "Pairing request peer mismatch"))
            .isEqualTo(PairingFailure(text(R.string.pair_host_peer_mismatch), false))
        assertThat(describe(409, "Pairing request superseded by retry"))
            .isEqualTo(PairingFailure(text(R.string.pair_host_superseded), false))
    }

    @Test
    fun `the restart flag does not change a message that never restarts`() {
        assertThat(describe(409, "Pairing exchange already in progress", restarted = true))
            .isEqualTo(describe(409, "Pairing exchange already in progress"))
    }

    @Test
    fun `an error the rules do not know keeps the upstream text`() {
        assertThat(describe(400, "Salt too short"))
            .isEqualTo(PairingFailure("upstream text", false))
        assertThat(describe(500, "Out of order call to getservercert"))
            .isEqualTo(PairingFailure("upstream text", false))
    }

    @Test
    fun `an absent upstream text stays absent`() {
        assertThat(describe(400, "Salt too short", fallback = null))
            .isEqualTo(PairingFailure(null, false))
    }

    @Test
    fun `an exception that is not a host answer keeps the upstream text without asking the rules`() {
        val failure =
            PairingErrors.describe(PairingEnvironment(rules, fake), null, "upstream text", false)

        assertThat(failure).isEqualTo(PairingFailure("upstream text", false))
        assertThat(rules.pairCalls).isEmpty()
    }

    @Test
    fun `an unavailable core keeps the upstream text`() {
        val unavailable = FakeHostRules(pairing = { _, _ -> null })

        val failure =
            PairingErrors.describe(
                PairingEnvironment(unavailable, fake),
                HostError(400, "Invalid uniqueid"),
                "upstream text",
                false,
            )

        assertThat(failure).isEqualTo(PairingFailure("upstream text", false))
    }

    @Test
    fun `the rules see the status and the host message`() {
        describe(409, "Pairing exchange already in progress")

        assertThat(rules.pairCalls).containsExactly(409 to "Pairing exchange already in progress")
    }

    @Test
    fun `a classified error is logged with its status and category and never its text`() {
        describe(400, "Out of order call to getservercert")

        val record = log.records.single()
        assertThat(record.level).isEqualTo(LogLevel.INFO)
        assertThat(record.tag).isEqualTo(LogTag.PAIR)
        assertThat(record.fields)
            .containsAtLeast(
                "status" to "400",
                "category" to "STALE_SESSION",
                "action" to "RESTART_ONCE",
            )
        assertThat(record.message).doesNotContain("getservercert")
        assertThat(record.fields.map { it.second })
            .doesNotContain("Out of order call to getservercert")
    }
}
