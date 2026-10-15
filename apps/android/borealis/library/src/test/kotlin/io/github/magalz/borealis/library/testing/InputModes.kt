package io.github.magalz.borealis.library.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.google.common.truth.Truth.assertThat

/** Robolectric qualifiers of the devices the Library screens are laid out for. */
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
