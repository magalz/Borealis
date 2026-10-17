package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What a screen reader is told about the components. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `a running cover is announced as running`() {
        composeRule.setContent { Tile(runningLabel = "Running") }

        tile().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Running"))
    }

    @Test
    fun `a hidden cover is announced as hidden`() {
        composeRule.setContent { Tile(hidden = true, hiddenLabel = "Hidden") }

        tile().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hidden"))
    }

    @Test
    fun `a running hidden cover is announced with both states`() {
        composeRule.setContent {
            Tile(runningLabel = "Running", hidden = true, hiddenLabel = "Hidden")
        }

        tile()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Running, Hidden",
                )
            )
    }

    @Test
    fun `a plain cover announces no state`() {
        composeRule.setContent { Tile() }

        tile().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `the cover options action is labelled`() {
        composeRule.setContent { Tile(optionsLabel = "App actions") }

        assertThat(longClickLabel(tile())).isEqualTo("App actions")
    }

    @Test
    fun `the host options action is labelled`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                HostCard(
                    name = "Desk PC",
                    status = DotStatus.Online,
                    statusText = "Online",
                    onClick = {},
                    onOptions = {},
                    optionsLabel = "Host options",
                    modifier = Modifier.testTag("card"),
                )
            }
        }

        assertThat(longClickLabel(composeRule.onNodeWithTag("card"))).isEqualTo("Host options")
    }

    @Test
    fun `the host click action says what OK does`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                HostCard(
                    name = "Living Room PC",
                    status = DotStatus.Offline,
                    statusText = "Offline",
                    onClick = {},
                    onClickLabel = "Wake",
                    modifier = Modifier.testTag("card"),
                )
            }
        }

        assertThat(clickLabel(composeRule.onNodeWithTag("card"))).isEqualTo("Wake")
    }

    @Test
    fun `the continue options action is labelled`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                ContinueCard(
                    appName = "Desktop",
                    detail = "Running on Desk PC",
                    resumeLabel = "Resume",
                    onClick = {},
                    onOptions = {},
                    optionsLabel = "Session options",
                    modifier = Modifier.testTag("card"),
                )
            }
        }

        assertThat(longClickLabel(composeRule.onNodeWithTag("card"))).isEqualTo("Session options")
    }

    @Test
    fun `the rail is a selectable group`() {
        composeRule.setContent { Rail() }

        composeRule
            .onNodeWithText("Home")
            .onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    }

    @Test
    fun `rail items are far enough apart for their outlines not to touch`() {
        composeRule.setContent { Rail() }

        val home = composeRule.onNodeWithText("Home").getUnclippedBoundsInRoot()
        val settings = composeRule.onNodeWithText("Settings").getUnclippedBoundsInRoot()

        // Each outline sits 5dp outside its item.
        assertThat(settings.top - home.bottom).isAtLeast(10.dp)
    }

    private fun tile(): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription("Desktop")

    private fun clickLabel(node: SemanticsNodeInteraction): String? =
        node.fetchSemanticsNode().config[SemanticsActions.OnClick].label

    private fun longClickLabel(node: SemanticsNodeInteraction): String? =
        node.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label

    @Composable
    private fun Tile(
        runningLabel: String? = null,
        hidden: Boolean = false,
        hiddenLabel: String? = null,
        optionsLabel: String? = null,
    ) {
        BorealisTheme(FormFactor.Tv) {
            CoverTile(
                name = "Desktop",
                onClick = {},
                onOptions = {},
                runningLabel = runningLabel,
                hidden = hidden,
                hiddenLabel = hiddenLabel,
                optionsLabel = optionsLabel,
            )
        }
    }

    @Composable
    private fun Rail() {
        BorealisTheme(FormFactor.Tv) {
            NavigationRail(
                items = listOf(RailItem("Home", ICON), RailItem("Settings", ICON)),
                selectedIndex = 0,
                onSelect = {},
            )
        }
    }

    private companion object {
        val ICON: ImageVector =
            ImageVector.Builder(
                    defaultWidth = 20.dp,
                    defaultHeight = 20.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                    moveTo(4f, 4f)
                    lineTo(20f, 4f)
                    lineTo(20f, 20f)
                    close()
                }
                .build()
    }
}
