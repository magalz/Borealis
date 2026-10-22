package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Library as the user starts it: the Card density that the settings store is the density of its
 * grids, and a change of the setting lays Hosts and Apps out again at once (ADR 0058, decision 7).
 * The activity stays the same one, the hosts are not collected again, and the card that had focus
 * keeps it.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = TV)
class LibraryActivityCardDensityTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    private val hosts = FakeHostRepository()
    private val apps = FakeAppRepository()

    @BindValue @JvmField val hostRepository: HostRepository = hosts
    @BindValue @JvmField val appRepository: AppRepository = apps
    @BindValue @JvmField val pairingService: PairingService = FakePairingService()
    @BindValue @JvmField val streamLauncher: StreamLauncher = FakeStreamLauncher()
    @BindValue @JvmField val legacyBridge: LegacyBridge = FakeLegacyBridge()
    @BindValue @JvmField val hostShortcuts: HostShortcuts = FakeHostShortcuts()

    @Inject lateinit var settings: SettingsRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<LibraryActivity>? = null
    private val five = listOf("a", "b", "c", "d", "e").map { aHost(id = it, name = "Host $it") }

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun launch(tv: Boolean = true) {
        if (tv) {
            shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        }
        scenario = ActivityScenario.launch(Intent(context, LibraryActivity::class.java))
        composeRule.waitForIdle()
    }

    private fun setDensity(id: String) {
        settings.setNow(Settings.CARD_DENSITY, id)
        composeRule.waitForIdle()
    }

    private fun top(node: SemanticsNodeInteraction) = node.getUnclippedBoundsInRoot().top

    /** How many of the five hosts share the top of the first one. */
    private fun hostsInFirstRow(): Int {
        val first = top(composeRule.onNodeWithTag("host:a"))
        return five.count { top(composeRule.onNodeWithTag("host:${it.id}")) == first }
    }

    private fun openApps() {
        hosts.emit(*five.toTypedArray())
        apps.emit(
            AppsSnapshot(
                host = five[0],
                apps = (1..SEVEN).map { anApp(it, "App $it") },
                loading = false,
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:a").performClick()
        composeRule.waitForIdle()
    }

    private fun appsInFirstRow(): Int {
        val first = top(composeRule.onNodeWithContentDescription("App 1"))
        return (1..SEVEN).count {
            top(composeRule.onNodeWithContentDescription("App $it")) == first
        }
    }

    @Test
    fun `with nothing stored Hosts keeps its three columns on a TV`() {
        hosts.emit(*five.toTypedArray())

        launch()

        assertThat(hostsInFirstRow()).isEqualTo(3)
    }

    @Test
    fun `a stored density is the density of the grids of the Library`() {
        settings.setNow(Settings.CARD_DENSITY, Settings.CARD_DENSITY_DENSE)
        hosts.emit(*five.toTypedArray())

        launch()

        assertThat(hostsInFirstRow()).isEqualTo(4)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a stored density is the density of Hosts on a phone`() {
        settings.setNow(Settings.CARD_DENSITY, Settings.CARD_DENSITY_COMPACT)
        hosts.emit(*five.toTypedArray())

        launch(tv = false)

        assertThat(hostsInFirstRow()).isEqualTo(2)
    }

    @Test
    fun `a change of the Card density setting lays Hosts out again at once`() {
        hosts.emit(*five.toTypedArray())
        launch()

        setDensity(Settings.CARD_DENSITY_COMPACT)
        assertThat(hostsInFirstRow()).isEqualTo(4)

        setDensity(Settings.CARD_DENSITY_DENSE)
        assertThat(hostsInFirstRow()).isEqualTo(4)

        setDensity(Settings.CARD_DENSITY_COMFORTABLE)
        assertThat(hostsInFirstRow()).isEqualTo(3)
    }

    @Test
    fun `a change of the Card density setting lays Apps out again at once`() {
        launch()
        openApps()
        assertThat(appsInFirstRow()).isEqualTo(5)

        setDensity(Settings.CARD_DENSITY_COMPACT)
        assertThat(appsInFirstRow()).isEqualTo(6)

        setDensity(Settings.CARD_DENSITY_COMFORTABLE)
        assertThat(appsInFirstRow()).isEqualTo(5)
    }

    @Test
    fun `a change of the setting keeps the focus on the card that had it`() {
        hosts.emit(*five.toTypedArray())
        launch()
        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("host:b").assertIsFocused()

        setDensity(Settings.CARD_DENSITY_COMPACT)
        composeRule.onNodeWithTag("host:b").assertIsFocused()

        setDensity(Settings.CARD_DENSITY_COMFORTABLE)
        composeRule.onNodeWithTag("host:b").assertIsFocused()
    }

    @Test
    fun `a change of the setting restarts nothing`() {
        hosts.emit(*five.toTypedArray())
        launch()
        var before: LibraryActivity? = null
        scenario!!.onActivity { before = it }

        setDensity(Settings.CARD_DENSITY_COMPACT)

        var after: LibraryActivity? = null
        scenario!!.onActivity { after = it }
        assertThat(after).isSameInstanceAs(before)
        assertThat(hosts.activeCollectors).isEqualTo(1)
    }

    private companion object {
        const val SEVEN = 7
    }
}
