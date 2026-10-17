package io.github.magalz.borealis.stream.ui.starting

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.launch.LaunchProgressText
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.stream.LaunchRetry
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.assertWidthNear
import io.github.magalz.borealis.stream.testing.setChrome
import io.github.magalz.borealis.stream.testing.setChromeInKeyboardMode
import io.github.magalz.borealis.stream.ui.STARTING_CONTENT_TAG
import io.github.magalz.borealis.stream.ui.StartingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class StartingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val intents = RecordingIntents()
    private var phase by mutableStateOf(StreamPhase.Starting(StartStage.CONNECTING))
    private val steps =
        listOf(
            "Connecting to Gaming PC",
            "Starting Desktop",
            "Preparing the display",
            "Waiting for the first frame",
        )

    @Test
    @Config(qualifiers = PHONE)
    fun `the title names the app and the subtitle the host`() {
        show()

        composeRule.onNode(hasText("Starting Desktop") and !isStep()).assertIsDisplayed()
        composeRule.onNode(hasText("Gaming PC") and !isStep()).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the title is a heading`() {
        show()

        composeRule
            .onNode(
                hasText("Starting Desktop") and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the host busy block is a polite live region`() {
        val retry =
            LaunchRetry(LaunchCategory.PENDING, attempt = 1, maxAttempts = 4, delayMs = 1000)
        show(StreamPhase.Starting(StartStage.STARTING_APP, retry))

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the content is 560dp wide`() {
        show(formFactor = FormFactor.Tv)

        composeRule.onNodeWithTag(STARTING_CONTENT_TAG).assertWidthNear(560.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the content is 86 percent of the screen`() {
        show()

        composeRule.onNodeWithTag(STARTING_CONTENT_TAG).assertWidthNear(309.6.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `every stage marks its own step as current and the steps before it as done`() {
        show()

        for ((index, stage) in StartStage.entries.withIndex()) {
            phase = StreamPhase.Starting(stage)
            composeRule.waitForIdle()

            steps.forEachIndexed { position, text ->
                when {
                    position < index -> step(text).assertState("Done")
                    position == index -> step(text).assertState("In progress").assertIsSelected()
                    else ->
                        step(text)
                            .assert(
                                SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)
                            )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the display note shows while the app starts and the display is prepared`() {
        show()

        for (stage in listOf(StartStage.STARTING_APP, StartStage.PREPARING_DISPLAY)) {
            phase = StreamPhase.Starting(stage)
            composeRule.waitForIdle()

            composeRule.onNodeWithText(DISPLAY_NOTE).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the display note does not show while connecting or waiting for the first frame`() {
        show()

        for (stage in listOf(StartStage.CONNECTING, StartStage.FIRST_FRAME)) {
            phase = StreamPhase.Starting(stage)
            composeRule.waitForIdle()

            composeRule.onNodeWithText(DISPLAY_NOTE).assertDoesNotExist()
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `while the host asks for a retry of the server info, connecting is current and the note is hidden`() {
        val retry = LaunchRetry(null, attempt = 1, maxAttempts = 6, delayMs = 1000)
        show(StreamPhase.Starting(StartStage.CONNECTING, retry))

        step(steps[0]).assertState("In progress").assertIsSelected()
        step(steps[1]).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        composeRule.onNodeWithText("HOST BUSY").assertIsDisplayed()
        composeRule.onNodeWithText(DISPLAY_NOTE).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `without a retry no host busy line shows`() {
        show(StreamPhase.Starting(StartStage.STARTING_APP))

        composeRule.onNodeWithText("HOST BUSY").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a retry shows the host busy line with the text the spinner showed`() {
        val retry =
            LaunchRetry(LaunchCategory.PENDING, attempt = 2, maxAttempts = 4, delayMs = 2500)
        show(StreamPhase.Starting(StartStage.STARTING_APP, retry))

        val expected =
            LaunchProgressText.format(context, LaunchCategory.PENDING.ordinal, 2, 4, 2500)
        assertThat(expected).contains("Retrying in 3 s (retry 2 of 4)")
        composeRule.onNodeWithText("HOST BUSY").assertIsDisplayed()
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a retry without a category shows the generic text`() {
        val retry = LaunchRetry(null, attempt = 1, maxAttempts = 6, delayMs = 1000)
        show(StreamPhase.Starting(StartStage.STARTING_APP, retry))

        val expected = LaunchProgressText.format(context, -1, 1, 6, 1000)
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Cancel leaves`() {
        show(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(intents.calls).containsExactly("leave")
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Cancel has the focus and OK on it leaves`() {
        show(formFactor = FormFactor.Tv)

        composeRule.onNodeWithText("Cancel").assertIsFocused()
        composeRule.onNodeWithText("Cancel").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(intents.calls).containsExactly("leave")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld Cancel has the focus`() {
        show(formFactor = FormFactor.Handheld)

        composeRule.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `gamepad B leaves`() {
        show(StreamPhase.Starting(StartStage.STARTING_APP), FormFactor.Tv)

        composeRule.onNodeWithText("Cancel").performKeyInput { pressKey(Key.ButtonB) }

        assertThat(intents.calls).containsExactly("leave")
    }

    @Test
    @Config(qualifiers = TV)
    fun `another gamepad key does not leave`() {
        show(StreamPhase.Starting(StartStage.STARTING_APP), FormFactor.Tv)

        composeRule.onNodeWithText("Cancel").performKeyInput { pressKey(Key.ButtonX) }

        assertThat(intents.calls).isEmpty()
    }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun show(
        start: StreamPhase.Starting = StreamPhase.Starting(StartStage.CONNECTING),
        formFactor: FormFactor = FormFactor.Phone,
    ) {
        phase = start
        // A TV or handheld is driven by a D-pad or a gamepad, which puts the window in keyboard
        // input mode: the initial focus on Cancel is about that mode.
        if (formFactor == FormFactor.Phone) {
            composeRule.setChrome(formFactor) { Starting() }
        } else {
            composeRule.setChromeInKeyboardMode(formFactor) { Starting() }
        }
    }

    @Composable private fun Starting() = StartingScreen("Desktop", "Gaming PC", phase, intents)

    private fun step(text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and isStep())

    private fun isStep() = SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionItemInfo)

    private fun SemanticsNodeInteraction.assertState(state: String) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))

    private companion object {
        const val DISPLAY_NOTE =
            "The host builds a display for this device. This can take up to 15 s."
    }
}
