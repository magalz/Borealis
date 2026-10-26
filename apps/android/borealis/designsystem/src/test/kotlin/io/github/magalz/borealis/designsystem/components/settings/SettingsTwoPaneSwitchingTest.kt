package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the pane of a [SettingsTwoPane] shows when the group changes: the new group looks fresh,
 * with no switch sliding and no scroll left over from the last time it was shown.
 */
class SettingsTwoPaneSwitchingTest : SettingsTwoPaneTestBase("on") {
    private val groups =
        listOf(
            SettingsGroup("on", "Switch on"),
            SettingsGroup("off", "Switch off"),
            SettingsGroup("long", "Long"),
            SettingsGroup("mid", "Mid"),
        )

    @Test
    fun `a switch of the new group shows its own state at once instead of sliding to it`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        composeRule.onNodeWithText("Switch off").performClick()
        // The first frames compose and lay out the new group.
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        val soon = pixelsOfSwitchRow()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val settled = pixelsOfSwitchRow()

        assertThat(soon).isEqualTo(settled)
    }

    @Test
    fun `a group opens at its top, whatever the group before was scrolled to`() {
        composeRule.setContent { Specimen() }
        composeRule.onNodeWithText("Long").performClick()
        composeRule.onNodeWithText("Long row 30").performScrollTo()

        composeRule.onNodeWithText("Mid").performClick()

        composeRule.onNodeWithText("Mid row 1").assertIsDisplayed()
    }

    @Test
    fun `a group that was scrolled opens at its top when it is shown again`() {
        composeRule.setContent { Specimen() }
        composeRule.onNodeWithText("Long").performClick()
        composeRule.onNodeWithText("Long row 30").performScrollTo()
        composeRule.onNodeWithText("Mid").performClick()

        composeRule.onNodeWithText("Long").performClick()

        composeRule.onNodeWithText("Long row 1").assertIsDisplayed()
    }

    private fun pixelsOfSwitchRow(): List<Int> {
        val image = composeRule.onNodeWithText("The switch").captureToImage().toPixelMap()
        return buildList {
            for (x in 0 until image.width step 2) {
                for (y in 0 until image.height step 2) add(image[x, y].hashCode())
            }
        }
    }

    @Composable
    private fun Specimen() {
        TwoPane(groups) { group ->
            when (group) {
                // One call site for both groups, as the rows of a screen are drawn from a list.
                "on",
                "off" -> SettingsSwitchRow("The switch", group == "on", onCheckedChange = {})
                "long" -> repeat(LONG_ROWS) { SettingsValueRow("Long row ${it + 1}", null, {}) }
                else -> repeat(MID_ROWS) { SettingsValueRow("Mid row ${it + 1}", null, {}) }
            }
        }
    }

    private companion object {
        /** Longer than any animation of the pane. */
        const val SETTLE_MILLIS = 1_000L
        const val LONG_ROWS = 30

        /** More rows than fit in the pane, so the pane scrolls, but fewer than the long group. */
        const val MID_ROWS = 12
    }
}
