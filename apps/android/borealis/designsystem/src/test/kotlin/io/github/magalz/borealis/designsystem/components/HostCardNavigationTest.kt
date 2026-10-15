package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The two focus stops of a host card: the card, and the inline action inside it. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HostCardNavigationTest {
    @get:Rule val composeRule = createComposeRule()
    private var opened = 0
    private var optionsRequested = 0
    private var acted = 0

    @Test
    fun `D-pad Down from the card reaches its inline button and Up returns`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        card().requestFocus()

        card().performKeyInput { pressKey(Key.DirectionDown) }
        button().assertIsFocused()

        button().performKeyInput { pressKey(Key.DirectionUp) }
        card().assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `on a phone the inline button is beside the card text so Right reaches it and Left returns`() {
        composeRule.setContentInKeyboardMode { Specimen(formFactor = FormFactor.Phone) }
        card().requestFocus()

        card().performKeyInput { pressKey(Key.DirectionRight) }
        button().assertIsFocused()

        button().performKeyInput { pressKey(Key.DirectionLeft) }
        card().assertIsFocused()
    }

    @Test
    fun `D-pad Down from the inline button moves on to what is below the card`() {
        composeRule.setContentInKeyboardMode { Specimen(withNeighbourBelow = true) }
        button().requestFocus()

        button().performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag(BELOW).assertIsFocused()
    }

    @Test
    fun `the card outline is not drawn while the inline button has focus`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        card().requestFocus()
        card().performKeyInput { pressKey(Key.DirectionDown) }
        button().assertIsFocused()

        val width = card().getUnclippedBoundsInRoot().width.value
        composeRule
            .onNodeWithTag(FRAME)
            .assertPixel(x = 24f + width / 2, y = 24f - 3.5f, BorealisTokens.Color.Page)
    }

    @Test
    fun `X while the inline button has focus reaches the options of the card`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        button().requestFocus()
        button().assertIsFocused()

        button().performKeyInput { pressKey(Key.ButtonX) }

        assertThat(optionsRequested).isEqualTo(1)
        assertThat(acted).isEqualTo(0)
    }

    @Test
    fun `OK on the inline button runs the action and not the card`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        button().requestFocus()

        button().performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(acted).isEqualTo(1)
        assertThat(opened).isEqualTo(0)
    }

    private fun card(): SemanticsNodeInteraction = composeRule.onNodeWithTag(CARD)

    private fun button(): SemanticsNodeInteraction =
        composeRule.onNode(
            hasClickAction() and hasAnyChild(hasText("Wake")),
            useUnmergedTree = true,
        )

    @Composable
    private fun Specimen(
        formFactor: FormFactor = FormFactor.Tv,
        withNeighbourBelow: Boolean = false,
    ) {
        BorealisTheme(formFactor) {
            Column {
                Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page).padding(24.dp)) {
                    HostCard(
                        name = "Living Room PC",
                        status = DotStatus.Offline,
                        statusText = "Offline",
                        action = HostCardAction("Wake") { acted++ },
                        onClick = { opened++ },
                        onOptions = { optionsRequested++ },
                        modifier = Modifier.width(220.dp).testTag(CARD),
                    )
                }
                if (withNeighbourBelow) Box(Modifier.size(40.dp).testTag(BELOW).focusable())
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
        const val CARD = "card"
        const val BELOW = "below"
    }
}
