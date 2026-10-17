@file:Suppress("UNUSED_PARAMETER")

package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The settings of a TV or handheld: the groups on the left, the selected one on the right. */
@Composable
fun SettingsTwoPane(
    groups: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(Int) -> Unit,
) {
    // Stub: the behaviour comes with the implementation.
}
