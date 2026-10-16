package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A panel over the right of its parent, with a scrim over the rest. */
@Composable
fun SidePanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Stub: the implementation follows its failing tests.
}
