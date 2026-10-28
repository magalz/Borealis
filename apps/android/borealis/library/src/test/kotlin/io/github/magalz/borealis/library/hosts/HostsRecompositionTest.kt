package io.github.magalz.borealis.library.hosts

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.testing.recompositions
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the screen costs while the focus moves. Hosts remembers the card that has focus so that it
 * can give it back, but a D-pad move is a frame that must not recompose the cards.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class HostsRecompositionTest {
    @get:Rule val composeRule = createComposeRule()

    private var counter by mutableIntStateOf(0)

    @Test
    fun `moving the focus between cards recomposes nothing`() {
        show()
        composeRule.onNodeWithTag("host:living").requestFocus()
        composeRule.waitForIdle()
        // A control: a state read in composition does recompose, and the count sees it.
        val settled = recompositions()
        repeat(2) {
            composeRule.runOnIdle { counter++ }
            composeRule.waitForIdle()
        }
        assertThat(recompositions()).isGreaterThan(settled)

        val before = recompositions()
        repeat(MOVES) {
            composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }
        }
        composeRule.waitForIdle()

        assertThat(recompositions()).isEqualTo(before)
    }

    private fun show() {
        composeRule.setContentInKeyboardMode {
            Box {
                HostsScreen(
                    state = withHostsNoContinue,
                    formFactor = FormFactor.Tv,
                    snackbarHostState = SnackbarHostState(),
                    onHostSelected = {},
                    onContinue = {},
                    onWake = {},
                    onForget = {},
                    onDetails = {},
                    onDetailsDismissed = {},
                    onAddHost = {},
                    onSettings = {},
                )
                Text("$counter")
            }
        }
    }

    private companion object {
        const val MOVES = 6
    }
}
