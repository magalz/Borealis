@file:Suppress("UNUSED_PARAMETER")

package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A row with a slider and the formatted value. */
@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Stub: the behaviour comes with the implementation.
}
