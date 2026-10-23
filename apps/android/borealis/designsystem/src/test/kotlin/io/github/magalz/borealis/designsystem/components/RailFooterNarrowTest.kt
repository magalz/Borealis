package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The name in the footer of the rail is one word. When the rail is too narrow for it at the text
 * that is drawn (the Wider spacing at a font scale of 2.0, in the wide fonts, was the case), the
 * name wraps inside the word instead of ending in an ellipsis: nothing is cut off (ADR 0058,
 * decision 4).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h480dp-land-hdpi-notouch")
class RailFooterNarrowTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(width: Int) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Handheld) {
                Box(Modifier.width(width.dp)) { RailFooter("Borealis", null, "Borealis") }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the name fits in a rail that has the room for it`() {
        show(RAIL_WIDTH)

        composeRule.assertNoClippedText()
    }

    @Test
    fun `the name wraps inside the word in a rail that is too narrow for it`() {
        show(NARROW_RAIL_WIDTH)

        composeRule.assertNoClippedText()
    }

    private companion object {
        const val RAIL_WIDTH = 160
        const val NARROW_RAIL_WIDTH = 80
    }
}
