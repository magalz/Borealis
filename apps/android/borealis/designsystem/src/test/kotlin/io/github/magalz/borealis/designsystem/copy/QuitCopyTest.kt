package io.github.magalz.borealis.designsystem.copy

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one source of what Quit costs: the Apps sheet, the Quit popup of the stream and the conflict
 * dialog read these lines. Desktop has no program to close, so Quit only ends the stream and
 * nothing is lost; any other app closes and loses its unsaved progress. A sentence on each line.
 */
@RunWith(AndroidJUnit4::class)
class QuitCopyTest {
    @get:Rule val composeRule = createComposeRule()

    /** The lines [lines] gives, read in one composition. */
    private fun read(lines: @Composable () -> List<String>): List<String> {
        var said = emptyList<String>()
        composeRule.setContent { said = lines() }
        composeRule.waitForIdle()
        return said
    }

    @Test
    fun `the consequence of Quit names a game and says its unsaved progress is lost`() {
        val said = read {
            listOf(
                QuitCopy.consequence("Hades II", desktop = false),
                QuitCopy.consequence("the running app", desktop = false),
            )
        }

        assertThat(said)
            .containsExactly(
                "Closes Hades II on the PC.\nUnsaved progress in it is lost.",
                "Closes the running app on the PC.\nUnsaved progress in it is lost.",
            )
            .inOrder()
    }

    @Test
    fun `the consequence of Quit on Desktop says it ends the stream and no more`() {
        val said = read { listOf(QuitCopy.consequence("Desktop", desktop = true)) }.single()

        assertThat(said).isEqualTo("Ends the stream.\nThe display and controls go back to the PC.")
        assertThat(said).doesNotContain("Unsaved")
        assertThat(said).doesNotContain("lost")
    }

    @Test
    fun `the conflict warning for a game says quitting closes it and its progress is lost`() {
        val said = read { listOf(QuitCopy.conflictWarning(desktop = false)) }.single()

        assertThat(said)
            .isEqualTo(
                "Quitting ends the stream on the other device and closes the app.\n" +
                    "Unsaved progress in it is lost."
            )
    }

    @Test
    fun `the conflict warning for Desktop says quitting ends the stream on the other device`() {
        val said = read { listOf(QuitCopy.conflictWarning(desktop = true)) }.single()

        assertThat(said).isEqualTo("Quitting ends the stream on the other device.")
        assertThat(said).doesNotContain("Unsaved")
    }
}
