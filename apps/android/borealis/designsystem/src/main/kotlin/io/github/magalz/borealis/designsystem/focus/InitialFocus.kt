package io.github.magalz.borealis.designsystem.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalInputModeManager
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

/**
 * Gives [requester] focus once, after the element is first laid out: the screen exposes the
 * requester, and this marks the element that starts with focus (the first action of a dialog, the
 * Continue card of Hosts). Put it before the modifier that makes the element focusable.
 *
 * On a TV or handheld the window also leaves touch mode, so the outline of the first element shows
 * as soon as the screen opens; a handheld with a touch screen starts in touch mode otherwise. A
 * phone keeps touch mode and the element still holds focus for a keyboard.
 */
fun Modifier.initialFocus(requester: FocusRequester): Modifier =
    focusRequester(requester) then InitialFocusElement(requester)

private data class InitialFocusElement(val requester: FocusRequester) :
    ModifierNodeElement<InitialFocusNode>() {
    override fun create() = InitialFocusNode(requester)

    override fun update(node: InitialFocusNode) {
        node.requester = requester
    }
}

private class InitialFocusNode(var requester: FocusRequester) :
    Modifier.Node(), LayoutAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var requested = false

    override fun onPlaced(coordinates: LayoutCoordinates) {
        if (requested) return
        requested = true
        if (currentValueOf(LocalFormFactor) != FormFactor.Phone) {
            currentValueOf(LocalInputModeManager).requestInputMode(InputMode.Keyboard)
        }
        requester.requestFocus()
    }
}

/** Whether the screen already took its initial focus. */
class InitialFocusState {
    internal var done = false
}

/** An [InitialFocusState] that starts again when [key] changes. */
@Composable
fun rememberInitialFocusState(key: Any? = null): InitialFocusState =
    remember(key) {
        InitialFocusState()
    }

fun Modifier.initialFocus(requester: FocusRequester, state: InitialFocusState): Modifier =
    initialFocus(requester)
