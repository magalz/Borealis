package io.github.magalz.borealis.designsystem.testing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager

/**
 * A window whose input mode a test sets. On a device a touch puts the window in touch mode before
 * the click that follows, and a key puts it in keyboard mode; neither does in a test, which injects
 * the events straight into the view. A test provides it as `LocalInputModeManager` and sets
 * [inputMode] where the device would have switched.
 */
class FakeInputModeManager(initial: InputMode) : InputModeManager {
    override var inputMode: InputMode by mutableStateOf(initial)

    override fun requestInputMode(inputMode: InputMode): Boolean {
        this.inputMode = inputMode
        return true
    }
}
