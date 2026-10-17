package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.library.LibraryLaunchRequest
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Settings, Privacy and diagnostics and the Audit trail as Library destinations on a phone (UC-009,
 * UC-006, UC-007): each one opens from the one before, and Back goes back one step.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class LibrarySettingsPhoneTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    private val hosts = FakeHostRepository()
    private val bridge = FakeLegacyBridge()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @BindValue @JvmField val hostRepository: HostRepository = hosts
    @BindValue @JvmField val appRepository: AppRepository = FakeAppRepository()
    @BindValue @JvmField val pairingService: PairingService = FakePairingService()
    @BindValue @JvmField val streamLauncher: StreamLauncher = FakeStreamLauncher()
    @BindValue @JvmField val legacyBridge: LegacyBridge = bridge
    @BindValue @JvmField val hostShortcuts: HostShortcuts = FakeHostShortcuts()

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

    private fun launch(request: LibraryLaunchRequest? = null) {
        val intent = Intent(context, LibraryActivity::class.java)
        if (request != null) intent.putExtras(request.toExtras())
        scenario = ActivityScenario.launch(intent)
        composeRule.waitForIdle()
    }

    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
    }

    private fun openPrivacy() {
        openSettings()
        composeRule.onNodeWithText("Privacy and diagnostics").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun openAudit() {
        composeRule.onNodeWithText("Audit trail").performClick()
        composeRule.waitForIdle()
    }

    // The title: Settings keeps its scroll position, so Resolution may be above the screen.
    private fun showsSettings() = composeRule.onNodeWithText("Settings").assertIsDisplayed()

    private fun showsPrivacy() =
        composeRule.onNodeWithText("Export diagnostic bundle").assertIsDisplayed()

    private fun showsAudit() =
        composeRule.onNodeWithText("Kept for 90 days or 2,000 entries").assertIsDisplayed()

    @Test
    fun `the gear on Hosts opens Settings`() {
        launch()

        openSettings()

        showsSettings()
        composeRule.onNodeWithTag("host:desk").assertDoesNotExist()
    }

    @Test
    fun `Privacy and diagnostics opens from Settings`() {
        launch()

        openPrivacy()

        showsPrivacy()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `the Audit trail opens from Privacy and diagnostics`() {
        launch()
        openPrivacy()

        openAudit()

        showsAudit()
        composeRule.onNodeWithText("Export diagnostic bundle").assertDoesNotExist()
    }

    @Test
    fun `Back goes back one step at a time, from the Audit trail to Hosts`() {
        launch()
        openPrivacy()
        openAudit()

        back()
        showsPrivacy()

        back()
        showsSettings()

        back()
        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `a phone has no rail on Settings`() {
        launch()

        openSettings()

        val isRailItem = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        composeRule.onNode(isRailItem).assertDoesNotExist()
    }

    @Test
    fun `a request for Privacy opens the Privacy screen and Back leads to Hosts`() {
        launch(LibraryLaunchRequest.Privacy)

        showsPrivacy()
        composeRule.onNodeWithTag("host:desk").assertDoesNotExist()

        back()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
    }

    @Test
    fun `a request for Privacy that arrives later replaces the screen that was open`() {
        val controller =
            Robolectric.buildActivity(
                LibraryActivity::class.java,
                Intent(context, LibraryActivity::class.java),
            )
        controller.setup()
        composeRule.waitForIdle()
        openSettings()

        controller.newIntent(
            Intent(context, LibraryActivity::class.java)
                .putExtras(LibraryLaunchRequest.Privacy.toExtras())
        )
        composeRule.waitForIdle()

        showsPrivacy()
        controller.get().onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
    }
}
