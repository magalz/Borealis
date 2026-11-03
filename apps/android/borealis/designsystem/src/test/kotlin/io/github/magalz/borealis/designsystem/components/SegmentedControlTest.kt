package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SegmentedControlTest {
    @get:Rule val composeRule = createComposeRule()
    private val chosen = mutableListOf<String>()

    @Test
    fun `every option is a radio button and only the selected one is selected`() {
        composeRule.setContent { Specimen(selected = "60") }

        for (option in OPTIONS) {
            composeRule
                .onNodeWithText("$option fps")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        composeRule.onNodeWithText("60 fps").assertIsSelected()
        composeRule.onNodeWithText("30 fps").assertIsNotSelected()
        composeRule.onNodeWithText("120 fps").assertIsNotSelected()
    }

    @Test
    fun `the state is on the node of each segment, which has the role and the click`() {
        composeRule.setContent { Specimen(selected = "60") }

        assertStateIsOnTheSegments("60 fps")
    }

    @Test
    fun `a segment of a control that is not a focus stop keeps its state on a phone`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                SegmentedControl(
                    OPTIONS,
                    "60",
                    { "$it fps" },
                    {},
                    groupLabel = "Frame rate",
                    focusable = false,
                )
            }
        }

        assertStateIsOnTheSegments("60 fps")
    }

    @Test
    fun `a tap selects an option`() {
        composeRule.setContent { Specimen(selected = "60") }

        composeRule.onNodeWithText("120 fps").performClick()

        assertThat(chosen).containsExactly("120")
    }

    @Test
    fun `OK selects the focused option`() {
        composeRule.setContentInKeyboardMode { Specimen(selected = "60") }
        composeRule.onNodeWithText("30 fps").requestFocus()

        composeRule.onNodeWithText("30 fps").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(chosen).containsExactly("30")
    }

    @Test
    fun `D-pad Right and Left move focus between the segments without selecting`() {
        composeRule.setContentInKeyboardMode { Specimen(selected = "60") }
        composeRule.onNodeWithText("60 fps").requestFocus()

        composeRule.onNodeWithText("60 fps").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("120 fps").assertIsFocused()

        composeRule.onNodeWithText("120 fps").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("60 fps").assertIsFocused()
        assertThat(chosen).isEmpty()
    }

    @Test
    fun `the selection follows the state the screen keeps`() {
        var current by mutableStateOf("60")
        composeRule.setContent {
            Specimen(selected = current, onSelect = { current = it })
        }

        composeRule.onNodeWithText("30 fps").performClick()

        composeRule.onNodeWithText("30 fps").assertIsSelected()
        composeRule.onNodeWithText("60 fps").assertIsNotSelected()
    }

    @Test
    fun `a disabled control does not select`() {
        composeRule.setContent { Specimen(selected = "60", enabled = false) }

        composeRule.onNodeWithText("30 fps").performClick()

        assertThat(chosen).isEmpty()
    }

    @Test
    fun `the selected segment has the accent tint`() {
        composeRule.setContent { Specimen(selected = "30") }

        // 3dp padding and the 1dp border put the first segment at 4dp from the control.
        composeRule.onNodeWithTag(FRAME).assertPixel(x = 24f + 5f, y = 24f + 5f, tintOverPage())
    }

    @Test
    fun `D-pad entry from outside lands on the selected segment`() {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(FormFactor.Tv) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Chip("Back", onClick = {})
                    SegmentedControl(OPTIONS, "60", { "$it fps" }, {})
                }
            }
        }
        composeRule.onNodeWithText("Back").requestFocus()

        composeRule.onNodeWithText("Back").performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithText("60 fps").assertIsFocused()
    }

    @Test
    fun `a request for one segment stays on it and is not redirected to the selected one`() {
        composeRule.setContentInKeyboardMode { Specimen(selected = "60") }

        composeRule.onNodeWithText("120 fps").requestFocus()

        composeRule.onNodeWithText("120 fps").assertIsFocused()
    }

    @Test
    fun `with focusable false no key reaches a segment and a tap still selects`() {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(FormFactor.Tv) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Chip("Back", onClick = {})
                    SegmentedControl(
                        OPTIONS,
                        "60",
                        { "$it fps" },
                        { chosen += it },
                        focusable = false,
                    )
                }
            }
        }
        composeRule.onNodeWithText("Back").requestFocus()

        composeRule.onNodeWithText("Back").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Back").assertIsFocused()
        composeRule.onNodeWithText("120 fps").performClick()

        assertThat(chosen).containsExactly("120")
    }

    @Test
    fun `a focused middle segment keeps the reading order of the segments`() {
        composeRule.setContentInKeyboardMode { Specimen(selected = "30") }
        composeRule.onNodeWithText("60 fps").requestFocus()

        val control =
            composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))

        // The lift draws the focused segment last, which moves it to the end of the children of
        // the control. TalkBack sorts the children of a traversal group by traversalIndex, so each
        // segment has the place that it has on the screen, and the control is the group that
        // those places are compared in. (The sort is stable: equal places keep the tree order.)
        val reading =
            control.fetchSemanticsNode().children.sortedBy {
                it.config.getOrElse(SemanticsProperties.TraversalIndex) { 0f }
            }
        assertThat(reading.map { it.config[SemanticsProperties.Text].single().text })
            .containsExactly("30 fps", "60 fps", "120 fps")
            .inOrder()
        control.assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
    }

    /**
     * A screen reader reads the state on the node of the segment, the one that has the role and the
     * click; the text inside it is a child and holds none (the Sprint 17a read took the text).
     */
    private fun assertStateIsOnTheSegments(selected: String) {
        val withState =
            composeRule
                .onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()

        assertThat(withState).hasSize(OPTIONS.size)
        for (node in withState) {
            assertThat(node.config[SemanticsProperties.Role]).isEqualTo(Role.RadioButton)
            assertThat(node.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
        }
        val states = withState.associate {
            // The label is the text of its only child.
            it.children.single().config[SemanticsProperties.Text].single().text to
                it.config[SemanticsProperties.Selected]
        }
        assertThat(states.filterValues { it }.keys).containsExactly(selected)
        assertThat(states.keys).containsExactly("30 fps", "60 fps", "120 fps")
    }

    private fun tintOverPage() =
        BorealisTokens.Color.AccentTint.compositeOver(BorealisTokens.Color.Page)

    @Composable
    private fun Specimen(
        selected: String,
        onSelect: (String) -> Unit = { chosen += it },
        enabled: Boolean = true,
    ) {
        BorealisTheme(FormFactor.Tv) {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page).padding(24.dp)) {
                SegmentedControl(
                    options = OPTIONS,
                    selected = selected,
                    label = { "$it fps" },
                    onSelect = onSelect,
                    enabled = enabled,
                )
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
        val OPTIONS = listOf("30", "60", "120")
    }
}
