@file:Suppress("MatchingDeclarationName")

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
 * Whether a screen already took its initial focus. One state serves every element of a screen that
 * may start with focus; see [Modifier.initialFocus] with a state.
 */
class InitialFocusState {
    internal var done = false
}

/**
 * An [InitialFocusState] for one screen. It starts again when [key] changes: key it on what makes
 * the screen show something else (Hosts keys it on whether it is still searching), so the new
 * content takes the initial focus once more.
 */
@Composable
fun rememberInitialFocusState(key: Any? = null): InitialFocusState =
    remember(key) { InitialFocusState() }

/**
 * Gives [requester] focus once, after the element is first laid out: the screen exposes the
 * requester, and this marks the element that starts with focus (the first action of a dialog, the
 * Continue card of Hosts). Put it before the modifier that makes the element focusable.
 *
 * This overload is per element: every element that enters the composition asks for focus once,
 * which is right for a dialog or a screen with one such element. A screen whose content comes and
 * goes (a list that gets a new first item, a section that appears, an item that scrolls out and
 * back) should use the overload with an [InitialFocusState], or a late element takes the focus the
 * user already moved.
 *
 * On a TV or handheld the window also leaves touch mode, so the outline of the first element shows
 * as soon as the screen opens; a handheld with a touch screen starts in touch mode otherwise. A
 * phone keeps touch mode and the element still holds focus for a keyboard.
 */
fun Modifier.initialFocus(requester: FocusRequester): Modifier =
    focusRequester(requester) then InitialFocusElement(requester, null)

/**
 * Gives [requester] focus when the element is laid out, but only while [state] is not done, and
 * marks it done once focus is granted. Every element of the screen that may start with focus passes
 * the same state, so the screen takes its initial focus once and later elements leave it where the
 * user moved it. Otherwise it behaves as [initialFocus] without a state.
 */
fun Modifier.initialFocus(requester: FocusRequester, state: InitialFocusState): Modifier =
    focusRequester(requester) then InitialFocusElement(requester, state)

private data class InitialFocusElement(
    val requester: FocusRequester,
    val state: InitialFocusState?,
) : ModifierNodeElement<InitialFocusNode>() {
    override fun create() = InitialFocusNode(requester, state)

    override fun update(node: InitialFocusNode) {
        node.requester = requester
        node.state = state
    }
}

private class InitialFocusNode(var requester: FocusRequester, var state: InitialFocusState?) :
    Modifier.Node(), LayoutAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var requested = false

    override fun onPlaced(coordinates: LayoutCoordinates) {
        val screen = state
        if (screen == null) {
            if (requested) return
            requested = true
        } else if (screen.done) {
            return
        }
        if (currentValueOf(LocalFormFactor) != FormFactor.Phone) {
            currentValueOf(LocalInputModeManager).requestInputMode(InputMode.Keyboard)
        }
        if (requester.requestFocus() && screen != null) screen.done = true
    }
}
