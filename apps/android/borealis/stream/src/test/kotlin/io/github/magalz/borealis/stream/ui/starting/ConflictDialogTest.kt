package io.github.magalz.borealis.stream.ui.starting

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.assertWidthNear
import io.github.magalz.borealis.stream.testing.setChrome
import io.github.magalz.borealis.stream.testing.setChromeInKeyboardMode
import io.github.magalz.borealis.stream.ui.CONFLICT_PANEL_TAG
import io.github.magalz.borealis.stream.ui.ConflictDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// What the warning says when the app on the other device is a game: it closes and loses progress.
// One sentence a line.
private const val GAME_WARNING =
    "Quitting ends the stream on the other device and closes the app.\n" +
        "Unsaved progress in it is lost."

@RunWith(AndroidJUnit4::class)
class ConflictDialogTest {
    @get:Rule val composeRule = createComposeRule()

    private val intents = RecordingIntents()

    @Test
    @Config(qualifiers = PHONE)
    fun `the title says an app is already running`() {
        show(sameApp())

        composeRule.onNodeWithText("An app is already running").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the same app offers to join it or quit it and start it again`() {
        show(sameApp())

        composeRule
            .onNodeWithText(
                "Desktop is already running on this host. " +
                    "You can join it, or quit it and start it again."
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `another app offers to quit it and start the requested one`() {
        show(otherApp())

        composeRule
            .onNodeWithText(
                "Desktop is already running on this host. You can join it, or quit it and start Hades II."
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an unknown running app is called another app`() {
        show(otherApp(running = null))

        composeRule
            .onNodeWithText(
                "Another app is already running on this host. You can join it, or quit it and start Hades II."
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the warning for Desktop says quitting ends the stream on the other device and no more`() {
        show(sameApp())

        composeRule
            .onNodeWithText("Quitting ends the stream on the other device.")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Unsaved", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("lost", substring = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the warning for Desktop is the same when another app was requested`() {
        show(otherApp())

        composeRule
            .onNodeWithText("Quitting ends the stream on the other device.")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a Desktop with a different case or spaces is still Desktop`() {
        show(sameApp().copy(runningAppName = " desktop "))

        composeRule
            .onNodeWithText("Quitting ends the stream on the other device.")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the warning for a game says quitting closes it and its unsaved progress is lost`() {
        show(otherApp(running = "Steam Big Picture"))

        composeRule.onNodeWithText(GAME_WARNING).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the warning for an unknown running app says its unsaved progress is lost`() {
        show(otherApp(running = null))

        composeRule.onNodeWithText(GAME_WARNING).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an app whose title only contains Desktop is a game`() {
        show(otherApp(running = "Remote Desktop"))

        composeRule.onNodeWithText(GAME_WARNING).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Cancel has the focus when the dialog opens and OK on it cancels`() {
        show(sameApp(), FormFactor.Tv)

        composeRule.onNodeWithText("Cancel").assertIsFocused()
        composeRule.onNodeWithText("Cancel").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(intents.calls).containsExactly("answerConflict CANCEL")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Cancel has the focus on a handheld`() {
        show(otherApp(), FormFactor.Handheld)

        composeRule.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Cancel answers cancel`() {
        show(sameApp())

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(intents.calls).containsExactly("answerConflict CANCEL")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Join answers join`() {
        show(sameApp())

        composeRule.onNodeWithText("Join").performClick()

        assertThat(intents.calls).containsExactly("answerConflict JOIN")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the same app names the third button Quit and relaunch and it answers quit`() {
        show(sameApp())

        composeRule.onNodeWithText("Quit and start").assertDoesNotExist()
        composeRule.onNodeWithText("Quit and relaunch").performClick()

        assertThat(intents.calls).containsExactly("answerConflict QUIT_AND_RELAUNCH")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `another app names the third button Quit and start and it answers quit`() {
        show(otherApp())

        composeRule.onNodeWithText("Quit and relaunch").assertDoesNotExist()
        composeRule.onNodeWithText("Quit and start").performClick()

        assertThat(intents.calls).containsExactly("answerConflict QUIT_AND_RELAUNCH")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an unknown running app names the third button Quit and start`() {
        show(otherApp(running = null))

        composeRule.onNodeWithText("Quit and start").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the same app with an unknown name also names the third button Quit and start`() {
        show(sameApp().copy(runningAppName = null))

        composeRule.onNodeWithText("Quit and start").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `gamepad B answers cancel`() {
        show(sameApp(), FormFactor.Tv)

        composeRule.onNodeWithText("Cancel").performKeyInput { pressKey(Key.ButtonB) }

        assertThat(intents.calls).containsExactly("answerConflict CANCEL")
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back with the focus in the dialog answers cancel`() {
        show(sameApp(), FormFactor.Tv)

        composeRule.onNodeWithText("Cancel").performKeyInput { pressKey(Key.Back) }

        assertThat(intents.calls).containsExactly("answerConflict CANCEL")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on the scrim answers cancel, as Cancel does`() {
        show(sameApp())

        composeRule.onRoot().performTouchInput { click(Offset(10f, 10f)) }

        assertThat(intents.calls).containsExactly("answerConflict CANCEL")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap inside the panel does not answer`() {
        show(sameApp())

        composeRule.onNodeWithText("An app is already running").performClick()

        assertThat(intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the title is a heading`() {
        show(sameApp())

        // The unmerged tree: the title and the body are read together, but only the title is a
        // heading.
        composeRule
            .onNode(
                hasText("An app is already running") and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the dialog announces its body and its warning with its title when it opens`() {
        show(sameApp())

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .assert(
                hasText("An app is already running") and
                    hasText(
                        "Desktop is already running on this host. " +
                            "You can join it, or quit it and start it again."
                    ) and
                    hasText("Quitting ends the stream on the other device.")
            )
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the panel is 420dp wide`() {
        show(sameApp(), FormFactor.Tv)

        composeRule.onNodeWithTag(CONFLICT_PANEL_TAG).assertWidthNear(420.dp)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the panel is 420dp wide`() {
        show(sameApp(), FormFactor.Handheld)

        composeRule.onNodeWithTag(CONFLICT_PANEL_TAG).assertWidthNear(420.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the panel is the window less 16dp on each side`() {
        show(sameApp())

        composeRule.onNodeWithTag(CONFLICT_PANEL_TAG).assertWidthNear(328.dp)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the padding is 24dp on every side, as in the other dialogs`() {
        show(sameApp(), FormFactor.Tv)

        val panel = composeRule.onNodeWithTag(CONFLICT_PANEL_TAG).getUnclippedBoundsInRoot()
        val title =
            composeRule.onNodeWithText("An app is already running").getUnclippedBoundsInRoot()
        val cancel = composeRule.onNodeWithText("Cancel").getUnclippedBoundsInRoot()
        assertThat(title.left - panel.left).isEqualTo(24.dp)
        assertThat(title.top - panel.top).isEqualTo(24.dp)
        assertThat(panel.bottom - cancel.bottom).isEqualTo(24.dp)
    }

    private fun sameApp() =
        StreamPhase.Conflict(
            runningAppName = "Desktop",
            requestedAppName = "Desktop",
            sameApp = true,
        )

    private fun otherApp(running: String? = "Desktop") =
        StreamPhase.Conflict(
            runningAppName = running,
            requestedAppName = "Hades II",
            sameApp = false,
        )

    private fun show(phase: StreamPhase.Conflict, formFactor: FormFactor = FormFactor.Phone) {
        // A TV or handheld is driven by a D-pad or a gamepad, which puts the window in keyboard
        // input mode: UC-003-T1 is about the focus in that mode.
        if (formFactor == FormFactor.Phone) {
            composeRule.setChrome(formFactor) { ConflictDialog(phase, intents) }
        } else {
            composeRule.setChromeInKeyboardMode(formFactor) { ConflictDialog(phase, intents) }
        }
    }
}
