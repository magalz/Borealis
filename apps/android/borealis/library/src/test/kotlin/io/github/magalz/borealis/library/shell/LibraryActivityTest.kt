package io.github.magalz.borealis.library.shell

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.designsystem.testing.assertSnackbarIsAsWideAsItsText
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.HostShortcuts
import io.github.magalz.borealis.library.domain.LegacyBridge
import io.github.magalz.borealis.library.domain.PairingInstructions
import io.github.magalz.borealis.library.domain.PairingOutcome
import io.github.magalz.borealis.library.domain.PairingService
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.domain.StreamLauncher
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.testing.FakeAppRepository
import io.github.magalz.borealis.library.testing.FakeHostRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.library.testing.FakeLegacyBridge
import io.github.magalz.borealis.library.testing.FakePairingService
import io.github.magalz.borealis.library.testing.FakeStreamLauncher
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The whole Library through its activity: Hilt with fake repositories, the real ViewModels, the
 * navigation and the shell. A phone unless a test says otherwise.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class LibraryActivityTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<LibraryActivity>()

    private val hosts = FakeHostRepository()
    private val shortcuts = FakeHostShortcuts()
    private val apps = FakeAppRepository()
    private val pairing = FakePairingService()
    private val streams = FakeStreamLauncher()
    private val bridge = FakeLegacyBridge()

    @BindValue @JvmField val hostRepository: HostRepository = hosts
    @BindValue @JvmField val appRepository: AppRepository = apps
    @BindValue @JvmField val pairingService: PairingService = pairing
    @BindValue @JvmField val streamLauncher: StreamLauncher = streams
    @BindValue @JvmField val legacyBridge: LegacyBridge = bridge
    @BindValue @JvmField val hostShortcuts: HostShortcuts = shortcuts

    private val desk = aHost(id = "desk", name = "Desk PC")

    @Before fun inject() = hiltRule.inject()

    @Test
    fun `it starts on Hosts`() {
        hosts.emit(desk)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
    }

    @Test
    fun `opening the apps of a host publishes its launcher shortcut`() {
        hosts.emit(desk)
        apps.emit(AppsSnapshot(host = desk, apps = listOf(anApp(1, "Hades II")), loading = false))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").performClick()
        composeRule.waitForIdle()

        assertThat(shortcuts.openedHosts).containsExactly("desk" to "Desk PC")
    }

    @Test
    fun `Add host then Cancel returns to Hosts`() {
        hosts.emit(desk)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Add host").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun `a phone has no rail on Hosts or on Add host`() {
        hosts.emit(desk)
        composeRule.waitForIdle()
        val isRailItem = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        composeRule.onNode(isRailItem).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Add host").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNode(isRailItem).assertDoesNotExist()
    }

    @Test
    fun `a host that was added returns to Hosts and says so`() {
        hosts.emit(desk)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Add host").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("192.168.1.20")
        composeRule.onNode(hasText("Add host") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("New PC added. Pair it to see its apps.").assertIsDisplayed()
    }

    @Test
    fun `pairing opens the apps and Back from them returns to Hosts, not to Pairing`() {
        hosts.emit(aHost(id = "studio", name = "Studio", paired = false))
        apps.emit(
            AppsSnapshot(
                host = aHost(id = "studio", name = "Studio"),
                apps = listOf(anApp(1, "Hades II")),
                loading = false,
            )
        )
        pairing.prepared += PairingInstructions("4817", "https://192.0.2.10:47990", "Galaxy S22")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:studio").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pair with Studio").assertIsDisplayed()
        pairing.pairCalls.single().outcome.complete(PairingOutcome.Paired)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
        composeRule.onNodeWithText("Pair with Studio").assertDoesNotExist()

        back()

        composeRule.onNodeWithTag("host:studio").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hades II").assertDoesNotExist()
        composeRule.onNodeWithText("Pair with Studio").assertDoesNotExist()
    }

    @Test
    fun `Back from Add host returns to Hosts`() {
        hosts.emit(desk)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add host").performClick()
        composeRule.waitForIdle()

        back()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
    }

    @Test
    fun `Continue starts the stream of the running app`() {
        hosts.emit(aHost(id = "desk", name = "Desk PC", runningApp = RunningApp(7, "Steam")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue:desk").performClick()
        composeRule.waitForIdle()

        assertThat(streams.requests)
            .containsExactly(
                StreamRequest("desk", 7, "Steam", hdrSupported = false, resumeChosen = true)
            )
    }

    @Test
    fun `a stream that cannot start because the host is gone says so`() {
        streams.known = false
        hosts.emit(aHost(id = "desk", name = "Desk PC", runningApp = RunningApp(7, "Steam")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue:desk").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("This host is no longer available.").assertIsDisplayed()
    }

    @Test
    fun `a message of the shell is as wide as its text`() {
        streams.known = false
        hosts.emit(aHost(id = "desk", name = "Desk PC", runningApp = RunningApp(7, "Steam")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue:desk").performClick()
        composeRule.waitForIdle()

        composeRule.assertSnackbarIsAsWideAsItsText("This host is no longer available.")
    }

    @Test
    fun `an Apps screen that closes with a message shows it on Hosts`() {
        hosts.emit(desk)
        apps.emit(AppsSnapshot(host = null, apps = emptyList(), loading = false))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("This PC is no longer in your list.").assertIsDisplayed()
    }

    @Test
    fun `the launcher work runs once after the content is set and again on every resume`() {
        composeRule.waitForIdle()

        assertThat(bridge.contentSetAtCreate).containsExactly(true)
        assertThat(bridge.resumed).isEqualTo(1)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        assertThat(bridge.contentSetAtCreate).hasSize(1)
        assertThat(bridge.resumed).isEqualTo(2)
    }

    @Test
    fun `Back on Hosts finishes the activity`() {
        composeRule.waitForIdle()

        back()

        assertThat(composeRule.activity.isFinishing).isTrue()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-notnight-xhdpi")
    fun `the system bars keep light icons on the dark UI in system light mode`() {
        composeRule.waitForIdle()

        val window = composeRule.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertThat(controller.isAppearanceLightStatusBars).isFalse()
        assertThat(controller.isAppearanceLightNavigationBars).isFalse()
    }

    @Test
    fun `a message left for Hosts is shown once and not again after the activity is recreated`() {
        hosts.emit(desk)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add host").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("192.168.1.20")
        composeRule.onNode(hasText("Add host") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New PC added. Pair it to see its apps.").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("New PC added. Pair it to see its apps.").assertDoesNotExist()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }
}
