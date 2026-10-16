package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
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
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Library on a TV: the activity is launched after the device reports the leanback feature, the
 * way the form factor detection recognises a TV, so the shell draws the rail beside Hosts and Apps.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = TV)
class LibraryActivityTvTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    private val hosts = FakeHostRepository()
    private val shortcuts = FakeHostShortcuts()
    private val apps = FakeAppRepository()
    private val bridge = FakeLegacyBridge()

    @BindValue @JvmField val hostRepository: HostRepository = hosts
    @BindValue @JvmField val appRepository: AppRepository = apps
    @BindValue @JvmField val pairingService: PairingService = FakePairingService()
    @BindValue @JvmField val streamLauncher: StreamLauncher = FakeStreamLauncher()
    @BindValue @JvmField val legacyBridge: LegacyBridge = bridge
    @BindValue @JvmField val hostShortcuts: HostShortcuts = shortcuts

    private val desk = aHost(id = "desk", name = "Desk PC")
    private lateinit var scenario: ActivityScenario<LibraryActivity>

    @Before
    fun launch() {
        hiltRule.inject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        scenario = ActivityScenario.launch(LibraryActivity::class.java)
    }

    @After fun close() = scenario.close()

    @Test
    fun `on a TV the rail stays on Apps and its Hosts item returns to Hosts`() {
        hosts.emit(desk)
        apps.emit(AppsSnapshot(host = desk, apps = listOf(anApp(1, "Hades II")), loading = false))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:desk").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
        composeRule.onNode(railItem("Hosts")).assertIsDisplayed()
        composeRule.onNode(railItem("Hosts")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hades II").assertDoesNotExist()
    }

    @Test
    fun `on a TV the rail opens Add host and Settings, and Add host has no rail`() {
        hosts.emit(desk)
        composeRule.waitForIdle()

        composeRule.onNode(railItem("Settings")).performClick()
        assertThat(bridge.settingsOpened).isEqualTo(1)

        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNode(railItem("Settings")).assertDoesNotExist()
    }

    @Test
    fun `Add host from the rail of Apps closes onto Hosts`() {
        hosts.emit(desk)
        apps.emit(AppsSnapshot(host = desk, apps = listOf(anApp(1, "Hades II")), loading = false))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:desk").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hades II").assertDoesNotExist()
    }

    @Test
    fun `on a TV the D-pad moves between the rail and the content`() {
        hosts.emit(desk)
        apps.emit(AppsSnapshot(host = desk, apps = listOf(anApp(1, "Hades II")), loading = false))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:desk").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNode(isFocused()).assert(isRailItem)

        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNode(isFocused()).assert(isNotRailItem)
    }

    private val isRailItem = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val isNotRailItem =
        SemanticsMatcher("is not a rail item") {
            it.config.getOrNull(SemanticsProperties.Role) != Role.Tab
        }

    private fun railItem(label: String) = hasText(label) and isRailItem
}
