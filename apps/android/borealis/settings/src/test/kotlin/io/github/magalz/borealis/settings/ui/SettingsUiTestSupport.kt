@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.testing.FakeSettingsOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Robolectric qualifiers of the devices that the settings screen is laid out for. */
const val PHONE = "w360dp-h640dp-xhdpi"
const val TV = "w960dp-h540dp-land-television-xhdpi"
const val HANDHELD = "w800dp-h480dp-land-hdpi-dpad"

/**
 * Sets [content] and switches the window to the keyboard input mode, the way a D-pad or a gamepad
 * key does on a device. Clickables are focusable in this mode only, and the focus outline shows
 * only in it.
 */
fun ComposeContentTestRule.setContentInKeyboardMode(content: @Composable () -> Unit) {
    var manager: InputModeManager? = null
    setContent {
        manager = LocalInputModeManager.current
        content()
    }
    runOnIdle { assertThat(checkNotNull(manager).requestInputMode(InputMode.Keyboard)).isTrue() }
    waitForIdle()
}

/** Runs `Dispatchers.Main` (and so `viewModelScope`) on a test dispatcher, so time is virtual. */
class MainDispatcherRule(private val dispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * Collects [flow] until the test ends, as a screen does, and runs the work that this starts.
 * Cancelling the returned job is the screen leaving.
 */
fun TestScope.subscribe(flow: Flow<*>): Job =
    backgroundScope
        .launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
        .also { runCurrent() }

/** The standard resolutions that a recent display and decoder offer. */
val STANDARD_RESOLUTIONS =
    listOf(
        ResolutionChoice("1280x720", 1280, 720, ResolutionKind.STANDARD),
        ResolutionChoice("1920x1080", 1920, 1080, ResolutionKind.STANDARD),
        ResolutionChoice("2560x1440", 2560, 1440, ResolutionKind.STANDARD),
        ResolutionChoice("3840x2160", 3840, 2160, ResolutionKind.STANDARD),
    )

/**
 * A [FakeSettingsOptions] that offers [STANDARD_RESOLUTIONS] and 30, 60, 90 and 120 fps unlocked.
 */
fun fakeOptions(): FakeSettingsOptions =
    FakeSettingsOptions().apply {
        resolutions = STANDARD_RESOLUTIONS
        frameRates = listOf(FrameRateChoice(30, false), FrameRateChoice(60, false))
    }

/** The names of the seven background styles, in the order of the Background row. */
val BACKGROUND_NAMES =
    listOf(
        "Aurora curtains",
        "Soft glow",
        "Northern lights",
        "Light mist",
        "Flowing light",
        "Starfield",
        "Off",
    )
