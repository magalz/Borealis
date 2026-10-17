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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
 * opens only when the user taps the field or presses DPAD_CENTER, so a remote can move through the
 * field without the keyboard covering the screen. Enter is still Done, as it is on a hardware
 * keyboard and on an input method that sends Done as an Enter key.
 *
 * The caller owns the text: if [onValueChange] does not lead to a new [value], the field shows
 * [value] again. So [value] must follow [onValueChange] before the next frame (state updated on the
 * main thread, as a view model's state flow does); a caller that updates it later would see its
 * text flicker back.
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
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnDone by rememberUpdatedState(onDone)
    // The last text that went to the caller, so an edit is reported once, and an edit that undoes
    // the previous one is reported too, even before the caller's value has caught up.
    var reported by remember { mutableStateOf(value) }
    var edits by remember { mutableIntStateOf(0) }
    // The caller's text wins: after an edit and when [value] changes, a field that holds something
    // else takes the caller's text back. Setting the state here is not a user edit, so it is not
    // reported again.
    LaunchedEffect(value, edits) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
        reported = value
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
            if (text != reported) {
                reported = text
                edits++
                currentOnValueChange(text)
            }
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
 * without it the field keeps the key. With [keyboardOnOk] DPAD_CENTER, the remote's OK and what a
 * gamepad's A falls back to, opens the keyboard when it is released, which is what a field that
 * does not open it on focus needs. Enter is not OK: it reaches the field and acts as Done.
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
            keyboardOnOk && event.key == Key.DirectionCenter -> {
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
