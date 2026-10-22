package io.github.magalz.borealis.library.pairing

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The room that the two columns of the Pairing screen of a TV leave each other, at a large text.
 * The PIN column is as wide as the longest of its lines, and the steps take the rest. The line
 * under the PIN, "The PIN stays valid for 5 minutes.", is the longest by far when the text is
 * large, and left the steps no room for a word (ADR 0058, decision 4); it wraps at the width of the
 * note about a restart instead.
 */
@RunWith(AndroidJUnit4::class)
class PairingTvRoomTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show() {
        composeRule.setContent {
            LibraryScaffold(
                FormFactor.Tv,
                RailSelection.Hosts,
                RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
            ) {
                PairingScreen(
                    PairingUiState.Waiting(
                        "4817",
                        "https://192.168.1.40:47990",
                        "Galaxy S22",
                        false,
                    ),
                    FormFactor.Tv,
                    onGetNewPin = {},
                    onCancel = {},
                    hostName = "Studio",
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun width(text: String): Dp {
        val bounds =
            composeRule
                .onNodeWithText(text, substring = true, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        return bounds.right - bounds.left
    }

    private fun lines(text: String): Int =
        composeRule
            .onNodeWithText(text, substring = true, useUnmergedTree = true)
            .textLayout()
            .lineCount

    @Test
    @Config(qualifiers = TV)
    fun `at the default size the line under the PIN is one line and the steps keep their room`() {
        show()

        assertThat(lines("The PIN stays valid")).isEqualTo(1)
        assertThat(width("Open the LuminalShine")).isAtLeast(STEP_ROOM)
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `at a font scale of 2 point 0 the line under the PIN wraps at the width of the note`() {
        show()

        assertThat(lines("The PIN stays valid")).isGreaterThan(1)
        assertThat(width("The PIN stays valid")).isAtMost(NOTE_WIDTH)
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `at a font scale of 2 point 0 the steps of a TV keep room for their words`() {
        show()

        assertThat(width("Open the LuminalShine")).isAtLeast(STEP_ROOM)
        assertThat(width("Avoid")).isAtLeast(STEP_ROOM)
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `the PIN stays in one row of four cells at a font scale of 2 point 0`() {
        show()

        val pin = composeRule.onNodeWithContentDescription("4, 8, 1, 7").getUnclippedBoundsInRoot()
        assertThat(pin.right - pin.left).isAtLeast(CELL_WIDTH * 4)
        assertThat(pin.bottom - pin.top).isLessThan(CELL_HEIGHT * 2)
    }

    private companion object {
        /** The width of the note about a restart, which the line under the PIN wraps at too. */
        val NOTE_WIDTH = 280.dp

        /**
         * Room for the words of a step: the 149 dp that the steps had at 2.0 cut "LuminalShine".
         */
        val STEP_ROOM = 200.dp

        val CELL_WIDTH = 56.dp
        val CELL_HEIGHT = 64.dp
    }
}
