package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
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
 * The rail of the Library on a TV stays on every screen and does not move when the screen beside it
 * changes: choosing Add host used to take the rail away at once and show the new screen late.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = TV)
class LibraryRailNavigationTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    private val hosts = FakeHostRepository()
    private val apps = FakeAppRepository()
    private val pairing = FakePairingService()

    @BindValue @JvmField val hostRepository: HostRepository = hosts
    @BindValue @JvmField val appRepository: AppRepository = apps
    @BindValue @JvmField val pairingService: PairingService = pairing
    @BindValue @JvmField val streamLauncher: StreamLauncher = FakeStreamLauncher()
    @BindValue @JvmField val legacyBridge: LegacyBridge = FakeLegacyBridge()
    @BindValue @JvmField val hostShortcuts: HostShortcuts = FakeHostShortcuts()

    private val desk = aHost(id = "desk", name = "Desk PC")
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<LibraryActivity>? = null

    @Before
    fun inject() {
        hiltRule.inject()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        hosts.emit(desk)
        apps.emit(AppsSnapshot(host = desk, apps = listOf(anApp(1, "Hades II")), loading = false))
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

    private val isRailItem = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun railItem(label: String) = hasText(label) and isRailItem

    private fun railBounds(): List<Rect> =
        listOf("Hosts", "Add host", "Settings").map {
            composeRule.onNode(railItem(it)).fetchSemanticsNode().boundsInRoot
        }

    @Test
    fun `Hosts has the rail with Hosts selected`() {
        launch()

        composeRule.onNode(railItem("Hosts")).assertIsSelected()
        composeRule.onNode(railItem("Add host")).assertIsNotSelected()
    }

    @Test
    fun `Apps has the rail with Hosts selected`() {
        launch(LibraryLaunchRequest.Apps("desk"))

        composeRule.onNode(railItem("Hosts")).assertIsSelected()
        composeRule.onNode(railItem("Add host")).assertIsNotSelected()
    }

    @Test
    fun `Add host has the rail with Add host selected`() {
        launch()

        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNode(railItem("Add host")).assertIsSelected()
        composeRule.onNode(railItem("Hosts")).assertIsNotSelected()
        composeRule.onNode(railItem("Settings")).assertIsDisplayed()
    }

    @Test
    fun `Pairing has the rail with Hosts selected`() {
        pairing.prepared += PairingInstructions("4817", "https://192.0.2.10:47990", "Galaxy S22")

        launch(LibraryLaunchRequest.Pair("desk"))

        composeRule.onNodeWithText("Pair with this PC").assertIsDisplayed()
        composeRule.onNode(railItem("Hosts")).assertIsSelected()
        composeRule.onNode(railItem("Add host")).assertIsNotSelected()
    }

    @Test
    fun `the Hosts item of the rail leads from Add host back to Hosts`() {
        launch()
        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(railItem("Hosts")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule.onNode(railItem("Hosts")).assertIsSelected()
    }

    @Test
    fun `the Hosts item of the rail leads from Pairing back to Hosts`() {
        pairing.prepared += PairingInstructions("4817", "https://192.0.2.10:47990", "Galaxy S22")
        launch(LibraryLaunchRequest.Pair("desk"))

        composeRule.onNode(railItem("Hosts")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:desk").assertIsDisplayed()
        composeRule.onNodeWithText("Pair with this PC").assertDoesNotExist()
    }

    @Test
    fun `the rail does not move when Add host opens and closes`() {
        launch()
        val onHosts = railBounds()

        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()
        val onAddHost = railBounds()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertThat(onAddHost).isEqualTo(onHosts)
        assertThat(railBounds()).isEqualTo(onHosts)
    }

    @Test
    fun `the new screen replaces the old one within a short cross-fade`() {
        launch()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.mainClock.advanceTimeBy(TRANSITION_BUDGET_MS)

        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithTag("host:desk").assertDoesNotExist()
    }

    @Test
    fun `Add host opens with the address field focused`() {
        launch()

        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(isFocused()).assert(hasSetTextAction())
    }

    @Test
    fun `D-pad Left from the Add host field reaches the rail`() {
        launch()
        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNode(isFocused()).assert(isRailItem)
    }

    @Test
    fun `Left inside an address that has text keeps focus in the field`() {
        launch()
        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(isFocused()).performTextInput("192")

        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNode(isFocused()).assert(hasSetTextAction())
    }

    @Test
    fun `choosing Add host on the rail while on Add host keeps the typed address`() {
        launch()
        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(isFocused()).performTextInput("192.168.1.20")

        composeRule.onNode(railItem("Add host")).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).assert(hasText("192.168.1.20"))
    }

    private companion object {
        const val TRANSITION_BUDGET_MS = 300L
    }
}
