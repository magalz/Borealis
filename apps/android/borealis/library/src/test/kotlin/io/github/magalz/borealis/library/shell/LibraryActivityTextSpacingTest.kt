package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.TextSpacing
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
 * The Library as the user starts it: the Text spacing that the settings store is the spacing of its
 * text, and a change of the setting shows at once, with no restart (ADR 0058, decision 4). The name
 * of the host has a space, so the word spacing shows too.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class LibraryActivityTextSpacingTest {
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

    private fun hostName(): TextLayoutResult =
        composeRule.onNodeWithText("Desk PC", useUnmergedTree = true).textLayout()

    /** The letter spacing of the name of the host, in em of its size. */
    private fun letterEm(): Float {
        val style = hostName().layoutInput.style
        val spacing = style.letterSpacing
        if (!spacing.isSpecified) return 0f
        return if (spacing.type == TextUnitType.Em) spacing.value
        else spacing.value / style.fontSize.value
    }

    /** The extra spacing that the space of the name carries, in em, or null when it has none. */
    private fun spaceEm(): Float? =
        hostName()
            .layoutInput
            .text
            .spanStyles
            .singleOrNull { it.start == SPACE && it.end == SPACE + 1 }
            ?.item
            ?.letterSpacing
            ?.value

    @Test
    fun `with nothing stored the text has the spacing of the design`() {
        launch()

        assertThat(letterEm()).isWithin(EPSILON).of(0f)
        assertThat(spaceEm()).isNull()
    }

    @Test
    fun `a stored spacing is the spacing of the text of the Library`() {
        settings.setNow(Settings.TEXT_SPACING, Settings.TEXT_SPACING_WIDER)

        launch()

        assertThat(letterEm()).isWithin(EPSILON).of(TextSpacing.Wider.letterEm)
        assertThat(spaceEm())
            .isWithin(EPSILON)
            .of(TextSpacing.Wider.letterEm + TextSpacing.Wider.wordEm)
    }

    @Test
    fun `a change of the Text spacing setting shows at once, with no restart`() {
        launch()

        settings.setNow(Settings.TEXT_SPACING, Settings.TEXT_SPACING_WIDE)
        composeRule.waitForIdle()
        assertThat(letterEm()).isWithin(EPSILON).of(TextSpacing.Wide.letterEm)
        assertThat(spaceEm())
            .isWithin(EPSILON)
            .of(TextSpacing.Wide.letterEm + TextSpacing.Wide.wordEm)

        settings.setNow(Settings.TEXT_SPACING, Settings.TEXT_SPACING_WIDER)
        composeRule.waitForIdle()
        assertThat(letterEm()).isWithin(EPSILON).of(TextSpacing.Wider.letterEm)

        settings.setNow(Settings.TEXT_SPACING, Settings.TEXT_SPACING_NORMAL)
        composeRule.waitForIdle()
        assertThat(letterEm()).isWithin(EPSILON).of(0f)
        assertThat(spaceEm()).isNull()
    }

    private companion object {
        const val EPSILON = 0.001f

        /** The index of the space in "Desk PC". */
        const val SPACE = 4
    }
}
