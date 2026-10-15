package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

/** How an action of the sheet looks: danger for what loses something. */
enum class ActionStyle {
    Normal,
    Danger,
}

/**
 * One action of an [ActionSheet]: the [label], its one-line [consequence] and a [key] to tell it
 * apart.
 */
class ActionSheetAction<out T>(
    val key: T,
    val label: String,
    val consequence: String,
    val style: ActionStyle = ActionStyle.Normal,
)

private val DIALOG_WIDTH = 420.dp
private val CONTENT_PADDING = 20.dp
/** The list sits 8dp from the edge, so the 12dp padding of a row puts its text at 20dp. */
private val LIST_PADDING = 8.dp
private val ACTION_GAP = 8.dp
private val ACTION_PADDING_HORIZONTAL = 12.dp
private val ACTION_PADDING_VERTICAL = 10.dp

/**
 * The actions of an item with the consequence under each label. On a phone it is a bottom sheet; on
 * a TV or handheld a centred dialog whose first action has focus. Back dismisses either. Choosing
 * an action calls [onAction] and leaves the sheet open: the screen closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ActionSheet(
    title: String,
    actions: List<ActionSheetAction<T>>,
    onAction: (ActionSheetAction<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (LocalFormFactor.current == FormFactor.Phone) {
        val colors = LocalBorealisColors.current
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = LocalBorealisShapes.current.sheet,
            containerColor = colors.surface,
            contentColor = colors.ink,
            scrimColor = colors.scrim,
        ) {
            ActionList(title, actions, onAction, focusFirst = false)
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val colors = LocalBorealisColors.current
            val shape = MaterialTheme.shapes.extraLarge
            Column(
                Modifier.width(DIALOG_WIDTH)
                    .background(colors.surface, shape)
                    .border(1.dp, colors.line, shape)
                    .clip(shape)
            ) {
                ActionList(title, actions, onAction, focusFirst = true)
            }
        }
    }
}

@Composable
private fun <T> ActionList(
    title: String,
    actions: List<ActionSheetAction<T>>,
    onAction: (ActionSheetAction<T>) -> Unit,
    focusFirst: Boolean,
) {
    val firstAction = remember { FocusRequester() }
    Column(
        Modifier.fillMaxWidth().padding(vertical = CONTENT_PADDING, horizontal = LIST_PADDING),
        verticalArrangement = Arrangement.spacedBy(ACTION_GAP),
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = ACTION_PADDING_HORIZONTAL),
            style = MaterialTheme.typography.titleLarge,
        )
        actions.forEachIndexed { index, action ->
            ActionRow(
                action,
                onClick = { onAction(action) },
                modifier =
                    if (focusFirst && index == 0) Modifier.initialFocus(firstAction) else Modifier,
            )
        }
    }
}

@Composable
private fun <T> ActionRow(action: ActionSheetAction<T>, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .borealisFocusIndication(shape)
            .clip(shape)
            .clickable(
                interactionSource = null,
                indication = BorealisPressIndication,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = ACTION_PADDING_HORIZONTAL, vertical = ACTION_PADDING_VERTICAL)
    ) {
        Text(
            action.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (action.style == ActionStyle.Danger) colors.dangerInk else colors.ink,
        )
        Text(
            action.consequence,
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
    }
}
