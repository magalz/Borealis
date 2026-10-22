package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Running badge of a cover tile fits inside its cover, whatever the width of the cover and the
 * size of the text (ADR 0058, decision 7: the badge was cut on a phone with 4 columns, where the
 * cover is 73dp wide). It is never clipped, its label is never split over lines inside a word and
 * never ends in an ellipsis. Where the label does not fit on one line the badge is the dot alone,
 * and the tile still says that its app runs, to a screen reader, through its state.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class CoverTileRunningBadgeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `the badge fits inside a cover that is 73dp wide`() = assertFits(NARROW)

    @Test
    fun `the badge fits inside a cover that is 73dp wide at the largest text`() =
        assertFits(NARROW, TextSize.Largest, SYSTEM_SCALE)

    @Test
    fun `the badge fits inside a cover that is 101dp wide at the largest text`() =
        assertFits(PHONE_COVER, TextSize.Largest, SYSTEM_SCALE)

    @Test
    fun `the badge keeps its label when the label fits on one line`() {
        show(WIDE)

        assertThat(labelLines()).isEqualTo(1)
        composeRule.onNodeWithTag(BADGE, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a badge that has no room for its label shows the dot alone`() {
        show(NARROW, TextSize.Largest, SYSTEM_SCALE)

        composeRule.onNodeWithText(LABEL, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(BADGE, useUnmergedTree = true).assertExists()
    }

    /** The lines of the label, or 0 where the badge has no label. */
    private fun labelLines(): Int {
        val label = composeRule.onAllNodesWithText(LABEL, useUnmergedTree = true)
        return if (label.fetchSemanticsNodes().isEmpty()) 0 else label[0].textLayout().lineCount
    }

    private fun assertFits(width: Dp, textSize: TextSize = TextSize.Default, scale: Float = 1f) {
        show(width, textSize, scale)

        val cover = composeRule.onNodeWithContentDescription("Desktop").getUnclippedBoundsInRoot()
        val labels = composeRule.onAllNodesWithText(LABEL, useUnmergedTree = true)
        if (labels.fetchSemanticsNodes().isNotEmpty()) {
            // The label is in one piece and inside the cover.
            assertThat(labelLines()).isEqualTo(1)
            val label = labels[0].getUnclippedBoundsInRoot()
            assertThat(label.left.value).isAtLeast(cover.left.value)
            assertThat(label.right.value).isAtMost(cover.right.value)
            assertThat(label.bottom.value).isAtMost(cover.bottom.value)
        }
        val badge =
            composeRule.onNodeWithTag(BADGE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(badge.left.value).isAtLeast(cover.left.value)
        assertThat(badge.right.value).isAtMost(cover.right.value)
        assertThat(badge.top.value).isAtLeast(cover.top.value)
        assertThat(badge.bottom.value).isAtMost(cover.bottom.value)
        composeRule.assertNoClippedText()
    }

    private fun show(width: Dp, textSize: TextSize = TextSize.Default, scale: Float = 1f) {
        composeRule.setContent {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density, scale)) {
                BorealisTheme(FormFactor.Phone, BorealisLook(textSize = textSize)) {
                    CoverTile(
                        name = "Desktop",
                        onClick = {},
                        modifier = Modifier.width(width),
                        runningLabel = "Running",
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        /** A cover of a phone of 360dp with 4 columns: (360 - 2 * 16 - 3 * 12) / 4. */
        val NARROW = 73.dp

        /** A cover of a phone of 360dp with 3 columns: (360 - 2 * 16 - 2 * 12) / 3. */
        val PHONE_COVER = 101.dp
        val WIDE = 160.dp
        const val SYSTEM_SCALE = 1.3f
        const val BADGE = "running-badge"
        const val LABEL = "Running"
    }
}
