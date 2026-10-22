package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.components.settings.SettingsGroup
import io.github.magalz.borealis.designsystem.components.settings.SettingsTwoPane
import io.github.magalz.borealis.designsystem.pixelAt
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * How a selection is marked (ADR 0058, decision 5). The selected segment, chip, rail item and group
 * of the Settings list draw their label in accent-soft, the role that keeps 4.5:1 over the accent
 * tint on the glass for every accent. White also marks the segment, the rail item and the group
 * with an indicator bar in `accent`, as its colour alone is too near the text colour; the other
 * accents have no bar.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SelectionMarkTest {
    @get:Rule val composeRule = createComposeRule()

    private var look by mutableStateOf(BorealisLook())
    private var direction by mutableStateOf(LayoutDirection.Ltr)

    private val rail =
        listOf("Hosts", "Add host", "Settings").map { RailItem(it, BorealisIcons.Gear) }
    private val groups = listOf(SettingsGroup("stream", "Stream"), SettingsGroup("video", "Video"))

    /** Every control of the selection marks, with the first of each selected. */
    @Composable
    private fun Specimen() {
        CompositionLocalProvider(LocalLayoutDirection provides direction) { Marks() }
    }

    @Composable
    private fun Marks() {
        BorealisTheme(FormFactor.Tv, look) {
            Row(Modifier.fillMaxSize().background(BorealisTokens.Color.Page)) {
                NavigationRail(rail, selectedIndex = 0, onSelect = {})
                Column(Modifier.weight(1f).padding(24.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SegmentedControl(listOf("30", "60"), "60", { "$it fps" }, {})
                        Chip("Alt+Tab", selected = true)
                        Chip("Win+D")
                    }
                    SettingsTwoPane(
                        groups,
                        selected = { "stream" },
                        onSelect = {},
                        modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(200.dp),
                    ) {
                        Box(Modifier.padding(8.dp))
                    }
                }
            }
        }
    }

    private fun show(accent: AccentChoice) {
        look = BorealisLook(accent = accent)
        composeRule.waitForIdle()
    }

    /**
     * Whether the item named [label] has the accent where its bar is: 1.5 dp in from the leading
     * edge and half way down for a rail item or a group, and 3.5 dp up from the bottom edge, half
     * way across, for a segment. With [trailing] a vertical bar is looked for 1.5 dp in from the
     * trailing edge of a right to left layout, which is the right one.
     */
    private fun hasBar(label: String, vertical: Boolean, trailing: Boolean = false): Boolean {
        val node = composeRule.onNodeWithText(label)
        val size = node.getUnclippedBoundsInRoot()
        val (x, y) =
            if (vertical) {
                val x = if (trailing) size.width.value - BAR_MIDDLE_DP else BAR_MIDDLE_DP
                x to size.height.value / 2
            } else {
                size.width.value / 2 to size.height.value - BAR_BOTTOM_DP
            }
        val accent = borealisColors(look).accent
        val pixel = node.pixelAt(x, y)
        val distance =
            abs(pixel.red - accent.red) +
                abs(pixel.green - accent.green) +
                abs(pixel.blue - accent.blue)
        return distance < PIXEL_TOLERANCE
    }

    // The label

    @Test
    fun `the selected segment, chip, rail item and group draw their label in accent-soft`() {
        composeRule.setContent { Specimen() }

        for (accent in AccentChoice.entries) {
            show(accent)
            val colors = borealisColors(look)

            for (label in listOf("60 fps", "Alt+Tab", "Hosts", "Stream")) {
                assertWithMessage("$accent $label")
                    .that(composeRule.textColor(label))
                    .isEqualTo(colors.accentSoft)
            }
        }
    }

    @Test
    fun `what is not selected keeps its own text colour`() {
        composeRule.setContent { Specimen() }

        for (accent in AccentChoice.entries) {
            show(accent)
            val colors = borealisColors(look)

            assertThat(composeRule.textColor("30 fps")).isEqualTo(colors.ink3)
            assertThat(composeRule.textColor("Win+D")).isEqualTo(colors.ink2)
            assertThat(composeRule.textColor("Add host")).isEqualTo(colors.ink2)
            assertThat(composeRule.textColor("Video")).isEqualTo(colors.ink2)
        }
    }

    // The bar of white

    /** The selected items and whether the bar is vertical, on the leading edge. */
    private val selectedItems = mapOf("60 fps" to false, "Hosts" to true, "Stream" to true)
    private val otherItems = mapOf("30 fps" to false, "Add host" to true, "Video" to true)

    @Test
    fun `white marks the selected segment, rail item and group with a bar in accent`() {
        composeRule.setContent { Specimen() }

        show(AccentChoice.White)

        for ((label, vertical) in selectedItems) {
            assertWithMessage("bar of $label").that(hasBar(label, vertical)).isTrue()
        }
    }

    @Test
    fun `white marks only what is selected`() {
        composeRule.setContent { Specimen() }

        show(AccentChoice.White)

        for ((label, vertical) in otherItems) {
            assertWithMessage("bar of $label").that(hasBar(label, vertical)).isFalse()
        }
    }

    @Test
    fun `violet and teal mark a selection by colour alone, with no bar`() {
        composeRule.setContent { Specimen() }

        for (accent in listOf(AccentChoice.Violet, AccentChoice.Teal)) {
            show(accent)

            for ((label, vertical) in selectedItems) {
                assertWithMessage("bar of $label of $accent")
                    .that(hasBar(label, vertical))
                    .isFalse()
            }
        }
    }

    @Test
    fun `in a right to left layout the vertical bar of white is on the trailing edge`() {
        composeRule.setContent { Specimen() }
        direction = LayoutDirection.Rtl

        show(AccentChoice.White)

        for (label in listOf("Hosts", "Stream")) {
            assertWithMessage("trailing bar of $label")
                .that(hasBar(label, vertical = true, trailing = true))
                .isTrue()
            assertWithMessage("no bar on the left of $label")
                .that(hasBar(label, vertical = true))
                .isFalse()
        }
        assertThat(hasBar("60 fps", vertical = false)).isTrue()
    }

    @Test
    fun `in a left to right layout there is no bar on the trailing edge`() {
        composeRule.setContent { Specimen() }

        show(AccentChoice.White)

        for (label in listOf("Hosts", "Stream")) {
            assertWithMessage("trailing edge of $label")
                .that(hasBar(label, vertical = true, trailing = true))
                .isFalse()
        }
    }

    @Test
    fun `the bar follows a change of the accent in a screen that is already shown`() {
        composeRule.setContent { Specimen() }
        show(AccentChoice.White)
        assertThat(hasBar("Hosts", vertical = true)).isTrue()

        show(AccentChoice.Teal)

        assertThat(hasBar("Hosts", vertical = true)).isFalse()
    }

    private companion object {
        const val BAR_MIDDLE_DP = 1.5f
        const val BAR_BOTTOM_DP = 3.5f

        /** The distance, summed over the channels, within which a pixel is the colour. */
        const val PIXEL_TOLERANCE = 0.02f
    }
}
