package io.github.magalz.borealis.library.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.glassSurface
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
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
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.settings.appearance.rememberLooks
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Background intensity that the viewer stored reaches the background of the Library, and of the
 * Settings screens, which are shown in the same frame: the glass over the bright still of Flowing
 * light shows less of it at a lower intensity, as the setting changes and with no restart.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = TV)
class LibraryBackgroundIntensityTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createComposeRule()

    // The Hilt graph of the module needs these, though no screen of the Library is shown.
    @BindValue @JvmField val hostRepository: HostRepository = FakeHostRepository()
    @BindValue @JvmField val appRepository: AppRepository = FakeAppRepository()
    @BindValue @JvmField val pairingService: PairingService = FakePairingService()
    @BindValue @JvmField val streamLauncher: StreamLauncher = FakeStreamLauncher()
    @BindValue @JvmField val legacyBridge: LegacyBridge = FakeLegacyBridge()
    @BindValue @JvmField val hostShortcuts: HostShortcuts = FakeHostShortcuts()

    @Inject lateinit var settings: SettingsRepository

    private val surface = BorealisTokens.Color.Surface

    @Before
    fun inject() {
        hiltRule.inject()
        noAnimations()
    }

    private fun show() {
        composeRule.setContent {
            LibraryScaffold(
                FormFactor.Tv,
                RailSelection.Hosts,
                RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.FlowingLight,
                look = rememberLooks(settings).look,
            ) {
                Box(Modifier.size(100.dp).testTag(PANEL).glassSurface(RoundedCornerShape(8.dp)))
            }
        }
        composeRule.waitForIdle()
    }

    /** How far the glass is from the surface colour, which is how much of the background shows. */
    private fun shown(): Float {
        val shot = composeRule.onNodeWithTag(PANEL).captureToImage().toPixelMap()
        val centre: Color = shot[shot.width / 2, shot.height / 2]
        return centre.red + centre.green + centre.blue -
            (surface.red + surface.green + surface.blue)
    }

    @Test
    fun `a stored intensity is drawn from the first frame`() {
        settings.setNow(Settings.BACKGROUND_INTENSITY, "25")
        show()
        val first = shown()

        settings.setNow(Settings.BACKGROUND_INTENSITY, "100")
        composeRule.waitForIdle()
        val full = shown()

        assertThat(full).isGreaterThan(SHOWS_THROUGH)
        assertThat(first).isWithin(MARGIN).of(full * 0.25f)
    }

    @Test
    fun `a change of the stored intensity shows at once, with no restart`() {
        show()
        val full = shown()
        assertThat(full).isGreaterThan(SHOWS_THROUGH)

        listOf("75" to 0.25f, "50" to 0.5f, "25" to 0.75f).forEach { (percent, veil) ->
            settings.setNow(Settings.BACKGROUND_INTENSITY, percent)
            composeRule.waitForIdle()

            assertThat(shown()).isWithin(MARGIN).of(full * (1f - veil))
        }

        settings.setNow(Settings.BACKGROUND_INTENSITY, "100")
        composeRule.waitForIdle()
        assertThat(shown()).isWithin(MARGIN).of(full)
    }

    private companion object {
        const val PANEL = "panel"
        const val SHOWS_THROUGH = 0.15f
        const val MARGIN = 0.05f
    }
}
