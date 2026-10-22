package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A row that ends in a value and a chevron (ADR 0058, decision 4): the text of the row is beside
 * the value while its longest word fits there, and when it does not (the text at the largest size,
 * with the letters and words spaced out, in a row of a phone) the value goes under the text, at the
 * end, so that no word is broken and nothing is cut.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.3f)
class SettingsRowTrailingTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(look: BorealisLook, content: @Composable () -> Unit) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone, look) {
                OnPage { Column(Modifier.width(ROW_WIDTH)) { content() } }
            }
        }
        composeRule.waitForIdle()
    }

    private val wider = BorealisLook(textSize = TextSize.Largest, textSpacing = TextSpacing.Wider)

    @Test
    fun `a summary with a long word that the value leaves no room for is not cut`() {
        show(wider) { ReduceMotion() }

        composeRule.assertNoClippedText()
    }

    @Test
    fun `the value goes under the text when the longest word does not fit beside it`() {
        show(wider) { ReduceMotion() }

        val summary =
            composeRule.onNodeWithText(SUMMARY, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val value =
            composeRule
                .onNodeWithText("Follow system", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

        assertThat(value.top).isAtLeast(summary.bottom)
        // At the end of the row, with the chevron after it.
        assertThat(value.right).isAtMost(ROW_WIDTH)
    }

    @Test
    fun `the value stays beside the text when the longest word fits there`() {
        show(BorealisLook()) { ReduceMotion() }

        val title = composeRule.onNodeWithText("Reduce motion", useUnmergedTree = true)
        val summary = composeRule.onNodeWithText(SUMMARY, useUnmergedTree = true)
        val value = composeRule.onNodeWithText("Follow system", useUnmergedTree = true)

        assertThat(value.getUnclippedBoundsInRoot().left)
            .isAtLeast(summary.getUnclippedBoundsInRoot().right)
        assertThat(value.getUnclippedBoundsInRoot().top)
            .isLessThan(summary.getUnclippedBoundsInRoot().bottom)
        assertThat(value.getUnclippedBoundsInRoot().bottom)
            .isGreaterThan(title.getUnclippedBoundsInRoot().top)
    }

    @Composable
    private fun ReduceMotion() {
        SettingsValueRow("Reduce motion", "Follow system", {}, summary = SUMMARY)
    }

    private companion object {
        /** A phone of 360 dp, less the 16 dp that the screen leaves on each side. */
        val ROW_WIDTH = 328.dp

        const val SUMMARY =
            "On removes animations; Off keeps them even when the device removes them. Follow " +
                "system uses the device's animation setting."
    }
}
