package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.host.Fixtures
import io.github.magalz.borealis.nativecore.LaunchAction
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.nativecore.LaunchDecision
import io.github.magalz.borealis.nativecore.LaunchVerb
import io.github.magalz.borealis.nativecore.PairAction
import io.github.magalz.borealis.nativecore.PairCategory
import io.github.magalz.borealis.nativecore.PairDecision
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
            val expected =
                LaunchDecision(
                    LaunchAction.valueOf(row[4]),
                    row[5].toInt(),
                    LaunchCategory.valueOf(row[6]),
                    row[7].toInt(),
                )

            val actual =
                FakeHostRules.coreTable(
                    LaunchVerb.valueOf(row[0]),
                    row[1].toInt(),
                    message(row[2]),
                    row[3].toInt(),
                )

            assertWithMessage("vector $row").that(actual).isEqualTo(expected)
        }
    }

    @Test
    fun `the fake answers every pairing vector of the core`() {
        val rows =
            Fixtures.text("vectors/pair_classify.tsv")
                .lines()
                .map { it.trimEnd('\r') }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .drop(1)
                .map { it.split('\t') }
        assertThat(rows).isNotEmpty()

        for (row in rows) {
            val expected = PairDecision(PairAction.valueOf(row[2]), PairCategory.valueOf(row[3]))

            val actual = FakeHostRules.corePairTable(row[0].toInt(), message(row[1]))

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
}
