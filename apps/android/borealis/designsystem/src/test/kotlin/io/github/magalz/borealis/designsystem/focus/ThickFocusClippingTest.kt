package io.github.magalz.borealis.designsystem.focus

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.components.NavigationRail
import io.github.magalz.borealis.designsystem.components.RailItem
import io.github.magalz.borealis.designsystem.components.settings.SettingsGroup
import io.github.magalz.borealis.designsystem.components.settings.SettingsTwoPane
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.testing.Edge
import io.github.magalz.borealis.designsystem.testing.colourOutside
import io.github.magalz.borealis.designsystem.testing.isNear
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The thicker focus outline (7dp from the element at its outer edge) is not cut off where the lists
 * of the design system have their first rows: the rail, and the group list and the pane of the
 * settings (ADR 0057, decision 11). The outline is read from the pixels, 6dp outside the edges of
 * the focused item, which is inside the 2dp to 7dp that it covers.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ThickFocusClippingTest {
    @get:Rule val composeRule = createComposeRule()

    private val thick = BorealisLook(thickFocus = true)
    private val outline = BorealisColors().ink

    @Test
    fun `the outline of the first and the last item of the rail is whole`() {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(FormFactor.Tv, thick) {
                NavigationRail(
                    items = listOf("Hosts", "Add host", "Settings").map { RailItem(it, ICON) },
                    selectedIndex = 0,
                    onSelect = {},
                )
            }
        }

        assertWhole("Hosts", Edge.Left, Edge.Top, Edge.Right)
        assertWhole("Settings", Edge.Left, Edge.Right)
    }

    @Test
    fun `the outline of the first group and the first row of the settings is whole`() {
        composeRule.setContentInKeyboardMode { Settings() }

        assertWhole("Stream", Edge.Left, Edge.Top, Edge.Right)
        assertWhole("Resolution", Edge.Left, Edge.Top, Edge.Right)
        assertWhole("Frame pacing", Edge.Left, Edge.Right)
    }

    private fun assertWhole(label: String, vararg edges: Edge) {
        val item = composeRule.onNodeWithText(label)
        item.requestFocus()
        composeRule.waitForIdle()
        for (edge in edges) {
            val seen = composeRule.colourOutside(item, edge, OUTSIDE_DP)
            assertWithMessage("the outline of \"$label\" 6dp past its $edge edge is $seen")
                .that(seen.isNear(outline))
                .isTrue()
        }
    }

    @Composable
    private fun Settings() {
        BorealisTheme(FormFactor.Tv, thick) {
            SettingsTwoPane(
                groups = listOf(SettingsGroup("stream", "Stream"), SettingsGroup("video", "Video")),
                selected = { "stream" },
                onSelect = {},
            ) {
                SettingsValueRow("Resolution", "1280 x 720", {})
                SettingsValueRow("Frame pacing", "Lowest latency", {})
            }
        }
    }

    private companion object {
        const val OUTSIDE_DP = 6f

        val ICON: ImageVector =
            ImageVector.Builder(
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .path { moveTo(0f, 0f) }
                .build()
    }
}
