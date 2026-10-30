package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

private const val DISABLED_ALPHA = 0.38f
private val GROUP_PADDING = 3.dp
private val SEGMENT_GAP = 2.dp
private val SEGMENT_MIN_HEIGHT_TV = 32.dp
/** The size that a finger needs (WCAG 2.5.8; Android and the accessibility checks ask 48dp). */
private val SEGMENT_MIN_TOUCH = 48.dp
private val SEGMENT_PADDING_HORIZONTAL = 9.dp
private val SEGMENT_PADDING_VERTICAL = 4.dp

/**
 * An inline group of exclusive [options] with [selected] one tinted in the accent, and its label in
 * the soft accent, which keeps 4.5:1 over the tint on the glass where the accent does not; with the
 * white accent it also has an indicator bar ([selectionBar]). Each segment is a radio button and a
 * focus stop with the Borealis focus indication: the D-pad moves between them and OK selects.
 * [label] names an option; [onSelect] gets the one the user chose. A disabled control is dimmed and
 * takes neither focus nor input.
 *
 * A segment is 48dp wide and high at least, except on a TV, which has no touch screen and keeps its
 * 32dp. [groupLabel] names what the control sets, and a screen reader says it after the name of a
 * segment, so that the Off of one control is not the Off of another.
 *
 * With [focusable] false the segments are not focus stops, and a tap or a screen reader still
 * selects one: for a control at the end of a row that owns the keys, as in a [MenuRow], where OK on
 * the row selects the next option. The control is a focus group, so a row cannot take its segments
 * out of the focus order itself.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    groupLabel: String? = null,
    focusable: Boolean = true,
) {
    val colors = LocalBorealisColors.current
    val touch = LocalFormFactor.current != FormFactor.Tv
    val selectedFocus = remember { FocusRequester() }
    val hasSelected = selected in options
    Row(
        modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .border(1.dp, colors.lineStrong, MaterialTheme.shapes.medium)
            .padding(GROUP_PADDING)
            .selectableGroup()
            .then(
                if (focusable) Modifier.selectedOnEnter(hasSelected, selectedFocus) else Modifier
            ),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        for (option in options) {
            val isSelected = option == selected
            val shape = MaterialTheme.shapes.small
            Box(
                (if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier)
                    .semantics {
                        if (groupLabel != null) contentDescription = "${label(option)}, $groupLabel"
                    }
                    .liftedWhenFocused()
                    .borealisFocusIndication(shape)
                    .background(if (isSelected) colors.accentTint else Color.Transparent, shape)
                    .clip(shape)
                    .selectionBar(isSelected, vertical = false)
                    .then(
                        if (focusable) Modifier else Modifier.focusProperties { canFocus = false }
                    )
                    .selectable(
                        selected = isSelected,
                        interactionSource = null,
                        indication = BorealisPressIndication,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .heightIn(min = if (touch) SEGMENT_MIN_TOUCH else SEGMENT_MIN_HEIGHT_TV)
                    .widthIn(min = if (touch) SEGMENT_MIN_TOUCH else 0.dp)
                    .padding(
                        horizontal = SEGMENT_PADDING_HORIZONTAL,
                        vertical = SEGMENT_PADDING_VERTICAL,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (isSelected) colors.accentSoft else colors.ink3,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * Entering the control by the D-pad lands on the selected segment, not the nearest. A request for
 * one segment (a tap, an initial focus) is not an entry and stays on it.
 */
private fun Modifier.selectedOnEnter(hasSelected: Boolean, selectedFocus: FocusRequester) =
    focusProperties {
        onEnter = {
            if (hasSelected && requestedFocusDirection != FocusDirection.Enter) {
                selectedFocus.requestFocus()
            }
        }
    }
    .focusGroup()
