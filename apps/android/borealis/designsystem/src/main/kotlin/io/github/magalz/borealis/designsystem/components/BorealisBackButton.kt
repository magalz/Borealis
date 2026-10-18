package io.github.magalz.borealis.designsystem.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The arrow that goes back: [BorealisIcons.Back] in an icon button, with a touch target of 48dp.
 * [contentDescription] is what a screen reader says, so it names where the arrow goes. A disabled
 * arrow does not call [onClick], for a screen that must not close while it works.
 */
@Composable
fun BorealisBackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(BorealisIcons.Back, contentDescription)
    }
}
