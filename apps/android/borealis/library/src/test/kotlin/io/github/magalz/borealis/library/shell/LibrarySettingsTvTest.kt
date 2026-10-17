package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
import io.github.magalz.borealis.library.testing.FakeDiagnosticsExport
import io.github.magalz.borealis.library.testing.FakeHostRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.library.testing.FakeLegacyBridge
import io.github.magalz.borealis.library.testing.FakePairingService
import io.github.magalz.borealis.library.testing.FakeStreamLauncher
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.savedBundle
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Settings, Privacy and diagnostics, the Audit trail and Send to phone as Library destinations on a
 * TV (UC-009, UC-006, UC-007): the rail stays beside them with Settings selected, and Back goes
 * back one step at a time.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = TV)
class LibrarySettingsTvTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()
    @get:Rule(order = 2) val folder = TemporaryFolder()

    private val hosts = FakeHostRepository()
    private val bridge = FakeLegacyBridge()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @BindValue @JvmField val hostRepository: HostRepository = hosts
    @BindValue @JvmField val appRepository: AppRepository = FakeAppRepository()
    @BindValue @JvmField val pairingService: PairingService = FakePairingService()
    @BindValue @JvmField val streamLauncher: StreamLauncher = FakeStreamLauncher()
    @BindValue @JvmField val legacyBridge: LegacyBridge = bridge
    @BindValue @JvmField val hostShortcuts: HostShortcuts = FakeHostShortcuts()

    @Inject lateinit var export: FakeDiagnosticsExport

    private var scenario: ActivityScenario<LibraryActivity>? = null

    private val isRailItem = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun railItem(label: String) = hasText(label) and isRailItem

    @Before
    fun inject() {
        hiltRule.inject()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
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
        composeRule.onNode(railItem("Settings")).performClick()
        composeRule.waitForIdle()
    }

    private fun openPrivacyGroup() {
        composeRule.onNodeWithText("Privacy and diagnostics").performClick()
        composeRule.waitForIdle()
    }

    private fun openAudit() {
        composeRule.onNodeWithText("Audit trail").performClick()
        composeRule.waitForIdle()
    }

    private fun showsSettings() = composeRule.onNodeWithText("Resolution").assertIsDisplayed()

    private fun showsPrivacyPane() =
        composeRule.onNodeWithText("Export diagnostic bundle").assertIsDisplayed()

    private fun showsAudit() =
        composeRule.onNodeWithText("Kept for 90 days or 2,000 entries").assertIsDisplayed()

    @Test
    fun `the Settings item of the rail opens Settings and is selected`() {
        launch()

        openSettings()

        showsSettings()
        composeRule.onNode(railItem("Settings")).assertIsSelected()
        composeRule.onNode(railItem("Hosts")).assertIsNotSelected()
        composeRule.onNodeWithTag("host:desk").assertDoesNotExist()
    }

    @Test
    fun `the Privacy group shows the privacy pane`() {
        launch()
        openSettings()

        openPrivacyGroup()

        showsPrivacyPane()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `the Audit trail opens the viewer and Back returns to the Privacy group`() {
        launch()
        openSettings()
        openPrivacyGroup()

        openAudit()

        showsAudit()
        composeRule.onNode(railItem("Settings")).assertIsSelected()

        back()

        showsPrivacyPane()
        composeRule.onNode(railItem("Settings")).assertIsSelected()
    }

    @Test
    fun `Back from the Audit trail puts the focus back on the Audit trail row`() {
        launch()
        openSettings()
        openPrivacyGroup()
        // A remote moves the focus to the row and opens it with OK.
        composeRule.onNodeWithText("Audit trail").requestFocus()
        composeRule.onNodeWithText("Audit trail").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        showsAudit()

        back()

        showsPrivacyPane()
        composeRule.onNodeWithText("Audit trail").assertIsFocused()
    }

    @Test
    fun `Back from Settings returns to Hosts`() {
        launch()
        openSettings()

        back()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNode(railItem("Hosts")).assertIsSelected()
    }

    @Test
    fun `the Settings item of the rail leaves the Audit trail for Settings`() {
        launch()
        openSettings()
        openPrivacyGroup()
        openAudit()

        composeRule.onNode(railItem("Settings")).performClick()
        composeRule.waitForIdle()

        showsPrivacyPane()
    }

    @Test
    fun `a request for Privacy opens Settings on the Privacy group and Back leads to Hosts`() {
        launch(LibraryLaunchRequest.Privacy)

        showsPrivacyPane()
        composeRule.onNode(railItem("Settings")).assertIsSelected()

        back()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
    }

    @Test
    fun `Send to phone opens its dialog over Saved, and Close leaves Saved open`() {
        val saved = savedBundle(folder.root)
        export.result = saved
        launch()
        openSettings()
        openPrivacyGroup()
        composeRule.onNodeWithText("Export diagnostic bundle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Diagnostics saved").assertIsDisplayed()

        composeRule.onNodeWithText("Send to phone").performClick()
        composeRule.waitForIdle()

        // The dialog of this test device has no local network, so it shows the adb command: the
        // Saved dialog under it shows the same text.
        composeRule
            .onNodeWithText("This device is not on a local network", substring = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(saved.adbPullCommand).assertCountEquals(2)

        // The dialog of Send to phone opened last, so its Close is the last one.
        val closes = composeRule.onAllNodesWithText("Close")
        closes[closes.fetchSemanticsNodes().size - 1].performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(saved.adbPullCommand).assertCountEquals(1)
        composeRule.onNodeWithText("Diagnostics saved").assertIsDisplayed()
    }
}
