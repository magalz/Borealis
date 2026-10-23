package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import io.github.magalz.borealis.settings.catalog.Setting
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
 * The open sections of the settings list on a phone, in the Library as the user starts it: a change
 * of the look that the settings store, which the user makes from Appearance, leaves them as they
 * are.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class LibrarySettingsSectionsLookTest {
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

    /** Settings with the Stream and the Audio sections open, and the others closed. */
    private fun openSettingsWithTwoSectionsOpen() {
        scenario = ActivityScenario.launch(Intent(context, LibraryActivity::class.java))
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        header("STREAM").performClick()
        composeRule.waitForIdle()
        header("AUDIO").performClick()
        composeRule.waitForIdle()
        assertTwoSectionsAreOpen()
    }

    private fun header(title: String): SemanticsNodeInteraction =
        composeRule.onNodeWithText(title).also { it.performScrollTo() }

    private fun state(value: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun assertTwoSectionsAreOpen() {
        header("STREAM").assert(state("Expanded"))
        header("AUDIO").assert(state("Expanded"))
        header("VIDEO").assert(state("Collapsed"))
        header("Resolution").assertIsDisplayed()
        header("Channels").assertIsDisplayed()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()
    }

    /**
     * Opens [section], then the list of [row] and chooses [option] in the sheet, as a user does.
     */
    private fun <T> choose(section: String, row: String, option: String, setting: Setting<T>) {
        val before = settings.snapshot.value[setting]
        header(section).performClick()
        composeRule.waitForIdle()
        header(row).performClick()
        composeRule.waitForIdle()
        composeRule
            .onNode(
                hasText(option) and
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            )
            .performClick()
        // The setting is stored off the main thread: the look changes when it is.
        composeRule.waitUntil(STORE_TIMEOUT_MS) { settings.snapshot.value[setting] != before }
        composeRule.waitForIdle()
    }

    private fun assertThreeSectionsAreOpen(section: String) {
        assertTwoSectionsAreOpen()
        header(section).assert(state("Expanded"))
    }

    @Test
    fun `choosing a font leaves the open sections open`() {
        openSettingsWithTwoSectionsOpen()

        choose("APPEARANCE", "Font", "Lexend", Settings.FONT)

        assertThreeSectionsAreOpen("APPEARANCE")
    }

    @Test
    fun `choosing a text spacing leaves the open sections open`() {
        openSettingsWithTwoSectionsOpen()

        choose("ACCESSIBILITY", "Text spacing", "Wider", Settings.TEXT_SPACING)

        assertThreeSectionsAreOpen("ACCESSIBILITY")
    }

    @Test
    fun `choosing an accent leaves the open sections open`() {
        openSettingsWithTwoSectionsOpen()

        choose("APPEARANCE", "Accent colour", "Teal", Settings.ACCENT)

        assertThreeSectionsAreOpen("APPEARANCE")
    }

    @Test
    fun `choosing a card density leaves the open sections open`() {
        openSettingsWithTwoSectionsOpen()

        choose("APPEARANCE", "Card density", "Dense", Settings.CARD_DENSITY)

        assertThreeSectionsAreOpen("APPEARANCE")
    }

    @Test
    fun `choosing a text size leaves the open sections open`() {
        openSettingsWithTwoSectionsOpen()

        choose("ACCESSIBILITY", "Text size", "115 %", Settings.TEXT_SIZE)

        assertThreeSectionsAreOpen("ACCESSIBILITY")
    }

    private companion object {
        const val STORE_TIMEOUT_MS = 5_000L
    }
}
