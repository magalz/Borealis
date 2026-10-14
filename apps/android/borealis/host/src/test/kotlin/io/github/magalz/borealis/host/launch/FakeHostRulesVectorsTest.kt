package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.host.Fixtures
import io.github.magalz.borealis.nativecore.LaunchAction
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.nativecore.LaunchDecision
import io.github.magalz.borealis.nativecore.LaunchVerb
import org.junit.Test

/**
 * Replays the core's golden vectors against [FakeHostRules.coreTable], so the table that the runner
 * tests rely on cannot drift from `core/src/launch.cpp`.
 */
class FakeHostRulesVectorsTest {
    private fun rows(): List<List<String>> =
        Fixtures.text("vectors/launch_decide.tsv")
            .lines()
            .map { it.trimEnd('\r') }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .drop(1)
            .map { it.split('\t') }

    private fun message(field: String): String? =
        when (field) {
            "<null>" -> null
            "<empty>" -> ""
            else -> field
        }

    @Test
    fun `the fake answers every golden vector of the core`() {
        val rows = rows()
        assertThat(rows).isNotEmpty()

        for (row in rows) {
            val (verb, status, message, attempts, action, delay, category, max) = row
            val expected =
                LaunchDecision(
                    LaunchAction.valueOf(action),
                    delay.toInt(),
                    LaunchCategory.valueOf(category),
                    max.toInt(),
                )

            val actual =
                FakeHostRules.coreTable(
                    LaunchVerb.valueOf(verb),
                    status.toInt(),
                    message(message),
                    attempts.toInt(),
                )

            assertWithMessage("vector $row").that(actual).isEqualTo(expected)
        }
    }

    @Test
    fun `a resume message needs the full phrase to count as no running app`() {
        // core/src/launch.cpp matches "no running app to resume"; a shorter text is a capture
        // failure.
        val actual = FakeHostRules.coreTable(LaunchVerb.RESUME, 503, "No running app", 0)

        assertThat(actual.action).isEqualTo(LaunchAction.RETRY)
        assertThat(actual.category).isEqualTo(LaunchCategory.CAPTURE_OR_DISPLAY)
    }

    private operator fun <T> List<T>.component6() = this[5]

    private operator fun <T> List<T>.component7() = this[6]

    private operator fun <T> List<T>.component8() = this[7]
}
