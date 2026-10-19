package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * When a [SettingsTwoPane] composes the pane of each group: the selected one at once, the others in
 * the frames after the first, and a group that brings its own content only when it is first shown.
 * A pane that is built stays built, and switching to it composes nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SettingsTwoPaneBuildingTest {
    @get:Rule val composeRule = createComposeRule()
    private var selected by mutableStateOf("g1")
    private var value by mutableStateOf("first value")
    private var otherValue by mutableStateOf("other value")

    /** The groups whose row was composed from nothing, in that order. */
    private val built = mutableListOf<String>()

    /** How many times the row of each group was composed, the first time included. */
    private val composedTimes = mutableMapOf<String, Int>()

    private val groups =
        listOf("g1", "g2", "g3", "g4", "own").map { SettingsGroup(it, "Group $it") }

    @Test
    fun `the selected group is composed at once and the others in the frames after the first`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeByFrame()
        assertThat(built).containsExactly("g1")

        var before = built.size
        repeat(FRAMES_TO_BUILD_ALL) {
            composeRule.mainClock.advanceTimeByFrame()
            assertThat(built.size - before).isAtMost(1)
            before = built.size
        }
        assertThat(built).containsExactly("g1", "g2", "g3", "g4").inOrder()
    }

    @Test
    fun `switching to a group that is built composes none of its rows`() {
        composeRule.setContent { Specimen() }
        composeRule.waitForIdle()
        assertThat(built).containsExactly("g1", "g2", "g3", "g4")

        composeRule.onNodeWithText("Group g3").performClick()

        composeRule.onNodeWithText("Row of g3").assertIsDisplayed()
        assertThat(built).containsExactly("g1", "g2", "g3", "g4")
    }

    @Test
    fun `a group that brings its own content is composed when first shown, then kept`() {
        composeRule.setContent { Specimen() }
        composeRule.waitForIdle()
        assertThat(built).doesNotContain("own")

        composeRule.onNodeWithText("Group own").performClick()
        composeRule.onNodeWithText("Row of own").assertIsDisplayed()
        composeRule.onNodeWithText("Group g2").performClick()
        composeRule.onNodeWithText("Group own").performClick()

        composeRule.onNodeWithText("Row of own").assertIsDisplayed()
        assertThat(built.count { it == "own" }).isEqualTo(1)
    }

    @Test
    fun `a group that is not shown has no rows in the semantics tree`() {
        composeRule.setContent { Specimen() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Row of g2").assertDoesNotExist()
        composeRule.onAllNodes(hasText("Row of", substring = true)).assertCountEquals(1)
        composeRule
            .onAllNodes(
                hasText("Row of", substring = true) and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)
            )
            .assertCountEquals(1)
    }

    @Test
    fun `a group shows the current value when it is shown again`() {
        composeRule.setContent { Specimen() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Group g2").performClick()

        value = "second value"
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Group g1").performClick()

        composeRule.onNodeWithText("second value").assertIsDisplayed()
        assertThat(built.count { it == "g1" }).isEqualTo(1)
    }

    @Test
    fun `a row is recomposed only when its own state changes, whatever pane it is in`() {
        composeRule.setContent { Specimen() }
        composeRule.waitForIdle()
        val timesBefore = composedTimes.toMap()

        value = "second value"
        composeRule.waitForIdle()

        assertThat(composedTimes.getValue("g1")).isGreaterThan(timesBefore.getValue("g1"))
        for (group in listOf("g2", "g3", "g4")) {
            assertThat(composedTimes.getValue(group)).isEqualTo(timesBefore.getValue(group))
        }
        composeRule.onNodeWithText("Group g2").performClick()
        composeRule.onNodeWithText("other value").assertIsDisplayed()
    }

    @Test
    fun `focus does not leave the pane for the rows of a group that is not shown`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Group g1").requestFocus()
        composeRule.onNodeWithText("Group g1").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Row of g1").assertIsFocused()

        composeRule.onNodeWithText("Row of g1").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithText("Row of g1").assertIsFocused()
    }

    /**
     * A row that notes when it is composed from nothing, and each time it is composed, as a row of
     * the screen is composed with the state that the screen hands down.
     */
    @Composable
    private fun BuiltRow(group: String, value: String) {
        remember { built.add(group) }
        SideEffect { composedTimes.merge(group, 1, Int::plus) }
        SettingsValueRow("Row of $group", value, {})
    }

    @Composable
    private fun Specimen() {
        // Read here, not in the rows, as the screen reads its state and hands it down.
        val handedDown = value
        val handedDownToOthers = otherValue
        BorealisTheme(FormFactor.Tv) {
            SettingsTwoPane(
                groups = groups,
                selected = selected,
                onSelect = { selected = it },
                selfScrolling = { it == "own" },
            ) { group ->
                BuiltRow(group, if (group == "g1") handedDown else handedDownToOthers)
            }
        }
    }

    private companion object {
        /** Enough frames for one group in each of them. */
        const val FRAMES_TO_BUILD_ALL = 12
    }
}
