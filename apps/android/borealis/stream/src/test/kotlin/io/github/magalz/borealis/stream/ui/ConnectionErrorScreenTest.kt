package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.paneTitles
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.assertWidthNear
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The connection error popup: what happened, what the host was doing and the next step, in a dialog
 * over the background of the screens outside the game or over the last frame of the stream.
 */
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
        composeRule.onNodeWithText("The stream could not start.").assertIsDisplayed()
        composeRule.onNodeWithText("stopped", substring = true).assertDoesNotExist()
        assertOnly()
    }

    @Test
    fun `a stream that ran and ended says it stopped, names the app and offers no state action`() {
        show(failure(FailureKind.STOPPED, "Error -1"))

        composeRule.onNodeWithText("Desktop stopped").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "The stream ended. The PC may have quit the app, or the connection dropped."
            )
            .assertIsDisplayed()
        composeRule.onNodeWithText("Error -1").assertIsDisplayed()
        composeRule.onNodeWithText("Couldn't start", substring = true).assertDoesNotExist()
        assertOnly()
    }

    @Test
    fun `a stopped stream still offers Try again and Close`() {
        show(failure(FailureKind.STOPPED))

        composeRule.onNodeWithText("Try again").assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun `a fixed hint of the start shows under the raw code`() {
        show(
            failure(
                FailureKind.GENERIC,
                "Error 470 · launch · HTTP 470",
                "This session wasn't started by this device, so it cannot be resumed.",
            )
        )

        composeRule.onNodeWithText("Error 470 · launch · HTTP 470").assertIsDisplayed()
        composeRule
            .onNodeWithText("This session wasn't started by this device, so it cannot be resumed.")
            .assertIsDisplayed()
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

        // The up of a press that began in the game, before the stream failed.
        composeRule.onNodeWithText("Try again").performKeyPress(buttonB(ACTION_UP))

        assertThat(actions.calls).isEmpty()
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
    fun `on a phone the buttons are stacked, in order, as the buttons of a dialog are`() {
        show(failure(FailureKind.HOST_OFFLINE))

        val tops =
            listOf("Try again", "Wake PC", "Diagnostics", "Close").map {
                composeRule.onNodeWithText(it).getUnclippedBoundsInRoot()
            }

        tops.zipWithNext().forEach { (above, below) ->
            assertThat(below.top).isAtLeast(above.bottom)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `on a phone without a state action the three buttons are still stacked`() {
        show(failure(FailureKind.HOST_BUSY))

        val buttons =
            listOf("Try again", "Diagnostics", "Close").map {
                composeRule.onNodeWithText(it).getUnclippedBoundsInRoot()
            }

        buttons.zipWithNext().forEach { (above, below) ->
            assertThat(below.top).isAtLeast(above.bottom)
        }
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `on a TV all the buttons stay in one row`() {
        show(failure(FailureKind.HOST_OFFLINE), FormFactor.Tv)

        val tops =
            listOf("Try again", "Wake PC", "Diagnostics", "Close").map {
                composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().top
            }

        assertThat(tops.toSet()).hasSize(1)
    }

    @Test
    @Config(qualifiers = "w800dp-h480dp-land-hdpi")
    fun `on a handheld all the buttons stay in one row`() {
        show(failure(FailureKind.HOST_OFFLINE), FormFactor.Handheld)

        val tops =
            listOf("Try again", "Wake PC", "Diagnostics", "Close").map {
                composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().top
            }

        assertThat(tops.toSet()).hasSize(1)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `the popup is as wide as a standard dialog, and the body wraps inside it`() {
        show(failure(FailureKind.HOST_BUSY), FormFactor.Tv)

        composeRule.onNodeWithTag(ERROR_PANEL_TAG).assertWidthNear(STANDARD_DIALOG.dp)
        val panel = composeRule.onNodeWithTag(ERROR_PANEL_TAG).getUnclippedBoundsInRoot()
        val body =
            composeRule
                .onNodeWithText(
                    "The host did not answer in time. It is probably building a display for " +
                        "another device."
                )
                .getUnclippedBoundsInRoot()

        assertThat(body.left.value).isAtLeast(panel.left.value)
        assertThat(body.right.value).isAtMost(panel.right.value)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `the popup is centred on the screen`() {
        show(failure(FailureKind.HOST_BUSY), FormFactor.Tv)

        val panel = composeRule.onNodeWithTag(ERROR_PANEL_TAG).getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()

        assertThat((panel.left + panel.right).value).isWithin(1f).of((root.left + root.right).value)
        assertThat((panel.top + panel.bottom).value).isWithin(1f).of((root.top + root.bottom).value)
    }

    @Test
    fun `the popup is announced by its title`() {
        show(failure(FailureKind.HOST_BUSY))

        assertThat(composeRule.paneTitles()).containsExactly("Desk PC is busy")
    }

    @Test
    fun `the title is the only heading of the popup`() {
        show(failure(FailureKind.HOST_BUSY, "Error -1", "Ports 47984 may be blocked"))

        // In the unmerged tree: the merged node of the announcement carries the heading of its
        // title.
        val headings =
            composeRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
                useUnmergedTree = true,
            )
        headings.assertCountEquals(1)
        headings[0].assertTextEquals("Desk PC is busy")
    }

    @Test
    fun `a tap outside the popup does nothing`() {
        show(failure(FailureKind.HOST_BUSY))

        composeRule.onRoot().performTouchInput { click(Offset(CORNER, CORNER)) }

        assertThat(actions.calls).isEmpty()
    }

    @Test
    fun `a tap in the popup that is not on a button does nothing`() {
        show(failure(FailureKind.HOST_BUSY))

        composeRule.onNodeWithTag(ERROR_PANEL_TAG).performTouchInput {
            click(Offset(CORNER, CORNER))
        }

        assertThat(actions.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `the Back key closes the popup, which leaves the stream`() {
        show(failure(FailureKind.HOST_BUSY), FormFactor.Tv)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Try again").performKeyInput { pressKey(Key.Back) }

        assertThat(actions.calls).containsExactly("leave")
    }

    @Test
    fun `the raw code and the port hint are outside the announcement of the title and the body`() {
        show(failure(FailureKind.HOST_BUSY, "Error -1 · launch", "Ports 47984 may be blocked"))

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
            .assertTextEquals(
                "Desk PC is busy",
                "The host did not answer in time. It is probably building a display for " +
                    "another device.",
            )
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
        const val CORNER = 4f
        const val STANDARD_DIALOG = 560
        const val ACTION_DOWN = android.view.KeyEvent.ACTION_DOWN
        const val ACTION_UP = android.view.KeyEvent.ACTION_UP
    }
}
