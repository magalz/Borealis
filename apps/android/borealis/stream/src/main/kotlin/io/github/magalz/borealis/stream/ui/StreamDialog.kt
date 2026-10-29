package io.github.magalz.borealis.stream.ui

import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import io.github.magalz.borealis.stream.ui.common.onBackKey
import io.github.magalz.borealis.stream.ui.common.onGamepadB
import kotlinx.coroutines.flow.filter

private val DIALOG_GAP = 12.dp
private val BUTTON_GAP = 12.dp

/** A button of a [StreamDialog]: its [label], how it looks and what it does. */
internal class StreamDialogAction(
    val label: String,
    val style: BorealisButtonStyle,
    val onClick: () -> Unit,
)

/**
 * A dialog inside the stream chrome: a scrim over the whole layer and, centred on it, a panel with
 * the surface, the compact width and the padding of a `BorealisDialog`, the [title], the [content]
 * and the [actions] as buttons. The conflict dialog and the Quit popup of the menu are made from
 * it.
 *
 * It is not a `BorealisDialog`, on purpose. That one is a platform dialog window, and the stream
 * host owns the input: `StreamChromeHost.dispatchKeyEvent` routes the gamepad and the keys to the
 * chrome root, which a dialog window would bypass (gamepad B, the D-pad focus and the Back rule).
 *
 * The scrim takes the touches, so nothing under the dialog is reached. The first of [actions] has
 * the initial focus, so it is the safe one (Cancel): a stray OK changes nothing. The focus stays in
 * the dialog, Up, Down, Left and Right move between the buttons and stop at the last. The gamepad B
 * button, the Back key and Back of the system call [onDismiss] and are used up here, so neither
 * reaches what is under the dialog nor the host's callback, which closes the open panel.
 *
 * The panel is announced by its [title], which is its heading. Three buttons or more stack on a
 * phone, where they do not fit side by side. [modifier] applies to the panel.
 */
@Composable
internal fun StreamDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: List<StreamDialogAction>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(actions.isNotEmpty()) { "A stream dialog has at least one action" }
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.extraLarge
    DismissOnSystemBack(onDismiss)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(colors.scrim)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onGamepadB(onDismiss)
            .onBackKey(onDismiss)
            // A tap on the scrim stays here: it does nothing, and nothing under it takes it.
            .pointerInput(Unit) { detectTapGestures() }
            .keepFocusInside(),
        contentAlignment = Alignment.Center,
    ) {
        // The padding is inside the scroll area: the area clips, and a focus outline reaches
        // outside its button.
        Column(
            modifier
                .semantics { paneTitle = title }
                .width(BorealisDimens.dialogWidth(DialogWidth.Compact, maxWidth))
                .background(colors.surface, shape)
                .border(1.dp, colors.line, shape)
                .clip(shape)
                .verticalScroll(rememberScrollState())
                .padding(BorealisTokens.Space.DialogPadding),
            verticalArrangement = Arrangement.spacedBy(DIALOG_GAP),
        ) {
            Text(
                title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            content()
            Actions(actions)
        }
    }
}

@Composable
private fun Actions(actions: List<StreamDialogAction>) {
    val firstFocus = remember { FocusRequester() }
    FocusOnKeyboard(firstFocus)
    val stacked =
        actions.size > MAX_SIDE_BY_SIDE_ON_A_PHONE && LocalFormFactor.current == FormFactor.Phone
    val width = if (stacked) Modifier.fillMaxWidth() else Modifier
    val buttons: @Composable () -> Unit = {
        actions.forEachIndexed { index, action ->
            BorealisButton(
                action.label,
                action.onClick,
                if (index == 0) width.initialFocus(firstFocus) else width,
                action.style,
            )
        }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) { buttons() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) { buttons() }
    }
}

private const val MAX_SIDE_BY_SIDE_ON_A_PHONE = 2

/**
 * Puts the focus on [first] when the window goes into the keyboard input mode. A phone opens the
 * dialog in touch mode, where a button cannot take the focus, so the initial focus does nothing
 * there; the first key of a D-pad, a pad or a keyboard leaves touch mode and the focus comes then,
 * to the safe button and not to whatever else the chrome has in reach.
 */
@Composable
private fun FocusOnKeyboard(first: FocusRequester) {
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(inputMode) {
        snapshotFlow { inputMode.inputMode }
            .filter { it == InputMode.Keyboard }
            .collect { first.requestFocus() }
    }
}

/**
 * The focus does not leave the dialog in any direction, so the D-pad never reaches what is under
 * it.
 */
private fun Modifier.keepFocusInside(): Modifier = focusProperties {
    onExit = { cancelFocusChange() }
}
    .focusGroup()

/**
 * Back of the system (the gesture, or the key when nothing in the dialog has the focus, as on a
 * phone in touch mode) calls [onDismiss] while the dialog is shown. The callback is added after the
 * host's, so it is asked first, and it goes when the dialog does.
 */
@Composable
private fun DismissOnSystemBack(onDismiss: () -> Unit) {
    val owner =
        checkNotNull(LocalView.current.findViewTreeOnBackPressedDispatcherOwner()) {
            "A stream dialog needs the OnBackPressedDispatcherOwner of its activity"
        }
    val latest = rememberUpdatedState(onDismiss)
    DisposableEffect(owner) {
        val callback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = latest.value()
            }
        owner.onBackPressedDispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}
