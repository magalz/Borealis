package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

private const val DISABLED_ALPHA = 0.38f
private val GROUP_PADDING = 3.dp
private val SEGMENT_GAP = 2.dp
private val SEGMENT_MIN_HEIGHT = 32.dp
private val SEGMENT_PADDING_HORIZONTAL = 12.dp
private val SEGMENT_PADDING_VERTICAL = 4.dp

/**
 * An inline group of exclusive [options] with [selected] one tinted in the accent. Each segment is
 * a radio button and a focus stop with the Borealis focus indication: the D-pad moves between them
 * and OK selects. [label] names an option; [onSelect] gets the one the user chose. A disabled
 * control is dimmed and takes neither focus nor input.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalBorealisColors.current
    Row(
        modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .border(1.dp, colors.lineStrong, MaterialTheme.shapes.medium)
            .padding(GROUP_PADDING)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        for (option in options) {
            val isSelected = option == selected
            val shape = MaterialTheme.shapes.small
            Box(
                Modifier.liftedWhenFocused()
                    .borealisFocusIndication(shape)
                    .background(if (isSelected) colors.accentTint else Color.Transparent, shape)
                    .clip(shape)
                    .selectable(
                        selected = isSelected,
                        interactionSource = null,
                        indication = BorealisPressIndication,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .heightIn(min = SEGMENT_MIN_HEIGHT)
                    .padding(
                        horizontal = SEGMENT_PADDING_HORIZONTAL,
                        vertical = SEGMENT_PADDING_VERTICAL,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (isSelected) colors.accent else colors.ink3,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
