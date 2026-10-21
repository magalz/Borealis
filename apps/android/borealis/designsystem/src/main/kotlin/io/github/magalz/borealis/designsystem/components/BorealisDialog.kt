package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.magalz.borealis.designsystem.focus.ignoreKeysHeldOnOpen
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.glass.OverlayOpen
import io.github.magalz.borealis.designsystem.glass.rememberOverlayGlass
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.windowWidth
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/**
 * A button of a [BorealisDialog]: its [label], what it does and how it looks. A null [style] takes
 * the default of the slot: Primary for the confirm action, Secondary for the dismiss action and
 * Text for the extra ones.
 */
class DialogAction(
    val label: String,
    val style: BorealisButtonStyle? = null,
    val onClick: () -> Unit,
)

private val TITLE_GAP = 12.dp
private val ACTIONS_GAP = 24.dp
private val BUTTON_GAP = 12.dp

/**
 * The one dialog frame: a surface with the sheet radius and a hairline, over the scrim, in one of
 * the three [DialogWidth]s (on a phone the narrower of the token and the window less 16dp on each
 * side). It has a [title] in the title style, a [content] slot and, at the foot, the [confirm]
 * action on the right with the [dismiss] action on its left; [extraActions] sit at the start of the
 * row. Padding is 24dp all round. The content scrolls and the title and actions stay in view when
 * the window is too short.
 *
 * Over a glass host that blurs its window the dialog draws no scrim, its surface is the tint of the
 * glass over the blurred window, and its text has the glass colours.
 *
 * The confirm action has focus when the dialog opens, so a D-pad or a gamepad works at once. Set
 * [focusDismiss] when the safe choice is the dismiss action (Cancel on an erase) and it has focus
 * instead; without a dismiss action the confirm action keeps it. An action calls its callback and
 * leaves the dialog open: the screen closes it. Back, or a tap on the scrim, calls
 * [onDismissRequest]. [modifier] applies to the dialog surface.
 */
@Composable
fun BorealisDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirm: DialogAction,
    modifier: Modifier = Modifier,
    width: DialogWidth = DialogWidth.Compact,
    dismiss: DialogAction? = null,
    extraActions: List<DialogAction> = emptyList(),
    focusDismiss: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.extraLarge
    val windowWidth = windowWidth()
    val margin = BorealisTokens.Space.DialogMargin
    val glass = rememberOverlayGlass()
    OverlayOpen()
    Dialog(
        onDismissRequest = onDismissRequest,
        // The window draws under the system bars, so the scrim covers the whole screen; the
        // dialog itself stays inside the safe insets.
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        NoPlatformDim()
        Box(
            Modifier.fillMaxSize()
                .ignoreKeysHeldOnOpen()
                // Over a blurred window there is no scrim: the glass is the dimmer.
                .then(if (glass == null) Modifier.background(colors.scrim) else Modifier)
                // A tap on the scrim dismisses. It is not a clickable: that would be a focus stop.
                .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = margin),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .width(BorealisDimens.dialogWidth(width, windowWidth))
                    .overlayPanel(shape, glass)
                    .border(1.dp, colors.line, shape)
                    .clip(shape)
                    // A tap on the dialog is not a tap on the scrim.
                    .pointerInput(Unit) { detectTapGestures {} }
                    .padding(BorealisTokens.Space.DialogPadding)
                    .semantics { paneTitle = title }
            ) {
                OverlayTextColors(glass) {
                    DialogBody(title, content)
                    DialogFooter(confirm, dismiss, extraActions, focusDismiss)
                }
            }
        }
    }
}

/** The scrim is the theme's, so the platform's dim of the dialog window goes. */
@Composable
internal fun NoPlatformDim() {
    val view = LocalView.current
    SideEffect {
        (view as? DialogWindowProvider ?: view.parent as? DialogWindowProvider)
            ?.window
            ?.setDimAmount(0f)
    }
}

/** The title, and under it the content, which scrolls when the window is too short for it. */
@Composable
private fun ColumnScope.DialogBody(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(TITLE_GAP))
    Column(
        Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(TITLE_GAP),
        content = content,
    )
    Spacer(Modifier.height(ACTIONS_GAP))
}

/** The extra actions at the start, then the dismiss action and the confirm action on the right. */
@Composable
private fun DialogFooter(
    confirm: DialogAction,
    dismiss: DialogAction?,
    extraActions: List<DialogAction>,
    focusDismiss: Boolean,
) {
    val confirmFocus = remember { FocusRequester() }
    val dismissFocus = remember { FocusRequester() }
    val focusConfirm = !focusDismiss || dismiss == null
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        extraActions.forEach { ActionButton(it, BorealisButtonStyle.Text) }
        Spacer(Modifier.weight(1f))
        dismiss?.let {
            ActionButton(
                it,
                BorealisButtonStyle.Secondary,
                if (focusConfirm) Modifier else Modifier.initialFocus(dismissFocus),
            )
        }
        ActionButton(
            confirm,
            BorealisButtonStyle.Primary,
            if (focusConfirm) Modifier.initialFocus(confirmFocus) else Modifier,
        )
    }
}

@Composable
private fun ActionButton(
    action: DialogAction,
    defaultStyle: BorealisButtonStyle,
    modifier: Modifier = Modifier,
) {
    BorealisButton(
        action.label,
        action.onClick,
        modifier,
        style = action.style ?: defaultStyle,
    )
}
