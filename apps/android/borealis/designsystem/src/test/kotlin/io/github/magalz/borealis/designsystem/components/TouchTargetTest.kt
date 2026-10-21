package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width as dpWidth
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.settings.SettingsGroup
import io.github.magalz.borealis.designsystem.components.settings.SettingsTwoPane
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.assertAccessible
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a finger can hit (WCAG 2.5.8; Android asks for 48dp): a control that is touched is at least
 * 48dp wide and high on a phone and on a handheld, which has a touch screen or has one beside its
 * gamepad. A TV has no touch screen, so its controls keep the size that the TV design gives them.
 */
@RunWith(AndroidJUnit4::class)
class TouchTargetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun `every segment of a segmented control is 48dp wide and high on a phone`() {
        composeRule.setContent { Segments(FormFactor.Phone) }

        for (label in SEGMENTS) assertAtLeast48(label)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `every segment of a segmented control is 48dp wide and high on a handheld`() {
        composeRule.setContent { Segments(FormFactor.Handheld) }

        for (label in SEGMENTS) assertAtLeast48(label)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a segment of a segmented control keeps its 32dp height on a TV`() {
        composeRule.setContent { Segments(FormFactor.Tv) }

        for (label in SEGMENTS) {
            assertThat(composeRule.onNodeWithText(label).getUnclippedBoundsInRoot().height)
                .isEqualTo(32.dp)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a chip in a wrap of chips is 48dp high on a phone`() {
        composeRule.setContent { Chips(FormFactor.Phone) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a chip keeps its size on a TV`() {
        composeRule.setContent { Chips(FormFactor.Tv) }

        assertThat(composeRule.onNodeWithText("Alt+Tab").getUnclippedBoundsInRoot().height)
            .isLessThan(30.dp)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a group of the settings list is 48dp high on a handheld`() {
        composeRule.setContent { Groups(FormFactor.Handheld) }

        for (group in GROUPS) assertAtLeast48(group)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a group of the settings list keeps its height on a TV`() {
        composeRule.setContent { Groups(FormFactor.Tv) }

        assertThat(composeRule.onNodeWithText("Video").getUnclippedBoundsInRoot().height)
            .isLessThan(40.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the handle of a bottom sheet is 48dp wide and high`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                ActionSheet(
                    title = "Desktop",
                    actions = listOf(ActionSheetAction("a", "Resume", "Back to the app")),
                    onAction = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.assertAccessible()
    }

    /** 48dp, give or take the rounding of a pixel at a density that is not a whole number. */
    private fun assertAtLeast48(text: String) {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        assertThat(bounds.dpWidth.value).isAtLeast(ROUNDED_48)
        assertThat(bounds.height.value).isAtLeast(ROUNDED_48)
    }

    @Composable
    private fun Segments(formFactor: FormFactor) {
        BorealisTheme(formFactor) { SegmentedControl(SEGMENTS, "60", { it }, {}) }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Chips(formFactor: FormFactor) {
        BorealisTheme(formFactor) {
            OnPage {
                // The wrap of the menu, narrow enough to wrap: 8dp from the edges, and the gaps
                // that
                // keep the outlines of two focus stops apart.
                FlowRow(
                    Modifier.width(240.dp).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (key in listOf("Alt+Tab", "Win+Tab", "Ctrl+Shift+Esc", "Alt+F4", "Win+D")) {
                        Chip(key, onClick = {})
                    }
                }
            }
        }
    }

    @Composable
    private fun Groups(formFactor: FormFactor) {
        BorealisTheme(formFactor) {
            SettingsTwoPane(
                groups = GROUPS.map { SettingsGroup(it.lowercase(), it) },
                selected = { "stream" },
                onSelect = {},
            ) {
                SettingsValueRow("Resolution", "1280 x 720", {})
            }
        }
    }

    private companion object {
        val SEGMENTS = listOf("30", "60", "120")
        val GROUPS = listOf("Stream", "Video", "Audio")
        const val ROUNDED_48 = 47.99f
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        const val HANDHELD = "w800dp-h480dp-land-hdpi"
    }
}
