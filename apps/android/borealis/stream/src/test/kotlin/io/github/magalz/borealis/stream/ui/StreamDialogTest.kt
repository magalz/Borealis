package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.assertWidthNear
import io.github.magalz.borealis.stream.testing.setChrome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The shape of a [StreamDialog] that its users choose: whether a tap on the scrim dismisses it, how
 * wide it is, what it says apart from its announcement, and where its buttons go when they do not
 * fit in one row.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class StreamDialogTest {
    @get:Rule val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun actions(count: Int) =
        LABELS.take(count).map { label ->
            StreamDialogAction(label, BorealisButtonStyle.Secondary) { calls += label }
        }

    private fun show(
        count: Int = 2,
        width: DialogWidth = DialogWidth.Compact,
        dismissOnScrimTap: Boolean = true,
        details: @Composable ColumnScope.() -> Unit = {},
    ) {
        composeRule.setChrome(FormFactor.Tv) {
            StreamDialog(
                "Title",
                { calls += "dismiss" },
                actions(count),
                Modifier.testTag(PANEL),
                width,
                dismissOnScrimTap,
                details,
            ) {
                Text("Body")
            }
        }
        composeRule.waitForIdle()
    }

    private fun tapOutside() = composeRule.onRoot().performTouchInput { click(Offset(4f, 4f)) }

    // The scrim

    @Test
    fun `a tap on the scrim dismisses the dialog, as Cancel does`() {
        show()

        tapOutside()

        assertThat(calls).containsExactly("dismiss")
    }

    @Test
    fun `a tap on the scrim does nothing where the dialog asks to ignore it`() {
        show(dismissOnScrimTap = false)

        tapOutside()

        assertThat(calls).isEmpty()
    }

    @Test
    fun `a tap in the panel is not a tap on the scrim, and its buttons still work`() {
        show(dismissOnScrimTap = false)

        composeRule.onNodeWithTag(PANEL).performTouchInput { click(Offset(4f, 4f)) }
        composeRule.onNodeWithText("Wake PC").performClick()

        assertThat(calls).containsExactly("Wake PC")
    }

    // The width and the buttons

    @Test
    fun `a Standard dialog is as wide as its token`() {
        show(width = DialogWidth.Standard)

        composeRule.onNodeWithTag(PANEL).assertWidthNear(560.dp)
    }

    @Test
    fun `a Compact dialog is as wide as its token`() {
        show(width = DialogWidth.Compact)

        composeRule.onNodeWithTag(PANEL).assertWidthNear(420.dp)
    }

    @Test
    fun `four buttons that do not fit in a row wrap to the next one, inside the panel`() {
        show(count = 4)

        val panel = composeRule.onNodeWithTag(PANEL).getUnclippedBoundsInRoot()
        val buttons = LABELS.map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot() }

        assertThat(buttons.map { it.top }.toSet().size).isGreaterThan(1)
        buttons.forEach { assertThat(it.right.value).isAtMost(panel.right.value) }
    }

    @Test
    fun `four buttons that fit in the width of the dialog stay in one row`() {
        show(count = 4, width = DialogWidth.Standard)

        val tops = LABELS.map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().top }

        assertThat(tops.toSet()).hasSize(1)
    }

    @Test
    fun `two buttons share a row, as they did`() {
        show(count = 2)

        val tops =
            LABELS.take(2).map {
                composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().top
            }

        assertThat(tops.toSet()).hasSize(1)
    }

    // What a screen reader says

    @Test
    fun `the details are shown under the body`() {
        show(details = { Text("Detail line") })

        composeRule.onNodeWithText("Detail line").assertIsDisplayed()
    }

    @Test
    fun `the announcement is the title and the body, and the details are not in it`() {
        show(details = { Text("Detail line") })
        composeRule.onNodeWithText("Detail line").assertIsDisplayed()

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
            .assertTextEquals("Title", "Body")
    }

    private companion object {
        const val PANEL = "panel"
        val LABELS = listOf("Try again", "Wake PC", "Diagnostics", "Close")
    }
}
