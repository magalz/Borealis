package io.github.magalz.borealis.stream.ui.starting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.stream.LaunchRetry
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.setChrome
import io.github.magalz.borealis.stream.ui.ConflictDialog
import io.github.magalz.borealis.stream.ui.StartingScreen
import io.github.magalz.borealis.stream.ui.background.OnStillBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the starting screen and the conflict dialog, named `<screen>_<device>_<state>`.
 */
@RunWith(AndroidJUnit4::class)
class StartingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val intents = RecordingIntents()

    @Test
    @Config(qualifiers = PHONE)
    fun startingPhoneConnecting() {
        composeRule.setChrome(FormFactor.Phone) {
            Starting(StreamPhase.Starting(StartStage.CONNECTING))
        }

        shoot("starting_phone_connecting")
    }

    @Test
    @Config(qualifiers = TV)
    fun startingTvPreparing() {
        composeRule.setChrome(FormFactor.Tv) {
            Starting(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))
        }

        shoot("starting_tv_preparing")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun startingPhoneRetry() {
        val retry =
            LaunchRetry(LaunchCategory.PENDING, attempt = 1, maxAttempts = 6, delayMs = 3000)
        composeRule.setChrome(FormFactor.Phone) {
            Starting(StreamPhase.Starting(StartStage.STARTING_APP, retry))
        }

        shoot("starting_phone_retry")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun conflictPhoneOtherApp() {
        val phase = StreamPhase.Conflict("Desktop", "Hades II", sameApp = false)
        composeRule.setChrome(FormFactor.Phone) { Conflict(phase) }

        shoot("conflict_phone_other-app")
    }

    @Test
    @Config(qualifiers = TV)
    fun conflictTvSameAppFocused() {
        val phase = StreamPhase.Conflict("Desktop", "Desktop", sameApp = true)
        composeRule.setChrome(FormFactor.Tv) { Conflict(phase) }

        shoot("conflict_tv_same-app_focused")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun startingHandheldStartingApp() {
        composeRule.setChrome(FormFactor.Handheld) {
            Starting(StreamPhase.Starting(StartStage.STARTING_APP))
        }

        shoot("starting_handheld_starting-app")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun conflictHandheldOtherApp() {
        val phase = StreamPhase.Conflict("Desktop", "Hades II", sameApp = false)
        composeRule.setChrome(FormFactor.Handheld) { Conflict(phase) }

        shoot("conflict_handheld_other-app")
    }

    @Composable
    private fun Starting(phase: StreamPhase.Starting) {
        Stream { StartingScreen("Desktop", "Gaming PC", phase, intents) }
    }

    @Composable
    private fun Conflict(phase: StreamPhase.Conflict) {
        Stream { ConflictDialog(phase, intents) }
    }

    /** The chrome sits over the stream, on one frame of the background outside the game. */
    @Composable
    private fun Stream(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { OnStillBackground(content) }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
