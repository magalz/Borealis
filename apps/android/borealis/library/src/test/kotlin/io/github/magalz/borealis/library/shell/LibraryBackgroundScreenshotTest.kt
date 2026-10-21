package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Library frame with Hosts on a background style, named
 * `librarybackground_<device>_<style>.png`. They are the still frame of a system without
 * animations, which is the same at every run: the Compose styles at the time of that frame, and for
 * a video style the still of its first frame, drawn with no player.
 */
@RunWith(AndroidJUnit4::class)
class LibraryBackgroundScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = TV)
    fun backgroundTvAuroraCurtains() =
        shoot("librarybackground_tv_aurora-curtains", FormFactor.Tv, BackgroundStyle.AuroraCurtains)

    @Test
    @Config(qualifiers = PHONE)
    fun backgroundPhoneAuroraCurtains() =
        shoot(
            "librarybackground_phone_aurora-curtains",
            FormFactor.Phone,
            BackgroundStyle.AuroraCurtains,
        )

    @Test
    @Config(qualifiers = TV)
    fun backgroundTvSoftGlow() =
        shoot("librarybackground_tv_soft-glow", FormFactor.Tv, BackgroundStyle.SoftGlow)

    @Test
    @Config(qualifiers = PHONE)
    fun backgroundPhoneSoftGlow() =
        shoot("librarybackground_phone_soft-glow", FormFactor.Phone, BackgroundStyle.SoftGlow)

    @Test
    @Config(qualifiers = TV)
    fun backgroundTvNorthernLightsStill() =
        shoot(
            "librarybackground_tv_northern-lights-still",
            FormFactor.Tv,
            BackgroundStyle.NorthernLights,
        )

    private fun shoot(name: String, formFactor: FormFactor, style: BackgroundStyle) {
        noAnimations()
        composeRule.setContent {
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                backgroundStyle = style,
            ) {
                HostsScreen(
                    state = withHosts,
                    formFactor = formFactor,
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
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
