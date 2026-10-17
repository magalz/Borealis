package io.github.magalz.borealis.designsystem.components

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
 * Compose opens the keyboard whenever the field gains focus. With [showKeyboardOnFocus] false, for
 * a TV or a handheld, the D-pad focus does not go to the field but to a wrapper that draws the
 * field's focused look, so a remote can move through the screen without the keyboard covering it.
 * OK (DPAD_CENTER) on the wrapper, or a tap, starts editing: the focus moves into the field, which
 * opens the keyboard as it gains it. A field that is not being edited has no text input session, so
 * no keyboard can be asked for it. Editing ends when the field loses the focus, by Up, Down or
 * Left, or by Done, which takes the focus back to the wrapper. Enter on the wrapper is Done too.
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
    val transformation = InputTransformation {
        val text = asCharSequence().toString()
        if (text != reported) {
            reported = text
            edits++
            currentOnValueChange(text)
        }
    }
    val content = FieldContent(state, label, error, keyboardType, transformation)
    if (showKeyboardOnFocus) {
        TextFieldBody(
            content,
            modifier.fillMaxWidth().fieldKeys(leaveWithDpad, keyboardOnOk = false, state),
            interactionSource = null,
            onKeyboardAction = { performDefaultAction ->
                currentOnDone?.invoke()
                performDefaultAction()
            },
        )
    } else {
        EditOnOkField(content, modifier, leaveWithDpad) { currentOnDone?.invoke() }
    }
}

/** What the field shows and how it takes input; the same with or without the wrapper. */
private class FieldContent(
    val state: TextFieldState,
    val label: String,
    val error: String?,
    val keyboardType: KeyboardType,
    val transformation: InputTransformation,
)

/**
 * A field that is not edited until OK. Not editing, the D-pad focus is on a wrapper laid over the
 * field, and the field itself cannot take it: a focused field would not have a text input session
 * (it does not open the keyboard on focus), and a keyboard cannot be shown without one. OK, or a
 * tap, sets `editing`: the focus moves into the field, which does open the keyboard on focus. The
 * wrapper is a sibling of the field, not its parent, so it is a focus target of its own that the
 * D-pad search finds; [modifier] (the caller's focus requester and focus properties) sits on the
 * box around both and applies to either. When the field loses the focus, editing ends; after Done
 * the focus goes back to the wrapper, after Up, Down or Left it stays where it went.
 */
@Composable
private fun EditOnOkField(
    content: FieldContent,
    modifier: Modifier,
    leaveWithDpad: Boolean,
    onDone: () -> Unit,
) {
    val view = LocalView.current
    var editing by remember { mutableStateOf(false) }
    var fieldHadFocus by remember { mutableStateOf(false) }
    var backToWrapper by remember { mutableStateOf(false) }
    var wrapperFocused by remember { mutableStateOf(false) }
    val wrapper = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    // The wrapper draws the focused look of the field through this source.
    val look = remember { MutableInteractionSource() }
    val wrapperFocus = remember { FocusInteraction.Focus() }
    LaunchedEffect(wrapperFocused) {
        look.emit(if (wrapperFocused) wrapperFocus else FocusInteraction.Unfocus(wrapperFocus))
    }
    LaunchedEffect(editing) {
        if (editing) {
            if (!field.requestFocus()) {
                backToWrapper = true
                editing = false
            }
        } else if (backToWrapper) {
            backToWrapper = false
            wrapper.requestFocus()
        }
    }
    val startEditing = { editing = true }
    Box(modifier.fillMaxWidth()) {
        TextFieldBody(
            content,
            Modifier.fillMaxWidth()
                .focusRequester(field)
                .onFocusChanged {
                    if (it.isFocused) {
                        fieldHadFocus = true
                    } else if (fieldHadFocus) {
                        fieldHadFocus = false
                        editing = false
                    }
                }
                .focusProperties { canFocus = editing }
                .fieldKeys(leaveWithDpad, keyboardOnOk = true, content.state),
            interactionSource = look,
            onKeyboardAction = {
                onDone()
                view.hideKeyboard()
                backToWrapper = true
                editing = false
            },
        )
        Box(
            Modifier.matchParentSize()
                .focusRequester(wrapper)
                .onFocusChanged { wrapperFocused = it.isFocused }
                .onPreviewKeyEvent { event -> wrapperKey(event, startEditing, onDone) }
                .focusProperties { canFocus = !editing }
                .focusable()
                .semantics {
                    contentDescription = content.label
                    role = Role.Button
                    onClick {
                        startEditing()
                        true
                    }
                }
                // A tap before OK starts editing; the field, which cannot have the focus yet, does
                // not get it.
                .pointerInput(editing) { if (!editing) detectTapGestures { startEditing() } }
        )
    }
}

/** OK starts editing; Enter, as on a hardware keyboard, is Done. Everything else is the D-pad's. */
private fun wrapperKey(event: KeyEvent, startEditing: () -> Unit, onDone: () -> Unit): Boolean =
    when (event.key) {
        Key.DirectionCenter -> {
            if (event.type == KeyEventType.KeyUp) startEditing()
            true
        }
        Key.Enter,
        Key.NumPadEnter -> {
            if (event.type == KeyEventType.KeyDown) onDone()
            true
        }
        else -> false
    }

@Composable
private fun TextFieldBody(
    content: FieldContent,
    modifier: Modifier,
    interactionSource: MutableInteractionSource?,
    onKeyboardAction: KeyboardActionHandler,
) {
    val colors = LocalBorealisColors.current
    OutlinedTextField(
        state = content.state,
        modifier = modifier,
        label = { Text(content.label) },
        supportingText = content.error?.let { message -> { Text(message) } },
        isError = content.error != null,
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = MaterialTheme.shapes.medium,
        inputTransformation = content.transformation,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = content.keyboardType,
                imeAction = ImeAction.Done,
                // The keyboard opens as the field gains the focus. A field that must not do it
                // never has the focus until it is edited, so the wrapper decides.
                showKeyboardOnFocus = true,
            ),
        onKeyboardAction = onKeyboardAction,
        interactionSource = interactionSource,
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
 *
 * The keyboard is opened through the input method manager for the field's own input session. The
 * software keyboard controller would not do: it goes to the legacy text input service, which has no
 * session for a state-based field, so on a device it did nothing.
 */
@Composable
private fun Modifier.fieldKeys(
    leaveWithDpad: Boolean,
    keyboardOnOk: Boolean,
    field: TextFieldState,
): Modifier {
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    return onPreviewKeyEvent { event ->
        when {
            keyboardOnOk && event.key == Key.DirectionCenter -> {
                if (event.type == KeyEventType.KeyUp) view.showKeyboard()
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

/** Asks the system to hide the keyboard. */
private fun View.hideKeyboard() {
    context
        .getSystemService(InputMethodManager::class.java)
        ?.hideSoftInputFromWindow(windowToken, 0)
}

/** Asks the system to show the keyboard for the input session that the focused field holds. */
private fun View.showKeyboard() {
    context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
}
