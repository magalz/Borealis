package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.library.LibraryLaunchRequest
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.HostShortcuts
import io.github.magalz.borealis.library.domain.LegacyBridge
import io.github.magalz.borealis.library.domain.PairingInstructions
import io.github.magalz.borealis.library.domain.PairingService
import io.github.magalz.borealis.library.domain.Reachability
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
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * What the Library does with a [LibraryLaunchRequest] in the intent that starts it or reaches it
 * later: launcher shortcuts and the stream error screen send them.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class LibraryActivityRequestTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    private val hosts = FakeHostRepository()
    private val shortcuts = FakeHostShortcuts()
    private val apps = FakeAppRepository()
    private val pairing = FakePairingService()
    private val streams = FakeStreamLauncher()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @BindValue @JvmField val hostRepository: HostRepository = hosts
    @BindValue @JvmField val appRepository: AppRepository = apps
    @BindValue @JvmField val pairingService: PairingService = pairing
    @BindValue @JvmField val streamLauncher: StreamLauncher = streams
    @BindValue @JvmField val legacyBridge: LegacyBridge = FakeLegacyBridge()
    @BindValue @JvmField val hostShortcuts: HostShortcuts = shortcuts

    @Inject lateinit var resumeChoice: ResumeChoice

    private var scenario: ActivityScenario<LibraryActivity>? = null

    @Before
    fun inject() {
        hiltRule.inject()
        hosts.emit(aHost(id = "desk", name = "Desk PC"))
        apps.emit(deskApps())
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun deskApps(
        running: RunningApp? = null,
        reachability: Reachability = Reachability.ONLINE,
    ) =
        AppsSnapshot(
            host =
                aHost(
                    id = "desk",
                    name = "Desk PC",
                    reachability = reachability,
                    runningApp = running,
                ),
            apps = listOf(anApp(1, "Hades II"), anApp(2, "Steam")),
            loading = false,
        )

    private fun intentFor(request: LibraryLaunchRequest) =
        Intent(context, LibraryActivity::class.java).putExtras(request.toExtras())

    private fun launch(request: LibraryLaunchRequest) {
        scenario = ActivityScenario.launch(intentFor(request))
        composeRule.waitForIdle()
    }

    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `a Hosts request shows the host list`() {
        launch(LibraryLaunchRequest.Hosts)

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
    }

    @Test
    fun `an Apps request shows the apps of the host and Back leads to Hosts`() {
        launch(LibraryLaunchRequest.Apps("desk"))

        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
        back()
        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hades II").assertDoesNotExist()
    }

    @Test
    fun `a Pair request shows the pairing screen and Back leads to Hosts`() {
        pairing.prepared += PairingInstructions("4817", "https://192.0.2.10:47990", "Galaxy S22")

        launch(LibraryLaunchRequest.Pair("desk"))

        composeRule.onNodeWithText("Pair with this PC").assertIsDisplayed()
        back()
        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("Pair with this PC").assertDoesNotExist()
    }

    @Test
    fun `a Launch request after Try again resumes the app that runs, as the user's choice`() {
        apps.emit(deskApps(running = RunningApp(1, "Hades II")))
        resumeChoice.remember("desk", 1)

        launch(LibraryLaunchRequest.Launch("desk", 1))

        assertThat(streams.requests)
            .containsExactly(
                StreamRequest("desk", 1, "Hades II", hdrSupported = false, resumeChosen = true)
            )
    }

    @Test
    fun `a Launch request of a shortcut for the app that runs is not the user's choice`() {
        apps.emit(deskApps(running = RunningApp(1, "Hades II")))

        launch(LibraryLaunchRequest.Launch("desk", 1))

        assertThat(streams.requests)
            .containsExactly(StreamRequest("desk", 1, "Hades II", hdrSupported = false))
    }

    @Test
    fun `the choice of Try again is gone when the Library goes before it handled the request`() {
        resumeChoice.remember("desk", 1)
        launch(LibraryLaunchRequest.Hosts)

        scenario?.close()
        scenario = null

        assertThat(resumeChoice.take("desk", 1)).isFalse()
    }

    @Test
    fun `a Launch request starts the app as a tap does and Back leads to Hosts`() {
        launch(LibraryLaunchRequest.Launch("desk", 1))

        assertThat(streams.requests)
            .containsExactly(StreamRequest("desk", 1, "Hades II", hdrSupported = false))
        back()
        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
    }

    @Test
    fun `a Launch request with another app running opens the conflict sheet instead of starting`() {
        apps.emit(deskApps(running = RunningApp(2, "Steam")))

        launch(LibraryLaunchRequest.Launch("desk", 1))

        composeRule.onNodeWithText("Quit Steam and start Hades II").assertIsDisplayed()
        assertThat(streams.requests).isEmpty()
    }

    @Test
    fun `a Launch request for an app that is not in the list stays on the apps`() {
        launch(LibraryLaunchRequest.Launch("desk", 99))

        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
        assertThat(streams.requests).isEmpty()
    }

    @Test
    fun `a Launch request for an offline host returns to Hosts without a stream`() {
        apps.emit(deskApps(reachability = Reachability.OFFLINE))

        launch(LibraryLaunchRequest.Launch("desk", 1))

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        assertThat(streams.requests).isEmpty()
    }

    @Test
    fun `a request is taken once, so recreating the activity does not replay it`() {
        launch(LibraryLaunchRequest.Launch("desk", 1))
        back()

        scenario!!.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        assertThat(streams.requests).hasSize(1)
    }

    @Test
    fun `a request that reaches a running Library replaces what it shows`() {
        val controller =
            Robolectric.buildActivity(
                LibraryActivity::class.java,
                Intent(context, LibraryActivity::class.java),
            )
        controller.setup()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()

        controller.newIntent(intentFor(LibraryLaunchRequest.Apps("desk")))
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
    }

    private fun libraryOn(request: LibraryLaunchRequest) =
        Robolectric.buildActivity(LibraryActivity::class.java, intentFor(request)).setup().also {
            composeRule.waitForIdle()
        }

    @Test
    fun `a Launch that reaches the Library already showing the apps of that host starts the app`() {
        val controller = libraryOn(LibraryLaunchRequest.Apps("desk"))

        controller.newIntent(intentFor(LibraryLaunchRequest.Launch("desk", 1)))
        composeRule.waitForIdle()

        assertThat(streams.requests)
            .containsExactly(StreamRequest("desk", 1, "Hades II", hdrSupported = false))
    }

    @Test
    fun `an Apps request for the host already showing keeps the live screen`() {
        val controller = libraryOn(LibraryLaunchRequest.Apps("desk"))

        controller.newIntent(intentFor(LibraryLaunchRequest.Apps("desk")))
        composeRule.waitForIdle()

        // A new screen would start polling the app list again.
        assertThat(apps.requestedHostIds).containsExactly("desk")
        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
        controller.get().onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
    }

    @Test
    fun `a Pair request for the host already being paired keeps the live screen`() {
        pairing.prepared += PairingInstructions("4817", "https://192.0.2.10:47990", "Galaxy S22")
        pairing.prepared += PairingInstructions("5521", "https://192.0.2.10:47990", "Galaxy S22")
        val controller = libraryOn(LibraryLaunchRequest.Pair("desk"))

        controller.newIntent(intentFor(LibraryLaunchRequest.Pair("desk")))
        composeRule.waitForIdle()

        assertThat(pairing.prepareCalls).containsExactly("desk")
    }

    @Test
    fun `a Launch for another host replaces the screen that is showing`() {
        val controller = libraryOn(LibraryLaunchRequest.Apps("other"))

        controller.newIntent(intentFor(LibraryLaunchRequest.Launch("desk", 1)))
        composeRule.waitForIdle()

        assertThat(streams.requests)
            .containsExactly(StreamRequest("desk", 1, "Hades II", hdrSupported = false))
    }
}
