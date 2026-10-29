package io.github.magalz.borealis.library.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.testing.FakeAppRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The route wires the ViewModel to the screen: effects become streams, messages and closing. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AppsRouteTest {
    @get:Rule val composeRule = createComposeRule()
    private val repository = FakeAppRepository()
    private val shortcuts = FakeHostShortcuts()
    private val streams = mutableListOf<StreamRequest>()
    private val closes = mutableListOf<String?>()

    private fun snapshot(
        running: Boolean = true,
        reachability: Reachability = Reachability.ONLINE,
        paired: Boolean = true,
        known: Boolean = true,
    ) =
        AppsSnapshot(
            host =
                if (known) {
                    aHost(
                        id = "host-1",
                        name = "Desk PC",
                        reachability = reachability,
                        paired = paired,
                        runningApp = if (running) RunningApp(1, "Desktop") else null,
                    )
                } else {
                    null
                },
            apps = listOf(anApp(1, "Desktop"), anApp(2, "Hades II", hdrSupported = true)),
            loading = false,
        )

    private fun show(snapshot: AppsSnapshot = snapshot()) {
        repository.emit(snapshot)
        val viewModel = AppsViewModel("host-1", repository, shortcuts)
        composeRule.setContent {
            AppsRoute(
                hostId = "host-1",
                onClose = { closes += it },
                onStartStream = { streams += it },
                viewModel = viewModel,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the route shows the apps of the host`() {
        show()

        composeRule.onNodeWithText("Desk PC").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Desktop").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
    }

    @Test
    fun `selecting the running app starts its stream`() {
        show()

        composeRule.onNodeWithContentDescription("Desktop").performClick()
        composeRule.waitForIdle()

        assertThat(streams)
            .containsExactly(StreamRequest("host-1", 1, "Desktop", false, resumeChosen = true))
    }

    @Test
    fun `starting an app when nothing runs starts its stream`() {
        show(snapshot(running = false))

        composeRule.onNodeWithContentDescription("Hades II").performClick()
        composeRule.waitForIdle()

        assertThat(streams).containsExactly(StreamRequest("host-1", 2, "Hades II", true))
    }

    @Test
    fun `an unknown host closes the screen with a message`() {
        show(snapshot(known = false))

        assertThat(closes).containsExactly("This PC is no longer in your list.")
    }

    @Test
    fun `a host that is not paired closes the screen with a message`() {
        show(snapshot(paired = false))

        assertThat(closes).containsExactly("This PC is not paired. Pair it first.")
    }

    @Test
    fun `an offline host closes the screen with a message`() {
        show(snapshot(reachability = Reachability.OFFLINE))

        assertThat(closes).containsExactly("This PC is offline.")
    }

    @Test
    fun `a quit that worked shows a message and stays on the screen`() {
        show()

        quitDesktop()

        composeRule.onNodeWithText("App quit.").assertIsDisplayed()
        assertThat(repository.quitCalls).isEqualTo(1)
        assertThat(closes).isEmpty()
    }

    @Test
    fun `a quit that failed shows the text of the adapter`() {
        repository.quitResult = QuitResult.Failed("The host refused.")
        show()

        quitDesktop()

        composeRule.onNodeWithText("The host refused.").assertIsDisplayed()
    }

    @Test
    fun `a quit that failed without a text shows the generic message`() {
        repository.quitResult = QuitResult.Failed(null)
        show()

        quitDesktop()

        composeRule.onNodeWithText("Failed to quit.").assertIsDisplayed()
    }

    @Test
    fun `a message that is still showing does not hold back the next effect`() {
        repository.quitResult = QuitResult.Failed("The host refused.")
        show()
        quitDesktop()
        composeRule.onNodeWithText("The host refused.").assertIsDisplayed()
        // The snackbar must still be up when the next effect arrives: stop its timeout.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("Desktop").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("The host refused.").assertIsDisplayed()
        assertThat(streams)
            .containsExactly(StreamRequest("host-1", 1, "Desktop", false, resumeChosen = true))
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `the back arrow closes the screen without a message`() {
        show()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(closes).containsExactly(null)
    }

    private fun quitDesktop() {
        composeRule.onNodeWithContentDescription("Desktop").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Quit Desktop").performClick()
        composeRule.onNodeWithText("Quit").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `adding an app to the home screen pins it and says so when the launcher refuses`() {
        show(snapshot(running = false))
        composeRule.onNodeWithContentDescription("Hades II").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Add to home screen").performClick()
        composeRule.waitForIdle()

        assertThat(shortcuts.pinned)
            .containsExactly(
                FakeHostShortcuts.PinCall("host-1", "Desk PC", 2, "Hades II", hdrSupported = true)
            )
        assertThat(streams).isEmpty()

        shortcuts.pinResult = false
        composeRule.onNodeWithContentDescription("Hades II").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add to home screen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Your launcher did not add the shortcut.").assertIsDisplayed()
    }
}
