package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/**
 * A single-line text field: the input radius, a line-strong border that turns accent on focus, and
 * [error] under it in danger-ink (it also turns the border and label to danger-ink). The keyboard
 * action is Done, which calls [onDone].
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
) {
    val colors = LocalBorealisColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = error?.let { message -> { Text(message) } },
        isError = error != null,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
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
