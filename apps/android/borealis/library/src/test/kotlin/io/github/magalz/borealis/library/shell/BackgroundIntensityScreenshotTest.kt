package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BackgroundIntensity
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.TV
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of Hosts on the TV under the Background intensity (ADR 0058, decision 8), named
 * `librarybackground_tv_<style>-intensity-<percent>.png`: the page colour over the background at
 * the veil of the level, under the glass. The animated background is held on its still frame, which
 * is the same at every run. With high contrast the dim of 0.85 is the stronger veil, so the frame
 * at 25 percent is the frame of high contrast alone.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class BackgroundIntensityScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private var look by mutableStateOf(BorealisLook())

    @Before
    fun theBackgroundIsItsStill() {
        noAnimations()
    }

    @Test
    fun auroraCurtainsHalf() =
        shoot(
            "librarybackground_tv_aurora-curtains-intensity-50",
            BackgroundStyle.AuroraCurtains,
            BackgroundIntensity.Half,
        )

    @Test
    fun auroraCurtainsQuarter() =
        shoot(
            "librarybackground_tv_aurora-curtains-intensity-25",
            BackgroundStyle.AuroraCurtains,
            BackgroundIntensity.Quarter,
        )

    @Test
    fun lightMistQuarter() =
        shoot(
            "librarybackground_tv_light-mist-intensity-25",
            BackgroundStyle.LightMist,
            BackgroundIntensity.Quarter,
        )

    @Test
    fun highContrastQuarterIsHighContrastAlone() {
        look = BorealisLook(highContrast = true)
        composeRule.setContentInKeyboardMode { Hosts(BackgroundStyle.AuroraCurtains) }
        composeRule.waitForIdle()
        val alone = composeRule.onRoot().captureToImage().toPixelMap()

        look = BorealisLook(highContrast = true, backgroundIntensity = BackgroundIntensity.Quarter)
        composeRule.waitForIdle()

        assertWithMessage("the frame of high contrast at 25 percent is that of high contrast alone")
            .that(
                composeRule
                    .onRoot()
                    .captureToImage()
                    .toPixelMap()
                    .buffer
                    .contentEquals(alone.buffer)
            )
            .isTrue()
        composeRule
            .onRoot()
            .captureRoboImage("src/test/screenshots/a11y_tv_high-contrast-intensity-25.png")
    }

    private fun shoot(name: String, style: BackgroundStyle, intensity: BackgroundIntensity) {
        look = BorealisLook(backgroundIntensity = intensity)
        composeRule.setContent { Hosts(style) }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Hosts(style: BackgroundStyle) {
        LibraryScaffold(
            FormFactor.Tv,
            selected = RailSelection.Hosts,
            actions = RailActions({}, {}, {}),
            backgroundStyle = style,
            look = look,
        ) {
            HostsScreen(
                state = withHosts,
                formFactor = FormFactor.Tv,
                snackbarHostState = SnackbarHostState(),
                onHostSelected = {},
                onContinue = {},
                onWake = {},
                onForget = {},
                onDetails = {},
                onDetailsDismissed = {},
                onAddHost = {},
                onSettings = {},
            )
        }
    }
}
