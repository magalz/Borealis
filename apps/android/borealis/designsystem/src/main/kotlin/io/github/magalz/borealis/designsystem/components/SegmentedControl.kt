package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** An inline group of exclusive options, one of them selected. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Stub: the implementation follows its failing tests.
}
