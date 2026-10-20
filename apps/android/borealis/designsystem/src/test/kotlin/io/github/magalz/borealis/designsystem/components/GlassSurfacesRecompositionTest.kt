package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.components.settings.CollapsibleSection
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.glass.GlassHost
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the glass costs per frame is the draw of the surfaces and nothing else: the background moves
 * under a Host card and a section of the Settings, which show it blurred, and neither recomposes.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GlassSurfacesRecompositionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the frames of the background redraw the glass surfaces and recompose none of them`() {
        composeRule.mainClock.autoAdvance = false
        var counter by mutableIntStateOf(0)
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                GlassHost(BackgroundStyle.AuroraCurtains, Modifier.testTag(ROOT)) {
                    Column {
                        // A control: a state read in composition does recompose, and the count
                        // sees it.
                        Text("$counter")
                        HostCard(
                            name = "Desk PC",
                            status = DotStatus.Online,
                            statusText = "Online",
                            onClick = {},
                            modifier = Modifier.width(240.dp).testTag(CARD),
                            detail = "192.168.1.20",
                        )
                        CollapsibleSection(
                            "Stream",
                            expanded = true,
                            onExpandedChange = {},
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SettingsValueRow("Resolution", "Native", onClick = {})
                        }
                    }
                }
            }
        }
        settle()
        val settled = recompositions()
        repeat(2) {
            composeRule.runOnIdle { counter++ }
            settle()
        }
        assertThat(recompositions()).isGreaterThan(settled)

        val before = recompositions()
        val cardBefore = card()
        composeRule.mainClock.advanceTimeBy(FIVE_SECONDS)
        settle()

        // The glass of the card shows the background that moved: it was redrawn.
        assertThat(card()).isNotEqualTo(cardBefore)
        assertThat(recompositions()).isEqualTo(before)
    }

    /**
     * Lets the recomposer take the changes. Its count runs one recomposition behind, so a control
     * needs two.
     */
    private fun settle() {
        repeat(2) { composeRule.mainClock.advanceTimeBy(100) }
    }

    private fun card(): IntArray =
        composeRule.onNodeWithTag(CARD).captureToImage().toPixelMap().buffer

    /** How many changes the recomposers of the test have taken: a count of recompositions. */
    @OptIn(ExperimentalComposeApi::class)
    private fun recompositions(): Long =
        Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    private companion object {
        const val ROOT = "root"
        const val CARD = "card"
        const val FIVE_SECONDS = 5_000L
    }
}
