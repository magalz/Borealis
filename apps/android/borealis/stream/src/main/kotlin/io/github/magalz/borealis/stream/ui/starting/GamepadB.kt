package io.github.magalz.borealis.stream.ui.starting

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Calls [action] when the gamepad B button goes down anywhere in the screen. The stream host owns
 * input, so the screen cancels itself instead of leaving it to a platform dialog or Back.
 */
internal fun Modifier.onGamepadB(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    val pressed = event.key == Key.ButtonB && event.type == KeyEventType.KeyDown
    if (pressed) action()
    pressed
}
