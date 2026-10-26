package io.github.magalz.borealis.stream.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.PointerSpeed
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.keys.KeyChord

/** Robolectric qualifiers of the devices the stream chrome is laid out for. */
internal const val PHONE = "w360dp-h640dp-xhdpi"
internal const val TV = "w960dp-h540dp-land-television-xhdpi"
internal const val HANDHELD = "w800dp-h480dp-land-hdpi"

/** A phone held sideways, which is how a stream is played: the panels get their 52% of 640dp. */
internal const val PHONE_LANDSCAPE = "w640dp-h360dp-land-xhdpi"

/** Sets [content] in the theme `StreamChromeRoot` wraps every screen in. */
internal fun ComposeContentTestRule.setChrome(
    formFactor: FormFactor,
    content: @Composable () -> Unit,
) = setContent { BorealisTheme(formFactor) { content() } }

/** Like [setChrome], in the keyboard input mode (see [setContentInKeyboardMode]). */
internal fun ComposeContentTestRule.setChromeInKeyboardMode(
    formFactor: FormFactor,
    content: @Composable () -> Unit,
) = setContentInKeyboardMode { BorealisTheme(formFactor) { content() } }

/** Asserts the node is [expected] wide, give or take a dp for rounding to pixels. */
internal fun SemanticsNodeInteraction.assertWidthNear(expected: Dp) {
    assertThat(getBoundsInRoot().width.value).isWithin(1f).of(expected.value)
}

/**
 * What a stream screen asked of the session. [calls] has one entry per call, in order, with its
 * argument when it has one; [chords] has the keys sent, which are not in [calls].
 */
@Suppress("TooManyFunctions") // One call per intent of the stream screens.
internal class RecordingIntents : StreamIntents {
    val calls = mutableListOf<String>()
    val chords = mutableListOf<KeyChord>()

    override fun openMenu() {
        calls += "openMenu"
    }

    override fun openKeyboard() {
        calls += "openKeyboard"
    }

    override fun closePanel() {
        calls += "closePanel"
    }

    override fun dismissMenuHint() {
        calls += "dismissMenuHint"
    }

    override fun send(chord: KeyChord) {
        chords += chord
    }

    override fun sendText(text: String) {
        calls += "sendText $text"
    }

    override fun openSystemKeyboard() {
        calls += "openSystemKeyboard"
    }

    override fun requestKeyframe() {
        calls += "requestKeyframe"
    }

    override fun setTouchMode(mode: TouchMode) {
        calls += "setTouchMode $mode"
    }

    override fun setGamepadMouse(enabled: Boolean) {
        calls += "setGamepadMouse $enabled"
    }

    override fun setPointerSpeed(speed: PointerSpeed) {
        calls += "setPointerSpeed $speed"
    }

    override fun setOverlay(mode: OverlayMode) {
        calls += "setOverlay $mode"
    }

    override fun disconnect() {
        calls += "disconnect"
    }

    override fun quitApp() {
        calls += "quitApp"
    }

    override fun leave() {
        calls += "leave"
    }

    override fun retry() {
        calls += "retry"
    }

    override fun wakeHost() {
        calls += "wakeHost"
    }

    override fun pairAgain() {
        calls += "pairAgain"
    }

    override fun openDiagnostics() {
        calls += "openDiagnostics"
    }

    override fun answerConflict(choice: ConflictChoice) {
        calls += "answerConflict $choice"
    }
}
