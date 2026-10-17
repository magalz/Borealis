package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.DialogWidth

/** A button of a [BorealisDialog]: its [label], what it does and how it looks. */
class DialogAction(
    val label: String,
    val style: BorealisButtonStyle? = null,
    val onClick: () -> Unit,
)

/** The one dialog frame; see the implementation commit. */
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
) {}
