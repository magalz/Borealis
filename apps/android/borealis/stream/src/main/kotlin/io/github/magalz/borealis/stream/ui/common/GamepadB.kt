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
 * Calls [action] when the gamepad B button goes down anywhere in the screen, once per press. The
 * key-down, its repeats and the key-up that follows stay in the screen: the stream host owns input,
 * so the screen cancels itself, and neither event may reach the next screen or the PC. A key-up
 * without a key-down the screen saw (B was held before it appeared) is left alone and does nothing.
 */
internal fun Modifier.onGamepadB(action: () -> Unit): Modifier = this then GamepadBElement(action)

private data class GamepadBElement(val action: () -> Unit) : ModifierNodeElement<GamepadBNode>() {
    override fun create() = GamepadBNode(action)

    override fun update(node: GamepadBNode) {
        node.action = action
    }
}

private class GamepadBNode(var action: () -> Unit) : Modifier.Node(), KeyInputModifierNode {
    private var held = false

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        if (event.key != Key.ButtonB) return false
        return when (event.type) {
            KeyEventType.KeyDown -> pressed(event)
            KeyEventType.KeyUp -> released()
            else -> false
        }
    }

    private fun pressed(event: KeyEvent): Boolean {
        if (event.nativeKeyEvent.repeatCount == 0) {
            held = true
            action()
        }
        return held
    }

    private fun released(): Boolean {
        val wasHeld = held
        held = false
        return wasHeld
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}
