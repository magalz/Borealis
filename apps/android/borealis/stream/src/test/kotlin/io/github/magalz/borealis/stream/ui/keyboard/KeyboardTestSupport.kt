package io.github.magalz.borealis.stream.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.keys.KeyChord

/** Robolectric qualifiers of the devices the keyboard is laid out for. */
const val PHONE = "w360dp-h640dp-xhdpi"
const val TV = "w960dp-h540dp-land-television-xhdpi"
const val HANDHELD = "w800dp-h480dp-land-hdpi"

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

/** Records what the keyboard asks of the stream: the chords it sends and the panel calls. */
@Suppress("TooManyFunctions") // One call per intent of the stream screens.
class RecordingIntents : StreamIntents {
    val chords = mutableListOf<KeyChord>()
    val calls = mutableListOf<String>()

    override fun send(chord: KeyChord) {
        chords += chord
    }

    override fun closePanel() {
        calls += "closePanel"
    }

    override fun openSystemKeyboard() {
        calls += "openSystemKeyboard"
    }

    override fun openMenu() = other("openMenu")

    override fun openKeyboard() = other("openKeyboard")

    override fun dismissMenuHint() = other("dismissMenuHint")

    override fun sendText(text: String) = other("sendText")

    override fun requestKeyframe() = other("requestKeyframe")

    override fun setTouchMode(mode: TouchMode) = other("setTouchMode")

    override fun setGamepadMouse(enabled: Boolean) = other("setGamepadMouse")

    override fun setOverlay(mode: OverlayMode) = other("setOverlay")

    override fun disconnect() = other("disconnect")

    override fun quitApp() = other("quitApp")

    override fun leave() = other("leave")

    override fun retry() = other("retry")

    override fun wakeHost() = other("wakeHost")

    override fun pairAgain() = other("pairAgain")

    override fun openDiagnostics() = other("openDiagnostics")

    override fun answerConflict(choice: ConflictChoice) = other("answerConflict")

    private fun other(call: String) {
        calls += call
    }
}
