package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.designsystem.testing.fontFileNames
import io.github.magalz.borealis.designsystem.testing.textLayout
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
 * The Library as the user starts it: the font that the settings store is the font of its text, and
 * a change of the Font setting shows at once, with no restart (ADR 0058, decision 2).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class LibraryActivityFontTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    private val hosts = FakeHostRepository()

    @BindValue @JvmField val hostRepository: HostRepository = hosts
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

    /** The files of the font that the name of the host is drawn in. */
    private fun hostNameFiles(): List<String> =
        composeRule
            .onNodeWithText("Desk PC", useUnmergedTree = true)
            .textLayout()
            .layoutInput
            .style
            .fontFamily
            .fontFileNames(context.resources)

    @Test
    fun `with nothing stored the text is IBM Plex Sans`() {
        launch()

        assertThat(hostNameFiles())
            .containsExactly(
                "ibm_plex_sans_regular",
                "ibm_plex_sans_medium",
                "ibm_plex_sans_semibold",
            )
            .inOrder()
    }

    @Test
    fun `a stored font is the font of the text of the Library`() {
        settings.setNow(Settings.FONT, "b612")

        launch()

        assertThat(hostNameFiles()).containsExactly("b612_regular", "b612_bold").inOrder()
    }

    @Test
    fun `a change of the Font setting shows at once, with no restart`() {
        launch()

        settings.setNow(Settings.FONT, "luciole")
        composeRule.waitForIdle()
        assertThat(hostNameFiles()).containsExactly("luciole_regular", "luciole_bold").inOrder()

        settings.setNow(Settings.FONT, "lexend")
        composeRule.waitForIdle()
        assertThat(hostNameFiles())
            .containsExactly("lexend_regular", "lexend_medium", "lexend_semibold")
            .inOrder()
    }
}
