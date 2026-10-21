package io.github.magalz.borealis.library.shell

import android.content.Context
import android.provider.Settings as AndroidSettings
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.glassSurface
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
 * The glass of the Library follows what the viewer stored: the frame gives the look that the
 * settings hold to its host, and a glass surface on a screen is solid where the Transparency is Off
 * or High contrast is on, and shows the bright background through where it is Standard.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = TV)
class LibraryGlassTest {
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
        // The still of Flowing light, which is bright: a tint over it is far from the surface.
        val context = ApplicationProvider.getApplicationContext<Context>()
        AndroidSettings.Global.putFloat(
            context.contentResolver,
            AndroidSettings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
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

    private fun panelCentre(): Color {
        val shot = composeRule.onNodeWithTag(PANEL).captureToImage().toPixelMap()
        return shot[shot.width / 2, shot.height / 2]
    }

    @Test
    fun `a stored Transparency of Off makes the glass of the Library the solid surface`() {
        settings.setNow(Settings.TRANSPARENCY, Settings.TRANSPARENCY_OFF)

        show()

        assertThat(panelCentre().red).isWithin(TOLERANCE).of(surface.red)
        assertThat(panelCentre().green).isWithin(TOLERANCE).of(surface.green)
    }

    @Test
    fun `a stored High contrast makes the glass of the Library the solid surface`() {
        settings.setNow(Settings.HIGH_CONTRAST, true)

        show()

        assertThat(panelCentre().red).isWithin(TOLERANCE).of(surface.red)
    }

    @Test
    fun `the glass of the Library shows the background through where nothing is stored`() {
        show()

        assertThat(panelCentre().red).isGreaterThan(surface.red + SHOWS_THROUGH)
    }

    @Test
    fun `a change of the stored Transparency shows at once, with no restart`() {
        show()
        assertThat(panelCentre().red).isGreaterThan(surface.red + SHOWS_THROUGH)

        settings.setNow(Settings.TRANSPARENCY, Settings.TRANSPARENCY_OFF)
        composeRule.waitForIdle()
        assertThat(panelCentre().red).isWithin(TOLERANCE).of(surface.red)

        settings.setNow(Settings.TRANSPARENCY, Settings.TRANSPARENCY_STANDARD)
        composeRule.waitForIdle()
        assertThat(panelCentre().red).isGreaterThan(surface.red + SHOWS_THROUGH)
    }

    private companion object {
        const val PANEL = "panel"
        const val TOLERANCE = 0.01f
        const val SHOWS_THROUGH = 0.05f
    }
}
