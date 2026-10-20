package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager

/**
 * A window whose input mode a test sets. On a device a touch puts the window in touch mode before
 * the click that follows; a touch injected into a test does not, so a test that stands for a tap
 * sets the mode itself.
 */
internal class FakeInputModeManager(initial: InputMode) : InputModeManager {
    override var inputMode: InputMode by mutableStateOf(initial)

    override fun requestInputMode(inputMode: InputMode): Boolean {
        this.inputMode = inputMode
        return true
    }
}
