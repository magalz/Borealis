package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/**
 * A single-line text field: the input radius, a line-strong border that turns accent on focus, and
 * [error] under it in danger-ink (it also turns the border and label to danger-ink). The keyboard
 * action is Done: it calls [onDone] if there is one and then does what Done does by default, which
 * is to hide the keyboard and clear focus. [keyboardType] picks the keyboard layout, such as Uri
 * for a host address.
 *
 * A text field keeps the D-pad for its caret, so a remote cannot leave it. With [leaveWithDpad], Up
 * and Down move the focus to the neighbour above and below, and Left moves it to the neighbour on
 * the left while the caret is at the start (an empty field has no caret to move). Left and Right
 * otherwise move the caret, and the text is never changed.
 *
 * Compose opens the keyboard whenever the field gains focus. With [showKeyboardOnFocus] false it
 * opens only when the user presses OK (a tap, Enter or DPAD_CENTER), so a remote can move through
 * the field without the keyboard covering the screen.
 */
@Composable
fun BorealisTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    onDone: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    leaveWithDpad: Boolean = false,
    showKeyboardOnFocus: Boolean = true,
) {
    val colors = LocalBorealisColors.current
    // The state keeps the caret, so the field can tell when Left has nowhere left to go.
    val state = rememberTextFieldState(value, TextRange(value.length))
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnDone by rememberUpdatedState(onDone)
    // The caller's text wins: when it differs from what the field holds, the field takes it.
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth().fieldKeys(leaveWithDpad, !showKeyboardOnFocus, state),
        label = { Text(label) },
        supportingText = error?.let { message -> { Text(message) } },
        isError = error != null,
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = MaterialTheme.shapes.medium,
        inputTransformation = {
            val text = asCharSequence().toString()
            if (text != currentValue) currentOnValueChange(text)
        },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
                showKeyboardOnFocus = showKeyboardOnFocus,
            ),
        onKeyboardAction = { performDefaultAction ->
            currentOnDone?.invoke()
            performDefaultAction()
        },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
                errorTextColor = colors.ink,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                cursorColor = colors.accent,
                errorCursorColor = colors.dangerInk,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.lineStrong,
                errorBorderColor = colors.dangerInk,
                focusedLabelColor = colors.accent,
                unfocusedLabelColor = colors.ink3,
                errorLabelColor = colors.dangerInk,
                errorSupportingTextColor = colors.dangerInk,
            ),
    )
}

/**
 * The keys that the field handles before it sees them. It would move the focus on Up, Down and Left
 * at the start of the text by itself; [leaveWithDpad] decides who does it: with it the focus moves
 * to the neighbour and, when there is none, the key goes on to the field, which moves its caret;
 * without it the field keeps the key. With [keyboardOnOk] OK, Enter and DPAD_CENTER open the
 * keyboard when they are released, which is what a field that does not open it on focus needs; the
 * field does not see them, so Enter does not act as Done.
 */
@Composable
private fun Modifier.fieldKeys(
    leaveWithDpad: Boolean,
    keyboardOnOk: Boolean,
    field: TextFieldState,
): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return onPreviewKeyEvent { event ->
        when {
            keyboardOnOk && event.key in OK_KEYS -> {
                if (event.type == KeyEventType.KeyUp) keyboard?.show()
                true
            }
            event.type == KeyEventType.KeyDown -> {
                val atStart = field.selection.collapsed && field.selection.start == 0
                val direction =
                    when (event.key) {
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        Key.DirectionLeft -> FocusDirection.Left.takeIf { atStart }
                        else -> null
                    }
                direction != null && (!leaveWithDpad || focusManager.moveFocus(direction))
            }
            else -> false
        }
    }
}

private val OK_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
