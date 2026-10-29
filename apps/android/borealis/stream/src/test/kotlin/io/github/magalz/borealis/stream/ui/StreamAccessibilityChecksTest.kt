package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.apps.common.testing.accessibility.framework.checks.TextContrastCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import io.github.magalz.borealis.designsystem.testing.AcceptedFinding
import io.github.magalz.borealis.designsystem.testing.NoTouchOnTv
import io.github.magalz.borealis.designsystem.testing.assertAccessible
import io.github.magalz.borealis.designsystem.testing.showing
import io.github.magalz.borealis.designsystem.testing.taggedWith
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.stream.BackgroundMode
import io.github.magalz.borealis.stream.LaunchRetry
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.ui.background.OutsideGameBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Google's Accessibility Test Framework over the stream chrome: the in-game menu, the keyboard, the
 * Starting, Quitting and error screens, the conflict dialog and the hint. The game has no glass:
 * the video is behind it. The screens outside the game stand on the plain page here, as the checks
 * of the Library do: the check reads the colour behind a text from a picture, which a moving
 * background would make different at every run, and what keeps the text of a moving background at
 * 4.5:1 is the halo, which the contrast tool measures.
 */
@RunWith(AndroidJUnit4::class)
class StreamAccessibilityChecksTest {
    @get:Rule val composeRule = createComposeRule()

    private val starting = StreamPhase.Starting(StartStage.CONNECTING)

    /** The Display row is disabled until the host API exists, and a disabled row is dimmed. */
    private val disabledDisplayRow =
        AcceptedFinding(
            TextContrastCheck::class.java,
            "the Display row is disabled, so its title is dimmed to 38 percent; WCAG 1.4.3 leaves " +
                "an inactive component out of the contrast, and the note under it keeps full strength",
            showing("Display for next launch"),
        )

    /**
     * The chips of the shortcut row scroll sideways, and the last one that shows is cut by the edge
     * of the row. The check reads the contrast of the sliver that is left, which has hardly any
     * text in it; the chip itself has ink-2 text on the surface, 10:1.
     */
    private val cutChip =
        AcceptedFinding(
            TextContrastCheck::class.java,
            "the check reads the contrast of the sliver of a chip that the edge of the scrolling " +
                "row cuts off; whole, the chip is ink-2 on the surface, at 10:1",
            taggedWith("chip:"),
        )

    /**
     * Ten letters in a row of the keyboard are as wide as the screen allows, and five rows are as
     * high as a phone held sideways allows, so a key is smaller than 48dp. The System keyboard chip
     * opens the keyboard of the device, whose keys are the size of a finger.
     */
    private val smallKeys =
        AcceptedFinding(
            TouchTargetSizeCheck::class.java,
            "the keys of a full keyboard are narrower than 48dp on a phone (ten in a row) and " +
                "fit five rows into the height of a phone held sideways; the System keyboard chip " +
                "opens the keyboard of the device instead",
            taggedWith("key:"),
        )

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `the menu on a phone`() {
        show(FormFactor.Phone) { Menu() }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `the menu on a handheld`() {
        show(FormFactor.Handheld) { Menu() }

        composeRule.assertAccessible(disabledDisplayRow)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the menu on a TV`() {
        show(FormFactor.Tv) { Menu() }

        composeRule.assertAccessible(NoTouchOnTv, disabledDisplayRow)
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `the menu with Send keys and Session info open`() {
        show(FormFactor.Phone) { Menu() }
        composeRule.onNodeWithText("Send keys").performClick()
        composeRule.onNodeWithText("Session info").performClick()
        composeRule.waitForIdle()

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `the quit confirmation`() {
        show(FormFactor.Phone) { Menu() }
        composeRule.onNodeWithText("Quit Desktop").performClick()
        composeRule.waitForIdle()

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the keyboard on a phone`() {
        show(FormFactor.Phone) { StreamKeyboard(true, RecordingIntents()) }

        composeRule.assertAccessible(smallKeys, cutChip)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the keyboard on a TV`() {
        show(FormFactor.Tv) { StreamKeyboard(false, RecordingIntents()) }

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Starting screen on a phone`() {
        showOutside(FormFactor.Phone) { Starting(starting) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the Starting screen on a TV`() {
        showOutside(FormFactor.Tv) { Starting(starting) }

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Starting screen while the host is busy`() {
        val retry =
            LaunchRetry(LaunchCategory.PENDING, attempt = 1, maxAttempts = 4, delayMs = 1000)
        showOutside(FormFactor.Phone) {
            Starting(StreamPhase.Starting(StartStage.STARTING_APP, retry))
        }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Quitting screen`() {
        showOutside(FormFactor.Phone) { QuittingScreen("Desktop") }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Disconnecting screen`() {
        showOutside(FormFactor.Phone) { DisconnectingScreen("Desktop") }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the error screen on a phone`() {
        showOutside(FormFactor.Phone) { ErrorScreen(FailureKind.CERTIFICATE_CHANGED) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the error screen on a TV`() {
        showOutside(FormFactor.Tv) { ErrorScreen(FailureKind.HOST_OFFLINE) }

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the conflict dialog on a phone`() {
        showOutside(FormFactor.Phone) { Conflict() }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the conflict dialog on a TV`() {
        showOutside(FormFactor.Tv) { Conflict() }

        composeRule.assertAccessible(NoTouchOnTv)
    }

    private fun show(formFactor: FormFactor, screen: @Composable () -> Unit) {
        composeRule.setContent {
            BorealisTheme(formFactor) { Box(Modifier.fillMaxSize()) { screen() } }
        }
        composeRule.waitForIdle()
    }

    /** A screen outside the game, on the plain page that the chrome puts behind it. */
    private fun showOutside(formFactor: FormFactor, screen: @Composable () -> Unit) {
        composeRule.setContent {
            BorealisTheme(formFactor) {
                OutsideGameBackground(StreamVisuals(background = BackgroundMode.OFF), false) {
                    Box(Modifier.fillMaxSize()) { screen() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Menu() {
        StreamingLayer(
            "Desktop",
            "Gaming PC",
            aStreaming(Panel.MENU, OverlayMode.DETAILED),
            aPerf(),
            RecordingIntents(),
        )
    }

    @Composable
    private fun Starting(phase: StreamPhase.Starting) {
        StartingScreen("Desktop", "Gaming PC", phase, RecordingIntents())
    }

    @Composable
    private fun ErrorScreen(kind: FailureKind) {
        ConnectionErrorScreen(
            "Desktop",
            "Desk PC",
            StreamFailure(kind, "Error -1", portHint = null),
            aSession(RecordingActions()),
        )
    }

    @Composable
    private fun Conflict() {
        ConflictDialog(StreamPhase.Conflict("Desktop", "Hades II", false), RecordingIntents())
    }
}
