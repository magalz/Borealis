package io.github.magalz.borealis.stream.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.ModifierNodeElement

/**
 * Calls [action] when Back comes up anywhere in the panel, once per press, as the system does. The
 * key-down, its repeats and the key-up stay in the panel: Compose would take an unhandled Back for
 * a focus exit, out of a chip of the menu or a key of the keyboard, and the panel would close only
 * with a second press. The segments of the menu's option rows are no focus stops (issue #98), so
 * Back has none to leave. A key-up without a key-down the panel saw (Back opened the panel) is left
 * alone and does nothing.
 *
 * Without focus in the panel nothing reaches it, and Back goes to the host's back callback.
 */
internal fun Modifier.onBackKey(action: () -> Unit): Modifier = this then BackKeyElement(action)

private data class BackKeyElement(val action: () -> Unit) : ModifierNodeElement<BackKeyNode>() {
    override fun create() = BackKeyNode(action)

    override fun update(node: BackKeyNode) {
        node.action = action
    }
}

private class BackKeyNode(var action: () -> Unit) : Modifier.Node(), KeyInputModifierNode {
    private var held = false

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        if (event.key != Key.Back) return false
        return when (event.type) {
            KeyEventType.KeyDown -> {
                held = true
                true
            }
            KeyEventType.KeyUp -> released()
            else -> false
        }
    }

    private fun released(): Boolean {
        val wasHeld = held
        held = false
        if (wasHeld) action()
        return wasHeld
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}
