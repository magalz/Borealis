package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.letterSpacingEm
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The version in the footer of the rail has no letter spacing of its own at Normal: it is a
 * version, not a kicker, so the 0.07 em of its style is taken off. At Wide and Wider it has the
 * level, like every text (ADR 0058, decision 4).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class RailFooterSpacingTest {
    @get:Rule val composeRule = createComposeRule()

    private var spacing by mutableStateOf(TextSpacing.Normal)

    private fun show() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(textSpacing = spacing)) {
                RailFooter("Borealis", VERSION, "Borealis, version 0.1.0")
            }
        }
        composeRule.waitForIdle()
    }

    private fun version() =
        composeRule.onNodeWithText(VERSION, useUnmergedTree = true).textLayout().layoutInput.style

    @Test
    fun `at Normal the version has no letter spacing`() {
        show()

        assertThat(version().letterSpacing).isEqualTo(0.sp)
    }

    @Test
    fun `at Wide and at Wider the version has the letter spacing of the level`() {
        spacing = TextSpacing.Wide
        show()
        assertThat(version().letterSpacingEm()).isAtLeast(TextSpacing.Wide.letterEm)

        spacing = TextSpacing.Wider
        composeRule.waitForIdle()
        assertThat(version().letterSpacingEm()).isAtLeast(TextSpacing.Wider.letterEm)

        spacing = TextSpacing.Normal
        composeRule.waitForIdle()
        assertThat(version().letterSpacing).isEqualTo(0.sp)
    }

    private companion object {
        const val VERSION = "0.1.0 (abc1234)"
    }
}
