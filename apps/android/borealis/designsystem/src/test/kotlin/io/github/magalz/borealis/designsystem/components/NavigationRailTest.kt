package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val TV = "w960dp-h540dp-land-television-xhdpi"

/**
 * The footer of the navigation rail: the Borealis mark, the name and the version at the bottom, for
 * a rail that has one.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class NavigationRailTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the footer is below the last item and at the bottom of the rail`() {
        composeRule.setContent { Rail(Modifier.testTag(RAIL)) }

        val last = composeRule.onNodeWithText("Settings").getUnclippedBoundsInRoot()
        val footer = footer().getUnclippedBoundsInRoot()
        val rail = composeRule.onNodeWithTag(RAIL).getUnclippedBoundsInRoot()

        assertThat(footer.top.value).isAtLeast(last.bottom.value)
        assertThat(footer.bottom.value).isAtMost(rail.bottom.value)
        assertThat(footer.bottom.value).isAtLeast(rail.bottom.value - RAIL_PADDING_DP)
    }

    @Test
    fun `the items stay where they are when the rail has a footer`() {
        var withFooter by mutableStateOf(false)
        composeRule.setContent { Rail(withFooter = withFooter) }
        val without = itemBounds()

        withFooter = true
        composeRule.waitForIdle()

        assertThat(itemBounds()).isEqualTo(without)
    }

    @Test
    fun `a screen reader reads the footer as one sentence and skips the mark`() {
        composeRule.setContent { Rail() }

        footer().assertExists()
        // The name, the version and the mark are not nodes of their own to a screen reader.
        composeRule.onNodeWithText("Borealis").assertDoesNotExist()
        composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            )
            .assertCountEquals(1)
    }

    @Test
    fun `the footer shows the name and the version`() {
        composeRule.setContent { Rail() }

        footer().assertExists()
        composeRule.onNodeWithText("Borealis", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(VERSION, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a footer without a version shows the name only`() {
        composeRule.setContent { Rail(version = null, description = "Borealis") }

        composeRule.onNodeWithContentDescription("Borealis").assertExists()
        composeRule.onNodeWithText("Borealis", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(VERSION, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the footer takes no focus, whatever it holds`() {
        composeRule.setContentInKeyboardMode { Rail(extra = { Probe() }) }

        composeRule.onNodeWithTag(PROBE).requestFocus()

        composeRule.onNodeWithTag(PROBE).assertIsNotFocused()
    }

    @Test
    fun `D-pad Down from the last item does not reach the footer`() {
        composeRule.setContentInKeyboardMode { Rail(extra = { Probe() }) }
        composeRule.onNodeWithText("Settings").requestFocus()

        composeRule.onNodeWithText("Settings").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag(PROBE).assertIsNotFocused()
        composeRule.onNode(isFocused()).assert(hasText("Settings"))
    }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = 1.3f)
    fun `at 130 percent of the font size the footer fits the rail of a handheld`() {
        composeRule.setContent {
            Rail(Modifier.testTag(RAIL), version = "0.1.0-debug", description = DESCRIPTION)
        }

        val rail = composeRule.onNodeWithTag(RAIL).getUnclippedBoundsInRoot()
        val footer = footer().getUnclippedBoundsInRoot()
        val name = composeRule.onNodeWithText("Borealis", useUnmergedTree = true)
        val version = composeRule.onNodeWithText("0.1.0-debug", useUnmergedTree = true)

        assertThat(footer.left.value).isAtLeast(rail.left.value)
        assertThat(footer.right.value).isAtMost(rail.right.value)
        assertThat(name.getUnclippedBoundsInRoot().right.value)
            .isAtMost(rail.right.value - RAIL_PADDING_DP)
        assertThat(version.getUnclippedBoundsInRoot().right.value)
            .isAtMost(rail.right.value - RAIL_PADDING_DP)
        assertThat(name.textLayout().lineCount).isEqualTo(1)
        assertThat(name.textLayout().isLineEllipsized(0)).isFalse()
    }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = 1.3f)
    fun `a version too long for the rail wraps inside the footer`() {
        composeRule.setContent {
            Rail(Modifier.testTag(RAIL), version = LONG_VERSION, description = DESCRIPTION)
        }

        val rail = composeRule.onNodeWithTag(RAIL).getUnclippedBoundsInRoot()
        val version = composeRule.onNodeWithText(LONG_VERSION, useUnmergedTree = true)

        assertThat(footerShown()).isTrue()
        assertThat(version.getUnclippedBoundsInRoot().right.value)
            .isAtMost(rail.right.value - RAIL_PADDING_DP)
        assertThat(version.textLayout().lineCount).isGreaterThan(1)
        assertThat(version.textLayout().lineCount).isAtMost(2)
    }

    @Test
    fun `a rail that is too short for the footer hides it before it overlaps the items`() {
        var height by mutableStateOf(TV_HEIGHT_DP.dp)
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) { Box(Modifier.height(height)) { RailWithFooter() } }
        }
        val items = itemBounds()
        val lastBottom = items.last().bottom.value
        val shown = mutableListOf<Boolean>()

        // From a rail with room for the items only, up to the full window, a step at a time.
        var h = lastBottom + RAIL_PADDING_DP
        while (h <= TV_HEIGHT_DP) {
            height = h.dp
            composeRule.waitForIdle()
            val isShown = footerShown()
            shown += isShown
            if (isShown) {
                assertThat(footer().getUnclippedBoundsInRoot().top.value).isAtLeast(lastBottom)
            }
            assertThat(itemBounds()).isEqualTo(items)
            h += STEP_DP
        }

        assertThat(shown.first()).isFalse()
        assertThat(shown.last()).isTrue()
        // Once there is room it stays: the footer does not come and go as the rail grows.
        assertThat(shown.dropWhile { !it }.all { it }).isTrue()
    }

    @Composable
    private fun Rail(
        modifier: Modifier = Modifier,
        withFooter: Boolean = true,
        version: String? = VERSION,
        description: String = DESCRIPTION,
        extra: @Composable () -> Unit = {},
    ) {
        BorealisTheme(FormFactor.Tv) {
            NavigationRail(
                items = ITEMS,
                selectedIndex = 0,
                onSelect = {},
                modifier = modifier,
                footer =
                    if (withFooter) {
                        {
                            RailFooter(
                                name = "Borealis",
                                version = version,
                                description = description,
                            )
                            extra()
                        }
                    } else {
                        null
                    },
            )
        }
    }

    @Composable
    private fun RailWithFooter() =
        NavigationRail(
            items = ITEMS,
            selectedIndex = 0,
            onSelect = {},
            footer = {
                RailFooter(name = "Borealis", version = VERSION, description = DESCRIPTION)
            },
        )

    @Composable
    private fun Probe() {
        Box(Modifier.size(8.dp).testTag(PROBE).focusable())
    }

    private fun footer(): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription(DESCRIPTION)

    private fun footerShown(): Boolean =
        composeRule.onAllNodesWithContentDescription(DESCRIPTION).fetchSemanticsNodes().any {
            it.layoutInfo.isPlaced
        }

    private fun itemBounds(): List<DpRect> = ITEMS.map {
        composeRule.onNodeWithText(it.label).getUnclippedBoundsInRoot()
    }

    private companion object {
        const val HANDHELD = "w800dp-h360dp-land-xhdpi"
        const val TV_HEIGHT_DP = 540f
        const val RAIL_PADDING_DP = 12f
        const val STEP_DP = 4f
        const val RAIL = "rail"
        const val PROBE = "probe"
        const val VERSION = "0.1.0"
        const val DESCRIPTION = "Borealis, version 0.1.0"
        const val LONG_VERSION = "0.1.0-debug+3a419f4e-dirty-with-a-suffix-that-is-far-too-long"

        val ICON: ImageVector =
            ImageVector.Builder(
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .path {
                    moveTo(4f, 4f)
                    lineTo(20f, 4f)
                    lineTo(20f, 20f)
                    lineTo(4f, 20f)
                    close()
                }
                .build()
        val ITEMS = listOf("Hosts", "Add host", "Settings").map { RailItem(it, ICON) }
    }
}
