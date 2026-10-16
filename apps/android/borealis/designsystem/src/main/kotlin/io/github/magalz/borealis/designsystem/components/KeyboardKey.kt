package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** How wide a key of the keyboard is. */
enum class KeyStyle {
    Normal,
    Wide,
    Space,
}

/** One key of the keyboard. */
@Composable
fun KeyboardKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyStyle = KeyStyle.Normal,
    latched: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    // Stub: the implementation follows its failing tests.
}
