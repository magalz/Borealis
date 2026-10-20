package io.github.magalz.borealis.designsystem.components.settings

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class CollapsibleSectionTest {
    @get:Rule val composeRule = createComposeRule()
    private val changes = mutableListOf<Boolean>()

    @Test
    fun `the header is announced as a heading and a button`() {
        composeRule.setContent { Specimen(expanded = false) }

        composeRule
            .onNodeWithText("STREAM")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `a collapsed section says Collapsed`() {
        composeRule.setContent { Specimen(expanded = false) }

        composeRule.onNodeWithText("STREAM").assert(stateDescription("Collapsed"))
    }

    @Test
    fun `an expanded section says Expanded`() {
        composeRule.setContent { Specimen(expanded = true) }

        composeRule.onNodeWithText("STREAM").assert(stateDescription("Expanded"))
    }

    @Test
    fun `a collapsed section shows no rows`() {
        composeRule.setContent { Specimen(expanded = false) }

        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
        composeRule.onNodeWithText("Frame rate").assertDoesNotExist()
    }

    @Test
    fun `an expanded section shows its rows`() {
        composeRule.setContent { Specimen(expanded = true) }

        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
        composeRule.onNodeWithText("Frame rate").assertIsDisplayed()
    }

    @Test
    fun `a tap on a collapsed header asks to open and on an open one to close`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                Column {
                    CollapsibleSection("Stream", false, { changes += it }) { Text("Resolution") }
                    CollapsibleSection("Video", true, { changes += it }) { Text("Codec") }
                }
            }
        }

        composeRule.onNodeWithText("STREAM").performClick()
        composeRule.onNodeWithText("VIDEO").performClick()

        assertThat(changes).containsExactly(true, false).inOrder()
    }

    @Test
    fun `OK and Enter on the focused header toggle it`() {
        composeRule.setContentInKeyboardMode { Specimen(expanded = false) }
        composeRule.onNodeWithText("STREAM").requestFocus()

        composeRule.onNodeWithText("STREAM").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("STREAM").performKeyInput { pressKey(Key.Enter) }

        assertThat(changes).containsExactly(true, true)
    }

    @Test
    fun `a host that keeps the state opens and closes the section with the header`() {
        composeRule.setContent { Hosted() }

        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
        composeRule.onNodeWithText("STREAM").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
        composeRule.onNodeWithText("STREAM").assert(stateDescription("Expanded"))

        composeRule.onNodeWithText("STREAM").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
        composeRule.onNodeWithText("STREAM").assert(stateDescription("Collapsed"))
    }

    @Test
    fun `the header is at least 48dp tall and as wide as its parent`() {
        composeRule.setContent { Specimen(expanded = false) }

        composeRule.onNodeWithText("STREAM").assertHeightIsAtLeast(48.dp)
        val header = composeRule.onNodeWithText("STREAM").getBoundsInRoot()
        val root = composeRule.onRoot().getBoundsInRoot()
        assertThat(header.width.value).isWithin(0.5f).of(root.width.value)
    }

    @Test
    fun `the chevron is decorative`() {
        composeRule.setContent { Specimen(expanded = true) }

        composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            )
            .assertCountEquals(0)
    }

    @Test
    fun `opening grows the height over several frames`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Hosted() }
        val collapsed = sectionHeight()

        composeRule.onNodeWithText("STREAM").performClick()
        composeRule.mainClock.advanceTimeBy(FRAME_MILLIS * 3)
        val midway = sectionHeight()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val open = sectionHeight()

        assertThat(midway.value).isGreaterThan(collapsed.value)
        assertThat(midway.value).isLessThan(open.value)
    }

    @Test
    fun `closing shrinks the height over several frames and then removes the rows`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Hosted(startOpen = true) }
        val open = sectionHeight()

        composeRule.onNodeWithText("STREAM").performClick()
        composeRule.mainClock.advanceTimeBy(FRAME_MILLIS * 3)
        val midway = sectionHeight()
        composeRule.onNodeWithText("Resolution").assertExists()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val collapsed = sectionHeight()

        assertThat(midway.value).isLessThan(open.value)
        assertThat(midway.value).isGreaterThan(collapsed.value)
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `with animations removed opening shows the rows at their full height at once`() {
        removeAnimations()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Hosted() }
        val collapsed = sectionHeight()

        composeRule.onNodeWithText("STREAM").performClick()
        composeRule.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        val atOnce = sectionHeight()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        assertThat(atOnce.value).isGreaterThan(collapsed.value)
        assertThat(atOnce.value).isEqualTo(sectionHeight().value)
    }

    @Test
    fun `with animations removed closing removes the rows at once`() {
        removeAnimations()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Hosted(startOpen = true) }

        composeRule.onNodeWithText("STREAM").performClick()
        composeRule.mainClock.advanceTimeBy(FRAME_MILLIS * 2)

        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    private fun sectionHeight(): Dp = composeRule.onNodeWithTag(SECTION).getBoundsInRoot().height

    private fun removeAnimations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    private fun stateDescription(state: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    @Composable
    private fun Specimen(expanded: Boolean) {
        BorealisTheme(FormFactor.Phone) {
            Column {
                CollapsibleSection("Stream", expanded, { changes += it }) {
                    Text("Resolution")
                    Text("Frame rate")
                }
            }
        }
    }

    @Composable
    private fun Hosted(startOpen: Boolean = false) {
        BorealisTheme(FormFactor.Phone) {
            var expanded by remember { mutableStateOf(startOpen) }
            Column {
                CollapsibleSection(
                    "Stream",
                    expanded,
                    { expanded = it },
                    Modifier.testTag(SECTION),
                ) {
                    Text("Resolution")
                    Text("Frame rate")
                }
            }
        }
    }

    private companion object {
        const val SECTION = "section"
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 2_000L
    }
}
