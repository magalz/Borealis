package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** How a row of a menu looks: danger for what loses something. */
enum class MenuRowStyle {
    Normal,
    Danger,
}

/** One row of a menu. */
@Composable
fun MenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    consequence: String? = null,
    style: MenuRowStyle = MenuRowStyle.Normal,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    note: String? = null,
) {
    // Stub: the implementation follows its failing tests.
}
