package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.HostShortcuts
import io.github.magalz.borealis.library.domain.LegacyBridge
import io.github.magalz.borealis.library.domain.PairingInstructions
import io.github.magalz.borealis.library.domain.PairingOutcome
import io.github.magalz.borealis.library.domain.PairingService
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Where focus goes when Back leaves the Apps screen of a host: onto that host's card, on a TV and
 * on a handheld. Hosts leaves the composition while Apps is on top, so the focus of the card is not
 * kept by anything but the screen itself.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class LibraryAppsBackFocusTest {
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

    // Sorted by name, so the second card is the one of the Living Room PC.
    private val aardvark = aHost(id = "aard", name = "Aardvark")
    private val living = aHost(id = "living", name = "Living Room PC")
    private val studio = aHost(id = "studio", name = "Studio")
    private var scenario: ActivityScenario<LibraryActivity>? = null

    @After fun close() = scenario?.close() ?: Unit

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Back from Apps focuses the card of the second host`() {
        launch(tv = true)
        openLivingApps()

        back()

        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld Back from Apps focuses the card of the second host`() {
        launch(tv = false)
        openLivingApps()

        back()

        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Back from Apps focuses the host card, not a Continue card above it`() {
        launch(tv = true)
        val running = living.copy(runningApp = RunningApp(1, "Desktop"))
        openLivingApps(running, aardvark, studio)

        back()

        composeRule.onNodeWithTag("continue:living").assertIsDisplayed()
        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the card keeps the focus when the host went offline and was checked meanwhile`() {
        launch(tv = true)
        openLivingApps()

        hosts.emit(aardvark, living.copy(reachability = Reachability.OFFLINE), studio)
        hosts.emit(aardvark, living.copy(reachability = Reachability.UNKNOWN), studio)
        hosts.emit(aardvark, living, studio)
        back()

        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the card keeps the focus when the host went offline meanwhile`() {
        launch(tv = false)
        openLivingApps()

        hosts.emit(aardvark, living.copy(reachability = Reachability.OFFLINE), studio)
        back()

        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Back from Apps focuses the card where the refreshed list put it`() {
        launch(tv = true)
        openLivingApps()

        hosts.emit(aHost(id = "abacus", name = "Abacus"), aardvark, living, studio)
        back()

        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld Back from Apps focuses the card where the refreshed list put it`() {
        launch(tv = false)
        openLivingApps()

        hosts.emit(aHost(id = "abacus", name = "Abacus"), aardvark, living, studio)
        back()

        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Back from Apps focuses the first card when the host is gone`() {
        launch(tv = true)
        openLivingApps()

        hosts.emit(aardvark, studio)
        back()

        composeRule.onNodeWithTag("host:aard").assertIsFocused()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld Back from Apps focuses the first card when the host is gone`() {
        launch(tv = false)
        openLivingApps()

        hosts.emit(aardvark, studio)
        back()

        composeRule.onNodeWithTag("host:aard").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the first card still has the focus when Hosts opens`() {
        launch(tv = true)
        hosts.emit(aardvark, living, studio)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:aard").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Cancel on Add host focuses the card that had the focus before`() {
        launch(tv = true)
        hosts.emit(aardvark, living, studio)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:studio").requestFocus()

        composeRule.onNode(hasText("Add host") and hasRole(Role.Tab)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:studio").assertIsFocused()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld Back from Apps focuses the host card, not a Continue card above it`() {
        launch(tv = false)
        val running = living.copy(runningApp = RunningApp(1, "Desktop"))
        openLivingApps(running, aardvark, studio)

        back()

        composeRule.onNodeWithTag("continue:living").assertIsDisplayed()
        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld a tap on a card is enough for Back from Apps to focus it`() {
        launch(tv = false)
        hosts.emit(aardvark, living, studio)
        apps.emit(AppsSnapshot(host = living, apps = listOf(anApp(1, "Hades II")), loading = false))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:living").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
        back()

        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV pairing, then Apps, then Back focuses the card of the host that was paired`() {
        launch(tv = true)
        hosts.emit(aardvark, living.copy(paired = false), studio)
        apps.emit(AppsSnapshot(host = living, apps = listOf(anApp(1, "Hades II")), loading = false))
        pairing.prepared += PairingInstructions("4817", "https://192.0.2.10:47990", "Galaxy S22")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:living").requestFocus()

        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pair with Living Room PC").assertIsDisplayed()
        pairing.pairCalls.single().outcome.complete(PairingOutcome.Paired)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
        back()

        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the card that had the focus has it again after the activity is recreated`() {
        launch(tv = true)
        hosts.emit(aardvark, living, studio)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:studio").requestFocus()

        scenario!!.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:studio").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Cancel on Add host focuses the Continue card that had the focus before`() {
        launch(tv = true)
        hosts.emit(
            aardvark.copy(runningApp = RunningApp(1, "Desktop")),
            living.copy(runningApp = RunningApp(2, "Hades II")),
            studio,
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("continue:living").requestFocus()

        composeRule.onNode(hasText("Add host") and hasRole(Role.Tab)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue:living").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Cancel on Add host focuses a card that the refreshed list moved out of view`() {
        launch(tv = true)
        val many = longList()
        val far = many[LONG_LIST - 5]
        hosts.emit(*many.toTypedArray())
        composeRule.waitForIdle()
        composeRule
            .onNode(hasScrollToNodeAction())
            .performScrollToNode(hasTestTag("host:${far.id}"))
        composeRule.onNodeWithTag("host:${far.id}").requestFocus()

        composeRule.onNode(hasText("Add host") and hasRole(Role.Tab)).performClick()
        composeRule.waitForIdle()
        // The host now sorts first, far from the scroll position Hosts comes back to.
        hosts.emit(far.copy(name = "Aardvark"), *many.filterNot { it == far }.toTypedArray())
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:${far.id}").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Back from Apps focuses a card that the refreshed list moved out of view`() {
        launch(tv = true)
        val many = longList()
        val opened = many[LONG_LIST - 5]
        hosts.emit(*many.toTypedArray())
        apps.emit(AppsSnapshot(host = opened, apps = listOf(anApp(1, "Hades II")), loading = false))
        composeRule.waitForIdle()
        composeRule
            .onNode(hasScrollToNodeAction())
            .performScrollToNode(hasTestTag("host:${opened.id}"))
        composeRule.onNodeWithTag("host:${opened.id}").requestFocus()
        composeRule.onNodeWithTag("host:${opened.id}").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()

        // The host now sorts first, far from the scroll position Hosts comes back to.
        hosts.emit(opened.copy(name = "Aardvark"), *many.filterNot { it == opened }.toTypedArray())
        back()

        composeRule.onNodeWithTag("host:${opened.id}").assertIsFocused()
    }

    /**
     * The activity as the form factor detection sees a TV, or a wide window without a touch screen.
     */
    private fun launch(tv: Boolean) {
        hiltRule.inject()
        if (tv) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        }
        scenario = ActivityScenario.launch(LibraryActivity::class.java)
    }

    /** Puts [shown] on Hosts and opens the Apps of the Living Room PC with OK, from its card. */
    private fun openLivingApps(vararg shown: Host) {
        val list = shown.ifEmpty { arrayOf(aardvark, living, studio) }
        hosts.emit(*list)
        apps.emit(AppsSnapshot(host = living, apps = listOf(anApp(1, "Hades II")), loading = false))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:living").requestFocus()
        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Hades II").assertIsDisplayed()
        composeRule.onNodeWithTag("host:living").assertDoesNotExist()
    }

    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertThat(focusedTag()).isNotNull()
    }

    private fun longList() =
        (0 until LONG_LIST).map { aHost(id = "h${it.padded()}", name = "Host ${it.padded()}") }

    private fun Int.padded() = toString().padStart(2, '0')

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun focusedTag(): String? =
        composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().firstNotNullOfOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag)
        }

    private companion object {
        /** A wide window with no touch screen is a handheld. */
        const val HANDHELD = "w800dp-h480dp-land-hdpi-notouch"

        /** Hosts enough for the grid to scroll. */
        const val LONG_LIST = 30
    }
}
