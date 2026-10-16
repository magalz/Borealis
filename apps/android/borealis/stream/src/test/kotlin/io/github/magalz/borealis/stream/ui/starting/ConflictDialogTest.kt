package io.github.magalz.borealis.stream.ui.starting

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.ui.ConflictDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
                "Desktop is already running on the host. " +
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
                "Desktop is already running on the host. You can join it, or quit it and start Hades II."
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an unknown running app is called another app`() {
        show(otherApp(running = null))

        composeRule
            .onNodeWithText(
                "Another app is already running on the host. You can join it, or quit it and start Hades II."
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the warning says quitting ends the stream on the other device`() {
        show(sameApp())

        composeRule
            .onNodeWithText(
                "Quitting ends the stream on the other device. Unsaved work in the app is lost."
            )
            .assertIsDisplayed()
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
    fun `Quit and relaunch answers quit and relaunch`() {
        show(otherApp())

        composeRule.onNodeWithText("Quit and relaunch").performClick()

        assertThat(intents.calls).containsExactly("answerConflict QUIT_AND_RELAUNCH")
    }

    @Test
    @Config(qualifiers = TV)
    fun `gamepad B answers cancel`() {
        show(sameApp(), FormFactor.Tv)

        composeRule.onNodeWithText("Cancel").performKeyInput { pressKey(Key.ButtonB) }

        assertThat(intents.calls).containsExactly("answerConflict CANCEL")
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
        composeRule.setChrome(formFactor) { ConflictDialog(phase, intents) }
    }
}
