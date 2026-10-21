package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Where the caller's modifier lands on a [BorealisTextField]: on the whole field, glass included,
 * as it does for any component, with or without the keyboard on focus. A margin that the caller
 * gives is outside the glass and its outline.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BorealisTextFieldModifierTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `with the keyboard on focus a margin of the caller is outside the glass`() {
        show(showKeyboardOnFocus = true)

        assertMarginOutsideGlass()
    }

    @Test
    fun `without the keyboard on focus a margin of the caller is outside the glass`() {
        show(showKeyboardOnFocus = false)

        assertMarginOutsideGlass()
    }

    private fun show(showKeyboardOnFocus: Boolean) {
        composeRule.setContent {
            BorealisTheme { Frame { Field(showKeyboardOnFocus) } }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        Box(Modifier.testTag(FRAME).width(340.dp).height(100.dp).background(MARGIN)) { content() }
    }

    @Composable
    private fun Field(showKeyboardOnFocus: Boolean) {
        BorealisTextField(
            value = "",
            onValueChange = {},
            label = "Address",
            modifier = Modifier.padding(horizontal = 24.dp),
            showKeyboardOnFocus = showKeyboardOnFocus,
        )
    }

    /** The field is 24dp in from each side of the frame; it has no text on its right. */
    private fun assertMarginOutsideGlass() {
        val frame = composeRule.onNodeWithTag(FRAME)
        frame.assertPixel(x = 12f, y = 28f, MARGIN)
        frame.assertPixel(x = 300f, y = 28f, GLASS)
    }

    private companion object {
        const val FRAME = "frame"
        val MARGIN = Color.Magenta
        val GLASS = BorealisTokens.Color.Surface
    }
}
