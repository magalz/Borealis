package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.designsystem.testing.assertSnackbarIsAsWideAsItsText
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.HostShortcuts
import io.github.magalz.borealis.library.domain.LegacyBridge
import io.github.magalz.borealis.library.domain.PairingService
import io.github.magalz.borealis.library.domain.StreamLauncher
import io.github.magalz.borealis.library.testing.FakeAppRepository
import io.github.magalz.borealis.library.testing.FakeHostRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.library.testing.FakeLegacyBridge
import io.github.magalz.borealis.library.testing.FakePairingService
import io.github.magalz.borealis.library.testing.FakeStreamLauncher
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.aHost
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The notice that a Quit in the stream leaves for the Library: the Library shows it with the host
 * of its shell, once, and only if it was there to show it.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class LibraryQuitNoticeTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    private val hosts = FakeHostRepository()

    @BindValue @JvmField val hostRepository: HostRepository = hosts
    @BindValue @JvmField val appRepository: AppRepository = FakeAppRepository()
    @BindValue @JvmField val pairingService: PairingService = FakePairingService()
    @BindValue @JvmField val streamLauncher: StreamLauncher = FakeStreamLauncher()
    @BindValue @JvmField val legacyBridge: LegacyBridge = FakeLegacyBridge()
    @BindValue @JvmField val hostShortcuts: HostShortcuts = FakeHostShortcuts()

    @Inject lateinit var quitNotice: QuitNotice

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<LibraryActivity>? = null

    @Before
    fun inject() {
        hiltRule.inject()
        hosts.emit(aHost(id = "desk", name = "Desk PC"))
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, LibraryActivity::class.java))
        composeRule.waitForIdle()
    }

    @Test
    fun `a quit in the stream shows App quit on the Library, as wide as its text`() {
        launch()

        quitNotice.post(LeaveNotice.Quit)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("App quit.").assertIsDisplayed()
        composeRule.assertSnackbarIsAsWideAsItsText("App quit.")
    }

    @Test
    fun `a disconnect in the stream says the app is still running, as wide as its text`() {
        launch()

        quitNotice.post(LeaveNotice.Disconnected("Desktop"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText(DISCONNECTED).assertIsDisplayed()
        composeRule.onNodeWithText("App quit.").assertDoesNotExist()
        composeRule.assertSnackbarIsAsWideAsItsText(DISCONNECTED)
    }

    @Test
    fun `a disconnect from an app the stream has no name for says the app is still running`() {
        launch()

        quitNotice.post(LeaveNotice.Disconnected(""))
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Disconnected. The app is still running on the PC.")
            .assertIsDisplayed()
    }

    @Test
    fun `the disconnect notice shows once and not again after the activity is recreated`() {
        launch()
        quitNotice.post(LeaveNotice.Disconnected("Desktop"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText(DISCONNECTED).assertIsDisplayed()

        scenario!!.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText(DISCONNECTED).assertDoesNotExist()
    }

    @Test
    fun `a disconnect notice left with no Library there is not shown by the next one`() {
        launch()
        scenario!!.close()
        scenario = null

        quitNotice.post(LeaveNotice.Disconnected("Desktop"))
        launch()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText(DISCONNECTED).assertDoesNotExist()
    }

    @Test
    fun `the notice shows once and not again after the activity is recreated`() {
        launch()
        quitNotice.post(LeaveNotice.Quit)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("App quit.").assertIsDisplayed()

        scenario!!.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("App quit.").assertDoesNotExist()
    }

    @Test
    fun `a notice left while the Library is stopped shows when it comes back`() {
        launch()
        scenario!!.moveToState(Lifecycle.State.CREATED)

        quitNotice.post(LeaveNotice.Quit)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("App quit.").assertIsDisplayed()
    }

    @Test
    fun `a notice left with no Library there is not shown by the next one`() {
        launch()
        scenario!!.close()
        scenario = null

        quitNotice.post(LeaveNotice.Quit)
        launch()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("App quit.").assertDoesNotExist()
    }

    private companion object {
        const val DISCONNECTED = "Disconnected. Desktop is still running on the PC."
    }
}
