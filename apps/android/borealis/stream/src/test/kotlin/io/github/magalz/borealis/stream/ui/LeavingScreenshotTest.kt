package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.setChrome
import io.github.magalz.borealis.stream.ui.background.OnStillBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the screens of leaving a stream, named `<screen>_<device>`, on one frame of the
 * background of the screens outside the game. The loader fills for ever: the clock is frozen at a
 * time at which the bands are fully drawn, so the capture shows the loader whole and is the same
 * every run.
 */
@RunWith(AndroidJUnit4::class)
class LeavingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun quittingPhone() = shoot("quitting_phone", FormFactor.Phone) { QuittingScreen("Desktop") }

    @Test
    @Config(qualifiers = TV)
    fun quittingTv() = shoot("quitting_tv", FormFactor.Tv) { QuittingScreen("Desktop") }

    @Test
    @Config(qualifiers = HANDHELD)
    fun quittingHandheld() =
        shoot("quitting_handheld", FormFactor.Handheld) { QuittingScreen("Desktop") }

    @Test
    @Config(qualifiers = PHONE)
    fun disconnectingPhone() =
        shoot("disconnecting_phone", FormFactor.Phone) { DisconnectingScreen("Desktop") }

    @Test
    @Config(qualifiers = TV)
    fun disconnectingTv() =
        shoot("disconnecting_tv", FormFactor.Tv) { DisconnectingScreen("Desktop") }

    @Test
    @Config(qualifiers = HANDHELD)
    fun disconnectingHandheld() =
        shoot("disconnecting_handheld", FormFactor.Handheld) { DisconnectingScreen("Desktop") }

    private fun shoot(name: String, formFactor: FormFactor, screen: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setChrome(formFactor) { OnStillBackground { screen() } }
        composeRule.mainClock.advanceTimeBy(LOADER_TIME_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val LOADER_TIME_MS = 1_200L
    }
}
