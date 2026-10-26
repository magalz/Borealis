package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.FakeInputModeManager
import org.junit.Test

/**
 * The panes of a [SettingsTwoPane] are stacked: every built pane is laid out and drawn in the same
 * bounds, and only the selected one can be seen, reached or touched. Showing another is a change of
 * which one counts, and recomposes, measures and records nothing.
 */
class SettingsTwoPaneStackTest : SettingsTwoPaneTestBase("g1") {
    /**
     * Whether the screen applies the selection that [SettingsTwoPane] asks for. A test that moves
     * focus over the groups without wanting the selection to follow turns it off.
     */
    private var applySelection = true

    private val composed = mutableMapOf<String, Int>()
    private val recomposed = mutableMapOf<String, Int>()
    private val measured = mutableMapOf<String, Int>()
    private val drawn = mutableMapOf<String, Int>()

    /** How many times the two-pane recomposed: it puts its modifier on its nodes each time. */
    private val twoPaneRecompositions = intArrayOf(0)

    /** How many times the content of the pane of each group ran, and the screen itself did. */
    private val contentRuns = mutableMapOf<String, Int>()
    private var screenRuns = 0
    private val clicks = mutableListOf<String>()
    private val focused = mutableListOf<String>()

    /** The keys that the field of a pane got. */
    private val fieldKeys = mutableListOf<Key>()

    @Test
    fun `a switch to a built pane composes, measures and draws nothing of its rows`() {
        composeRule.setContent { Specimen() }
        composeRule.waitForIdle()
        composeRule.onRoot().captureToImage()
        // The pane that is not shown was built, laid out and recorded while it waited.
        assertThat(measured["g3"] ?: 0).isGreaterThan(0)
        assertThat(drawn["g3"] ?: 0).isGreaterThan(0)
        val before = counts()

        composeRule.onNodeWithText("Group g3").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureToImage()

        composeRule.onNodeWithText("Row 1 of g3").assertIsDisplayed()
        assertThat(counts()).isEqualTo(before)
    }

    @Test
    fun `a switch recomposes neither the two-pane nor the content of a pane nor a row`() {
        composeRule.setContent { Specimen() }
        composeRule.waitForIdle()
        val before = counts()

        composeRule.onNodeWithText("Group g2").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Group g4").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Group g1").performClick()
        composeRule.waitForIdle()

        // Not the screen, not the two-pane, not the content of any pane, not a row: only the
        // layers and the two wrappers of the panes that change are touched.
        assertThat(twoPaneRecompositions[0]).isEqualTo(0)
        assertThat(screenRuns).isEqualTo(before.screenRuns)
        assertThat(contentRuns).isEqualTo(before.contentRuns)
        assertThat(recomposed).isEqualTo(before.recomposed)
    }

    @Test
    fun `the selected pane is laid out first and the others one in each frame after it`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeByFrame()
        assertThat(measured.keys).containsExactly("g1")

        var before = measured.size
        repeat(FRAMES_TO_BUILD_ALL) {
            composeRule.mainClock.advanceTimeByFrame()
            assertThat(measured.size - before).isAtMost(1)
            before = measured.size
        }
        assertThat(measured.keys).containsExactly("g1", "g2", "g3", "g4")
    }

    @Test
    fun `a pane that is not shown cannot be touched`() {
        composeRule.setContent { Specimen() }
        composeRule.waitForIdle()
        // The pane of "own" has nothing to touch, so a touch could only reach a pane beneath it.
        composeRule.onNodeWithText("Group own").performClick()
        composeRule.waitForIdle()
        val pane = composeRule.onNodeWithTag("own").fetchSemanticsNode().boundsInRoot

        composeRule.onRoot().performTouchInput {
            click(Offset(pane.left + 100f, pane.top + 60f))
        }
        composeRule.waitForIdle()

        assertThat(clicks).isEmpty()
    }

    @Test
    fun `focus never goes to a pane that is not shown`() {
        applySelection = false
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Group g1").requestFocus()
        composeRule.waitForIdle()

        repeat(TABS) {
            press(Key.Tab)
        }
        composeRule.onNodeWithText("Group g1").requestFocus()
        press(Key.DirectionRight)
        repeat(TABS) {
            press(Key.DirectionDown)
        }

        assertThat(focused).isNotEmpty()
        assertThat(focused.filterNot { it.endsWith("of g1") }).isEmpty()
    }

    @Test
    fun `Shift and Tab from the first group wrap into the selected pane, not the first`() {
        applySelection = false
        selected = "g3"
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Group g1").requestFocus()
        composeRule.waitForIdle()

        composeRule.onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Row 1 of g3").assertIsFocused()
    }

    @Test
    fun `Right right after the selection changed enters the new pane and is not undone`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Group g1").requestFocus()
        composeRule.waitForIdle()

        // The selection changes and Right arrives before the panes have recomposed, as a Right
        // right after a Down does on a device when the pane follows at once.
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { selected = "g2" }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Row 1 of g2").assertIsFocused()
        assertThat(focused).doesNotContain("1 of g1")
    }

    @Test
    fun `Tab from the last group enters the selected pane at its first row`() {
        applySelection = false
        selected = "g3"
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Group own").requestFocus()
        composeRule.waitForIdle()

        press(Key.Tab)

        composeRule.onNodeWithText("Row 1 of g3").assertIsFocused()
    }

    @Test
    fun `a pane that holds focus when another group is selected gets no more keys`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Group g1").requestFocus()
        press(Key.DirectionRight)
        composeRule.onNodeWithText("Row 1 of g1").requestFocus()

        composeRule.runOnUiThread { selected = "g2" }
        composeRule.waitForIdle()
        press(Key.Enter)

        assertThat(clicks).isEmpty()
    }

    @Test
    fun `a pane that holds focus when a tap selects another group gets no more keys`() {
        val window = FakeInputModeManager(InputMode.Keyboard)
        composeRule.setContentInKeyboardMode {
            CompositionLocalProvider(LocalInputModeManager provides window) { Specimen() }
        }
        composeRule.onNodeWithText("Group g1").requestFocus()
        press(Key.DirectionRight)
        // The field takes focus in touch mode as well, where a tap leaves the window.
        composeRule.onNodeWithTag("field of g1").requestFocus()
        press(Key.Enter)
        assertThat(fieldKeys).isNotEmpty()
        fieldKeys.clear()

        window.inputMode = InputMode.Touch
        composeRule.onNodeWithText("Group g3").performClick()
        composeRule.waitForIdle()
        press(Key.Enter)

        assertThat(fieldKeys).isEmpty()
        assertThat(clicks).isEmpty()
    }

    /** What was counted so far, to compare before and after a step. */
    private fun counts() =
        Counts(
            composed.toMap(),
            recomposed.toMap(),
            measured.toMap(),
            drawn.toMap(),
            contentRuns.toMap(),
            screenRuns,
        )

    private data class Counts(
        val composed: Map<String, Int>,
        val recomposed: Map<String, Int>,
        val measured: Map<String, Int>,
        val drawn: Map<String, Int>,
        val contentRuns: Map<String, Int>,
        val screenRuns: Int,
    )

    /** A row that counts its composition, its measures and its draws, and notes taps and focus. */
    @Composable
    private fun ProbeRow(group: String, index: Int) {
        val name = "Row $index of $group"
        remember { composed.merge(group, 1, Int::plus) }
        SideEffect { recomposed.merge(group, 1, Int::plus) }
        SettingsValueRow(
            title = name,
            value = null,
            onClick = { clicks += name },
            modifier =
                Modifier.onFocusChanged { if (it.isFocused) focused += "$index of $group" }
                    .layout { measurable, constraints ->
                        measured.merge(group, 1, Int::plus)
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .drawBehind { drawn.merge(group, 1, Int::plus) },
        )
    }

    /**
     * A row that takes focus in touch mode too, as a text field does, and notes the keys that it
     * gets. A clickable row cannot be focused in that mode, so it could not show a pane that holds
     * focus while the window is in it.
     */
    @Composable
    private fun FieldRow(group: String) {
        Box(
            Modifier.testTag("field of $group")
                .size(FIELD_SIZE)
                .onKeyEvent {
                    fieldKeys += it.key
                    false
                }
                .focusable()
        )
    }

    @Composable
    private fun Specimen() {
        SideEffect { screenRuns++ }
        TwoPane(
            NUMBERED_GROUPS,
            modifier = CountingModifier(twoPaneRecompositions),
            selfScrolling = { it == "own" },
            onSelect = { if (applySelection) selected = it },
        ) { group ->
            contentRuns.merge(group, 1, Int::plus)
            // Rows at the same places in every pane, and more of them in "g2" than in "g1".
            when (group) {
                "own" -> Box(Modifier.fillMaxSize().testTag("own"))
                "g1" -> {
                    ProbeRow(group, 1)
                    FieldRow(group)
                }
                "g2" -> repeat(MANY_ROWS) { ProbeRow(group, it + 1) }
                else -> ProbeRow(group, 1)
            }
        }
    }

    private companion object {
        const val TABS = 8
        const val MANY_ROWS = 3
        val FIELD_SIZE = 40.dp
    }
}
