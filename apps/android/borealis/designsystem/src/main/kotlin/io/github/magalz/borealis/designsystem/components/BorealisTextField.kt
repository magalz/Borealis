package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
) {
    val colors = LocalBorealisColors.current
    // The caret is part of the state, so the field can tell when Left has nowhere left to go.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    OutlinedTextField(
        value = shown,
        onValueChange = {
            field = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier.fillMaxWidth().dpadExit(leaveWithDpad, shown),
        label = { Text(label) },
        supportingText = error?.let { message -> { Text(message) } },
        isError = error != null,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    onDone?.invoke()
                    defaultKeyboardAction(ImeAction.Done)
                }
            ),
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
 * Hands the D-pad keys that the field has no use for to the focus: when the neighbour does not
 * exist the key goes on to the field, which moves its caret.
 */
@Composable
private fun Modifier.dpadExit(enabled: Boolean, field: TextFieldValue): Modifier {
    val focusManager = LocalFocusManager.current
    if (!enabled) return this
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            Key.DirectionLeft ->
                field.selection.collapsed &&
                    field.selection.start == 0 &&
                    focusManager.moveFocus(FocusDirection.Left)
            else -> false
        }
    }
}
