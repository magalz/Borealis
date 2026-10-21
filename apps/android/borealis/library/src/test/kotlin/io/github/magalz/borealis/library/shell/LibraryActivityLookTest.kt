package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
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
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Library as the user starts it: the look that the settings store reaches the screens, and a
 * change of a setting shows at once. The one part of the look that the screens act on today is
 * Reduce motion: a video background plays its clip while animations are kept, and shows its still
 * and no clip when Reduce motion is On, also when the system keeps its animations.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class LibraryActivityLookTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @BindValue @JvmField val hostRepository: HostRepository = FakeHostRepository()
    @BindValue @JvmField val appRepository: AppRepository = FakeAppRepository()
    @BindValue @JvmField val pairingService: PairingService = FakePairingService()
    @BindValue @JvmField val streamLauncher: StreamLauncher = FakeStreamLauncher()
    @BindValue @JvmField val legacyBridge: LegacyBridge = FakeLegacyBridge()
    @BindValue @JvmField val hostShortcuts: HostShortcuts = FakeHostShortcuts()

    @Inject lateinit var settings: SettingsRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<LibraryActivity>? = null

    @Before
    fun inject() {
        hiltRule.inject()
        settings.setNow(Settings.BACKGROUND, "light_mist")
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, LibraryActivity::class.java))
        composeRule.waitForIdle()
    }

    private fun assertClipPlays() {
        composeRule.onNodeWithTag(VIDEO).assertExists()
    }

    private fun assertOnlyTheStillShows() {
        composeRule.onNodeWithTag(VIDEO).assertDoesNotExist()
        composeRule.onNodeWithTag(STILL).assertExists()
    }

    @Test
    fun `a stored Reduce motion of On shows the still and plays no clip`() {
        settings.setNow(Settings.REDUCE_MOTION, "on")

        launch()

        assertOnlyTheStillShows()
    }

    @Test
    fun `with Reduce motion following a system that animates, the clip plays`() {
        launch()

        assertClipPlays()
    }

    @Test
    fun `a stored Off keeps the clip playing`() {
        settings.setNow(Settings.REDUCE_MOTION, "off")

        launch()

        assertClipPlays()
    }

    @Test
    fun `a change of Reduce motion shows at once, with no restart`() {
        launch()
        assertClipPlays()

        settings.setNow(Settings.REDUCE_MOTION, "on")
        composeRule.waitForIdle()
        assertOnlyTheStillShows()

        settings.setNow(Settings.REDUCE_MOTION, "follow_system")
        composeRule.waitForIdle()
        assertClipPlays()
    }

    private companion object {
        // The tags of the pieces of a video background (BackgroundTags, internal to the design
        // system).
        const val VIDEO = "background:video"
        const val STILL = "background:still"
    }
}
