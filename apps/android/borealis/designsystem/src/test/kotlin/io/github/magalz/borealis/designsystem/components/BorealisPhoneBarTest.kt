package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BorealisPhoneBarTest {
    @get:Rule val composeRule = createComposeRule()
    private var backs = 0
    private var toggles = 0

    @Test
    fun `a tap on the arrow goes back`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithContentDescription(BACK).performClick()

        assertThat(backs).isEqualTo(1)
        assertThat(toggles).isEqualTo(0)
    }

    @Test
    fun `the arrow is announced with the description it was given`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithContentDescription(BACK).assertIsDisplayed()
    }

    @Test
    fun `the title sits after the arrow`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText(TITLE).assertIsDisplayed()
        val arrow = composeRule.onNodeWithContentDescription(BACK).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText(TITLE).getUnclippedBoundsInRoot()
        assertThat(title.left.value).isAtLeast(arrow.right.value)
    }

    @Test
    fun `the actions sit at the end of the bar and run on a tap`() {
        composeRule.setContent { Specimen(withAction = true) }

        composeRule.onNodeWithContentDescription(ACTION).performClick()

        assertThat(toggles).isEqualTo(1)
        assertThat(backs).isEqualTo(0)
        val title = composeRule.onNodeWithText(TITLE).getUnclippedBoundsInRoot()
        val action = composeRule.onNodeWithContentDescription(ACTION).getUnclippedBoundsInRoot()
        assertThat(action.left.value).isAtLeast(title.right.value)
        assertDp(action.right, 360.dp - SIDE_PADDING)
    }

    @Test
    fun `a title that is too long to fit leaves the actions in view`() {
        composeRule.setContent { Specimen(title = LONG_TITLE, withAction = true) }

        val title = composeRule.onNodeWithText(LONG_TITLE).getUnclippedBoundsInRoot()
        val action = composeRule.onNodeWithContentDescription(ACTION).getUnclippedBoundsInRoot()
        assertThat(title.right.value).isAtMost(action.left.value)
        assertDp(action.right, 360.dp - SIDE_PADDING)
    }

    @Test
    fun `the arrow lines up with the gutter of a phone`() {
        composeRule.setContent { Specimen() }

        val icon =
            composeRule
                .onNodeWithContentDescription(BACK, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        assertDp(icon.left, BorealisTokens.Space.GutterPhone)
    }

    @Test
    fun `the bar is as wide as the window`() {
        composeRule.setContent { Specimen() }

        assertDp(composeRule.onRoot().getUnclippedBoundsInRoot().width, 360.dp)
    }

    @Test
    fun `the gap spaces the title from the arrow`() {
        composeRule.setContent {
            BorealisTheme {
                Column {
                    BorealisPhoneBar(onBack = {}, backDescription = "Back plain") { Text("Plain") }
                    BorealisPhoneBar(onBack = {}, backDescription = "Back gap", gap = 4.dp) {
                        Text("Gap")
                    }
                }
            }
        }

        val plain = composeRule.onNodeWithText("Plain").getUnclippedBoundsInRoot()
        val gap = composeRule.onNodeWithText("Gap").getUnclippedBoundsInRoot()
        assertDp(gap.left - plain.left, 4.dp)
    }

    @Composable
    private fun Specimen(title: String = TITLE, withAction: Boolean = false) {
        BorealisTheme {
            BorealisPhoneBar(
                onBack = { backs++ },
                backDescription = BACK,
                actions = {
                    if (withAction) {
                        // A plain 48dp target, so the test reads its edges and not an icon
                        // button's.
                        Box(
                            Modifier.size(48.dp)
                                .semantics { contentDescription = ACTION }
                                .clickable { toggles++ }
                        )
                    }
                },
            ) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    private fun assertDp(actual: Dp, expected: Dp) {
        assertThat(actual.value).isWithin(0.5f).of(expected.value)
    }

    private companion object {
        const val BACK = "Back to Hosts"
        const val ACTION = "Show hidden apps"
        const val TITLE = "Living room PC"
        const val LONG_TITLE =
            "A host with a name so long that it cannot possibly fit in a bar of a phone, " +
                "however narrow the arrow and the actions leave it, and then some"
        val SIDE_PADDING = 4.dp
    }
}
