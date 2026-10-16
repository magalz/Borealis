package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The connection error screen: what happened, what the host was doing and the next step. */
@RunWith(AndroidJUnit4::class)
class ConnectionErrorScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val actions = RecordingActions()

    private fun show(
        failure: StreamFailure,
        formFactor: FormFactor = FormFactor.Phone,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BorealisTheme(formFactor) {
                    ConnectionErrorScreen("Desktop", "Desk PC", failure, aSession(actions))
                }
            }
        }
    }

    private fun failure(kind: FailureKind, rawCode: String = "Error -1", portHint: String? = null) =
        StreamFailure(kind, rawCode, portHint)

    private fun assertOnly(vararg shown: String) {
        val stateActions = listOf("Wake PC", "Pair again")
        shown.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        (stateActions - shown.toSet()).forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun `a busy host says what it is probably doing and offers no state action`() {
        show(failure(FailureKind.HOST_BUSY))

        composeRule.onNodeWithText("Desk PC is busy").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "The host did not answer in time. It is probably building a display for " +
                    "another device."
            )
            .assertIsDisplayed()
        assertOnly()
    }

    @Test
    fun `an offline host offers Wake PC, which wakes the host`() {
        show(failure(FailureKind.HOST_OFFLINE))

        composeRule.onNodeWithText("Can't reach Desk PC").assertIsDisplayed()
        composeRule
            .onNodeWithText("The PC is off, asleep, or on another network.")
            .assertIsDisplayed()
        assertOnly("Wake PC")
        composeRule.onNodeWithText("Wake PC").performClick()

        assertThat(actions.calls).containsExactly("wakeHost")
    }

    @Test
    fun `a changed certificate offers Pair again, which pairs again`() {
        show(failure(FailureKind.CERTIFICATE_CHANGED))

        composeRule.onNodeWithText("Desk PC has a new certificate").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "The host identity changed since pairing. Pair again to keep streaming."
            )
            .assertIsDisplayed()
        assertOnly("Pair again")
        composeRule.onNodeWithText("Pair again").performClick()

        assertThat(actions.calls).containsExactly("pairAgain")
    }

    @Test
    fun `a generic failure names the app and offers no state action`() {
        show(failure(FailureKind.GENERIC))

        composeRule.onNodeWithText("Couldn't start Desktop").assertIsDisplayed()
        composeRule.onNodeWithText("The stream could not start or stopped.").assertIsDisplayed()
        assertOnly()
    }

    @Test
    fun `the raw code and the port hint are shown for support`() {
        show(
            failure(
                FailureKind.HOST_OFFLINE,
                "Error -1 · no answer on port 47989",
                "Ports 47984, 47989 and 48010 may be blocked",
            )
        )

        composeRule.onNodeWithText("Error -1 · no answer on port 47989").assertIsDisplayed()
        composeRule
            .onNodeWithText("Ports 47984, 47989 and 48010 may be blocked")
            .assertIsDisplayed()
    }

    @Test
    fun `without a port hint only the raw code is shown`() {
        show(failure(FailureKind.GENERIC, "Error -1 · RTSP handshake", portHint = null))

        composeRule.onNodeWithText("Error -1 · RTSP handshake").assertIsDisplayed()
        composeRule.onNodeWithText("null", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Ports", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `on a TV Try again has focus`() {
        show(failure(FailureKind.HOST_OFFLINE), FormFactor.Tv)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Try again").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w800dp-h480dp-land-hdpi")
    fun `on a handheld Try again has focus`() {
        show(failure(FailureKind.CERTIFICATE_CHANGED), FormFactor.Handheld)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Try again").assertIsFocused()
    }

    @Test
    fun `Try again retries`() {
        show(failure(FailureKind.HOST_BUSY))

        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions.calls).containsExactly("retry")
    }

    @Test
    fun `Diagnostics opens the diagnostics`() {
        show(failure(FailureKind.HOST_BUSY))

        composeRule.onNodeWithText("Diagnostics").performClick()

        assertThat(actions.calls).containsExactly("openDiagnostics")
    }

    @Test
    fun `Close leaves the stream`() {
        show(failure(FailureKind.HOST_BUSY))

        composeRule.onNodeWithText("Close").performClick()

        assertThat(actions.calls).containsExactly("leave")
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `gamepad B leaves the stream`() {
        show(failure(FailureKind.HOST_BUSY), FormFactor.Tv)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Try again").performKeyInput { pressKey(Key.ButtonB) }

        assertThat(actions.calls).containsExactly("leave")
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `a lone key up of gamepad B does nothing`() {
        show(failure(FailureKind.HOST_BUSY), FormFactor.Tv)
        composeRule.waitForIdle()

        val consumed = composeRule.onNodeWithText("Try again").performKeyPress(buttonB(ACTION_UP))

        assertThat(actions.calls).isEmpty()
        // The up of a press that began before the screen showed is still not for a button below.
        assertThat(consumed).isTrue()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `gamepad B leaves on the first key down only, and its key up and repeats do nothing more`() {
        show(failure(FailureKind.HOST_BUSY), FormFactor.Tv)
        composeRule.waitForIdle()
        val button = composeRule.onNodeWithText("Try again")

        val first = button.performKeyPress(buttonB(ACTION_DOWN))
        assertThat(actions.calls).containsExactly("leave")
        val repeat = button.performKeyPress(buttonB(ACTION_DOWN, repeat = 1))
        val up = button.performKeyPress(buttonB(ACTION_UP))

        assertThat(actions.calls).containsExactly("leave")
        assertThat(listOf(first, repeat, up)).containsExactly(true, true, true)
    }

    private fun buttonB(action: Int, repeat: Int = 0) =
        KeyEvent(
            android.view.KeyEvent(
                0L,
                0L,
                action,
                android.view.KeyEvent.KEYCODE_BUTTON_B,
                repeat,
            )
        )

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `the buttons come in order, Try again, the state action, Diagnostics, Close`() {
        show(failure(FailureKind.HOST_OFFLINE), FormFactor.Tv)

        val lefts =
            listOf("Try again", "Wake PC", "Diagnostics", "Close").map {
                composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().left
            }

        assertThat(lefts).isInStrictOrder()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `the buttons are at least 10dp apart horizontally`() {
        show(failure(FailureKind.HOST_OFFLINE), FormFactor.Tv)

        val tryAgain = composeRule.onNodeWithText("Try again").getUnclippedBoundsInRoot()
        val wake = composeRule.onNodeWithText("Wake PC").getUnclippedBoundsInRoot()

        assertThat((wake.left - tryAgain.right).value).isAtLeast(MIN_GAP)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `wrapped rows of buttons are at least 10dp apart vertically`() {
        // A TV-sized button row wraps on a narrow phone with Wake PC: Close drops to a second row.
        show(failure(FailureKind.HOST_OFFLINE), FormFactor.Tv)

        val tryAgain = composeRule.onNodeWithText("Try again").getUnclippedBoundsInRoot()
        val close = composeRule.onNodeWithText("Close").getUnclippedBoundsInRoot()

        assertThat((close.top - tryAgain.bottom).value).isAtLeast(MIN_GAP)
    }

    @Test
    @Config(qualifiers = "w640dp-h240dp-land-xhdpi")
    fun `on a short landscape phone with large text every button stays reachable`() {
        show(
            failure(FailureKind.CERTIFICATE_CHANGED, portHint = "Ports 47984 may be blocked"),
            fontScale = 2f,
        )

        composeRule.onNodeWithText("Close").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the title is a heading and the title and body announce politely`() {
        show(failure(FailureKind.HOST_BUSY))

        composeRule
            .onNodeWithText("Desk PC is busy")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
            .assertIsDisplayed()
    }

    private companion object {
        const val MIN_GAP = 10f
        const val ACTION_DOWN = android.view.KeyEvent.ACTION_DOWN
        const val ACTION_UP = android.view.KeyEvent.ACTION_UP
    }
}
