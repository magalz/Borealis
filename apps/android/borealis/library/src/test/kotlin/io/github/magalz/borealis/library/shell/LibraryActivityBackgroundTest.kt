package io.github.magalz.borealis.library.shell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import io.github.magalz.borealis.library.LibraryLaunchRequest
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Library as the user starts it: the shell passes the Aurora curtains, the default style, until
 * the Appearance setting chooses another. The system removes animations, so the frame is the same
 * on every run.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = TV)
class LibraryActivityBackgroundTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @BindValue @JvmField val hostRepository: HostRepository = FakeHostRepository()
    @BindValue @JvmField val appRepository: AppRepository = FakeAppRepository()
    @BindValue @JvmField val pairingService: PairingService = FakePairingService()
    @BindValue @JvmField val streamLauncher: StreamLauncher = FakeStreamLauncher()
    @BindValue @JvmField val legacyBridge: LegacyBridge = FakeLegacyBridge()
    @BindValue @JvmField val hostShortcuts: HostShortcuts = FakeHostShortcuts()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<LibraryActivity>? = null

    @Before
    fun inject() {
        hiltRule.inject()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun `the Library shows the Aurora curtains behind Settings by default`() {
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtras(LibraryLaunchRequest.Privacy.toExtras())
        scenario = ActivityScenario.launch(intent)
        composeRule.waitForIdle()

        val shot = composeRule.onRoot().captureToImage().toPixelMap()
        // The margin right of the rows of the Privacy pane, which is empty page without a
        // background.
        val lifts =
            (RIGHT_MARGIN_TOP until RIGHT_MARGIN_BOTTOM step STEP).flatMap { y ->
                (RIGHT_MARGIN_LEFT until shot.width step STEP).map { x ->
                    val pixel = shot[x, y]
                    pixel.red + pixel.green + pixel.blue - PAGE_SUM
                }
            }
        assertThat(lifts.max()).isIn(Range.open(0.15f, 1.2f))
    }

    private companion object {
        // Pixels of the 1920 x 1080 TV: past the toggles, which end at 1804, from 200 to 500 down.
        const val RIGHT_MARGIN_LEFT = 1860
        const val RIGHT_MARGIN_TOP = 200
        const val RIGHT_MARGIN_BOTTOM = 500
        const val STEP = 4
        val PAGE_SUM = BorealisTokens.Color.Page.let { it.red + it.green + it.blue }
    }
}
