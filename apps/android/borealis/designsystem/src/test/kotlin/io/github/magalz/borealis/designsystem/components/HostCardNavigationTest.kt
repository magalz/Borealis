package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A host card is one focus stop: the D-pad moves from card to card and never into the card. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HostCardNavigationTest {
    @get:Rule val composeRule = createComposeRule()
    private var opened = 0
    private var optionsRequested = 0

    @Test
    fun `D-pad Down from the card moves on to what is below it`() {
        composeRule.setContentInKeyboardMode { Specimen(withNeighbourBelow = true) }
        card().requestFocus()

        card().performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag(BELOW).assertIsFocused()
    }

    @Test
    fun `D-pad Right from the card moves on to what is beside it`() {
        composeRule.setContentInKeyboardMode { Specimen(withNeighbourBeside = true) }
        card().requestFocus()

        card().performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithTag(BESIDE).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `on a phone Right from the card moves on to what is beside it`() {
        composeRule.setContentInKeyboardMode {
            Specimen(formFactor = FormFactor.Phone, withNeighbourBeside = true)
        }
        card().requestFocus()

        card().performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithTag(BESIDE).assertIsFocused()
    }

    @Test
    fun `the card is the only focus stop of its content`() {
        composeRule.setContentInKeyboardMode { Specimen() }

        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `OK on the card runs its click and not the options`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        card().requestFocus()

        card().performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(opened).isEqualTo(1)
        assertThat(optionsRequested).isEqualTo(0)
    }

    @Test
    fun `holding OK on the card asks for the options and does not click it`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        card().requestFocus()

        card().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(HOLD_MILLIS)
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        assertThat(optionsRequested).isEqualTo(1)
        assertThat(opened).isEqualTo(0)
    }

    @Test
    fun `X on the card reaches the options`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        card().requestFocus()

        card().performKeyInput { pressKey(Key.ButtonX) }

        assertThat(optionsRequested).isEqualTo(1)
        assertThat(opened).isEqualTo(0)
    }

    private fun card(): SemanticsNodeInteraction = composeRule.onNodeWithTag(CARD)

    @Composable
    private fun Specimen(
        formFactor: FormFactor = FormFactor.Tv,
        withNeighbourBelow: Boolean = false,
        withNeighbourBeside: Boolean = false,
    ) {
        BorealisTheme(formFactor) {
            Column {
                Row {
                    Box(Modifier.padding(24.dp)) {
                        HostCard(
                            name = "Living Room PC",
                            status = DotStatus.Offline,
                            statusText = "Offline",
                            onClick = { opened++ },
                            onOptions = { optionsRequested++ },
                            modifier = Modifier.width(220.dp).testTag(CARD),
                        )
                    }
                    if (withNeighbourBeside) Box(Modifier.size(40.dp).testTag(BESIDE).focusable())
                }
                if (withNeighbourBelow) Box(Modifier.size(40.dp).testTag(BELOW).focusable())
            }
        }
    }

    private companion object {
        const val CARD = "card"
        const val BELOW = "below"
        const val BESIDE = "beside"
        const val HOLD_MILLIS = 1_200L
    }
}
