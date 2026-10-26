package io.github.magalz.borealis.library.hosts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.domain.WakeResult
import io.github.magalz.borealis.library.testing.FakeHostRepository
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.aHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The route with the real ViewModel over a fake repository: the screen, the events and effects. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class HostsRouteTest {
    @get:Rule val composeRule = createComposeRule()

    private val repository = FakeHostRepository()
    private val opened = mutableListOf<String>()
    private val paired = mutableListOf<Pair<String, String?>>()
    private var messageShown = 0
    private val streams = mutableListOf<StreamRequest>()
    private var addHost = 0
    private var settings = 0

    private val offline =
        aHost(id = "living", name = "Living Room PC", reachability = Reachability.OFFLINE)
            .copy(canWake = true)

    @Test
    fun `the hosts of the repository show and the search message goes away`() {
        show()
        composeRule.onNodeWithText("Looking for hosts").assertIsDisplayed()

        repository.emit(aHost(id = "desk", name = "Desk PC"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("Looking for hosts").assertDoesNotExist()
    }

    @Test
    fun `a tap on a paired host opens its apps`() {
        repository.emit(aHost(id = "desk", name = "Desk PC"))
        show()

        composeRule.onNodeWithTag("host:desk").performClick()

        assertThat(opened).containsExactly("desk")
    }

    @Test
    fun `a tap on a host that is not paired opens its pairing`() {
        repository.emit(aHost(id = "studio", name = "Studio", paired = false))
        show()

        composeRule.onNodeWithTag("host:studio").performClick()

        assertThat(paired).containsExactly("studio" to "Studio")
    }

    @Test
    fun `an incoming message shows in a snackbar and is reported as shown`() {
        repository.emit(aHost())
        show(message = "New PC added. Pair it to see its apps.")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("New PC added. Pair it to see its apps.").assertIsDisplayed()
        assertThat(messageShown).isEqualTo(1)
    }

    @Test
    fun `without an incoming message nothing is reported as shown`() {
        repository.emit(aHost())
        show()

        assertThat(messageShown).isEqualTo(0)
    }

    @Test
    fun `Resume starts the running app`() {
        repository.emit(aHost(id = "desk", name = "Desk PC", runningApp = RunningApp(7, "Steam")))
        show()

        composeRule.onNodeWithTag("continue:desk").performClick()

        assertThat(streams).containsExactly(StreamRequest("desk", 7, "Steam", hdrSupported = false))
    }

    @Test
    fun `the top bar actions open Add host and Settings`() {
        repository.emit(aHost())
        show()

        composeRule.onNodeWithContentDescription("Add host").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertThat(addHost).isEqualTo(1)
        assertThat(settings).isEqualTo(1)
    }

    @Test
    fun `a tap on an offline host sends the packet and says so in a snackbar`() {
        repository.emit(offline)
        show()

        composeRule.onNodeWithTag("host:living").performClick()
        composeRule.waitForIdle()

        assertThat(repository.wokenHostIds).containsExactly("living")
        composeRule
            .onNodeWithText("Wake-up sent. The PC can take a minute to appear.")
            .assertIsDisplayed()
    }

    @Test
    fun `a failed wake says so in a snackbar`() {
        repository.wakeResult = WakeResult.FAILED
        repository.emit(offline)
        show()

        composeRule.onNodeWithTag("host:living").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Could not send the wake-up.").assertIsDisplayed()
    }

    @Test
    fun `a host that cannot wake says it is offline`() {
        repository.emit(offline.copy(canWake = false))
        show()

        composeRule.onNodeWithTag("host:living").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("This host is offline. Wake it or check that LuminalShine is running.")
            .assertIsDisplayed()
    }

    @Test
    fun `a host that was not polled yet says it is still being checked`() {
        repository.emit(offline.copy(reachability = Reachability.UNKNOWN))
        show()

        composeRule.onNodeWithTag("host:living").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Still checking this host. Try again in a moment.")
            .assertIsDisplayed()
    }

    @Test
    fun `the message of a PC that is already on`() {
        repository.wakeResult = WakeResult.ALREADY_ONLINE
        repository.emit(offline)
        show()

        composeRule.onNodeWithTag("host:living").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("The PC is already on.").assertIsDisplayed()
    }

    @Test
    fun `the message of a PC without a known network address`() {
        repository.wakeResult = WakeResult.NO_MAC_ADDRESS
        repository.emit(offline)
        show()

        composeRule.onNodeWithTag("host:living").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(
                "Borealis does not know this PC's network address yet. Pair it once while it is on."
            )
            .assertIsDisplayed()
    }

    private fun show(message: String? = null) {
        val viewModel = HostsViewModel(repository)
        composeRule.setContent {
            HostsRoute(
                onOpenApps = { opened += it },
                onOpenPairing = { id, name -> paired += id to name },
                onOpenAddHost = { addHost++ },
                onOpenSettings = { settings++ },
                onStartStream = { streams += it },
                message = message,
                onMessageShown = { messageShown++ },
                viewModel = viewModel,
            )
        }
        composeRule.waitForIdle()
    }
}
