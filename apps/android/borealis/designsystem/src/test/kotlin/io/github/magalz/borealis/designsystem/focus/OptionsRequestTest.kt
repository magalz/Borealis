package io.github.magalz.borealis.designsystem.focus

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every way to ask for the options of an item (the input rules of the Phase 2 proposal). */
@RunWith(AndroidJUnit4::class)
class OptionsRequestTest {
    @get:Rule val composeRule = createComposeRule()
    private var clicks = 0
    private var options = 0

    @Test
    fun `X on a gamepad requests the options`() {
        composeRule.setContentInKeyboardMode { Item() }

        item().requestFocus()
        item().performKeyInput { pressKey(Key.ButtonX) }

        assertThat(options).isEqualTo(1)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `onOptionsRequest works on a plain focusable`() {
        composeRule.setContentInKeyboardMode {
            Box(Modifier.size(80.dp).testTag(ITEM).onOptionsRequest { options++ }.focusable())
        }

        item().requestFocus()
        item().performKeyInput { pressKey(Key.ButtonX) }

        assertThat(options).isEqualTo(1)
    }

    @Test
    fun `the menu key requests the options`() {
        composeRule.setContentInKeyboardMode { Item() }

        item().requestFocus()
        item().performKeyInput { pressKey(Key.Menu) }

        assertThat(options).isEqualTo(1)
    }

    @Test
    fun `holding X requests the options once`() {
        composeRule.setContentInKeyboardMode { Item() }
        item().requestFocus()

        item().performKeyInput {
            keyDown(Key.ButtonX)
            advanceEventTime(HOLD_MILLIS)
            keyUp(Key.ButtonX)
        }

        assertThat(options).isEqualTo(1)
    }

    @Test
    fun `a short press of OK clicks and does not request the options`() {
        composeRule.setContentInKeyboardMode { Item() }

        item().requestFocus()
        item().performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(clicks).isEqualTo(1)
        assertThat(options).isEqualTo(0)
    }

    @Test
    fun `holding OK requests the options and does not click`() {
        composeRule.setContentInKeyboardMode { Item() }
        item().requestFocus()

        item().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(HOLD_MILLIS)
            keyUp(Key.DirectionCenter)
        }

        assertThat(options).isEqualTo(1)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `holding Enter requests the options and does not click`() {
        composeRule.setContentInKeyboardMode { Item() }
        item().requestFocus()

        item().performKeyInput {
            keyDown(Key.Enter)
            advanceEventTime(HOLD_MILLIS)
            keyUp(Key.Enter)
        }

        assertThat(options).isEqualTo(1)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `a long press on touch requests the options and does not click`() {
        composeRule.setContent { Item() }

        item().performTouchInput { longClick() }

        assertThat(options).isEqualTo(1)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `a tap clicks`() {
        composeRule.setContent { Item() }

        item().performTouchInput { click() }

        assertThat(clicks).isEqualTo(1)
        assertThat(options).isEqualTo(0)
    }

    @Test
    fun `an item without options ignores X and the menu key`() {
        composeRule.setContentInKeyboardMode { Item(withOptions = false) }
        item().requestFocus()

        item().performKeyInput {
            pressKey(Key.ButtonX)
            pressKey(Key.Menu)
        }

        assertThat(options).isEqualTo(0)
    }

    @Composable
    private fun Item(withOptions: Boolean = true) {
        Box(
            Modifier.size(80.dp)
                .testTag(ITEM)
                .optionsClickable(
                    onClick = { clicks++ },
                    onOptions = if (withOptions) ({ options++ }) else null,
                )
        )
    }

    private fun item(): SemanticsNodeInteraction = composeRule.onNodeWithTag(ITEM)

    private companion object {
        const val ITEM = "item"

        /** Longer than the long-press timeout, so the platform reports a held key. */
        const val HOLD_MILLIS = 1_200L
    }
}
