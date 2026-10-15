package io.github.magalz.borealis.designsystem.focus

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role

private val OPTIONS_KEYS = setOf(Key.ButtonX, Key.Menu)

/**
 * Calls [onOptions] when the user presses X on a gamepad or Menu on a remote while the element (or
 * something inside it) has focus. A key held down asks once.
 *
 * This handles the keys that no click action covers. Holding OK on a TV and a long press on touch
 * are long clicks, which [optionsClickable] wires to the same callback.
 */
fun Modifier.onOptionsRequest(onOptions: () -> Unit): Modifier = onKeyEvent { event ->
    val isOptionsKey = event.type == KeyEventType.KeyDown && event.key in OPTIONS_KEYS
    if (isOptionsKey && event.nativeKeyEvent.repeatCount == 0) onOptions()
    isOptionsKey
}

/**
 * A focusable item with a main action and, optionally, its options. Everything that opens the
 * options calls [onOptions]: a long press on touch, holding OK or Enter on a TV, X on a gamepad and
 * the Menu key. A long press or a held OK is not also a click.
 *
 * Put the focus indication before this modifier, as described on [borealisFocusIndication].
 */
fun Modifier.optionsClickable(
    onClick: () -> Unit,
    onOptions: (() -> Unit)? = null,
    role: Role? = null,
    onOptionsLabel: String? = null,
): Modifier =
    (if (onOptions == null) this else onOptionsRequest(onOptions)).combinedClickable(
        interactionSource = null,
        indication = BorealisPressIndication,
        role = role,
        onLongClick = onOptions,
        onClick = onClick,
    )
