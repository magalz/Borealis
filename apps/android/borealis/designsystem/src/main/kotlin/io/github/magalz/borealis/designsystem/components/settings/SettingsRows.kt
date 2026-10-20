package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisIcons
import io.github.magalz.borealis.designsystem.components.MenuRow
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import kotlin.math.roundToInt

/** How a row of settings looks: danger for what loses something, such as an erase. */
enum class SettingsRowTone {
    Normal,
    Danger,
}

private val CONTENT_GAP = 12.dp
private const val VALUE_MAX_SHARE = 0.4f

/**
 * A row with a [title], an optional [summary] and a switch at the end. A tap or OK toggles it by
 * calling [onCheckedChange] with the opposite state; a screen reader says On or Off after the
 * title.
 *
 * The row is the full-screen form of [MenuRow]: the same focus outline, press wash, disabled rule
 * and danger ink, but larger (48dp on a phone, 52dp on a TV or handheld), with a value, a summary
 * or a control of its own. It is a separate family so that a menu row stays compact on the stream.
 *
 * A disabled row is dimmed and does not react, but it stays a focus stop: on a TV the user can
 * still land on it and read [disabledNote], which says why it is disabled and shows only then.
 *
 * The outline of a focused row reaches 5dp outside it, so a list of rows needs 8dp of padding
 * around it and 10dp between rows.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    disabledNote: String? = null,
) {
    val colors = LocalBorealisColors.current
    Row(
        modifier
            .settingsRowSurface()
            .then(
                if (enabled) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = null,
                        indication = BorealisPressIndication,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    // A disabled toggleable is not focusable, and the row must stay a focus stop.
                    Modifier.focusable().semantics(mergeDescendants = true) {
                        role = Role.Switch
                        toggleableState = ToggleableState(checked)
                        disabled()
                    }
                }
            )
            .settingsRowSize(),
        horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(
            title,
            summary,
            disabledNote,
            enabled,
            SettingsRowTone.Normal,
            Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            // The row owns the input, so the switch is only drawn.
            onCheckedChange = null,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.accent,
                    checkedBorderColor = colors.accent,
                    uncheckedThumbColor = colors.ink3,
                    uncheckedTrackColor = colors.lineStrong,
                    uncheckedBorderColor = colors.lineStrong,
                ),
        )
    }
}

/**
 * A row with a [title], an optional [summary], the current [value] at the end and a chevron. A tap
 * or OK runs [onClick]; a screen reader says [onClickLabel], when given, for what activating the
 * row does. A null [value] makes a link or an action row, such as Audit trail or Export diagnostic
 * bundle. A long title or value wraps; nothing is cut.
 *
 * The row is the full-screen form of [MenuRow]: the same focus outline, press wash, disabled rule
 * and danger ink, but larger (48dp on a phone, 52dp on a TV or handheld), with a value, a summary
 * or a control of its own. It is a separate family so that a menu row stays compact on the stream.
 *
 * [SettingsRowTone.Danger] sets the title in the danger ink and leaves out the chevron: the row
 * does not open anything, it does something that loses something, and the screen asks before it
 * goes on.
 *
 * A disabled row is dimmed and does not react, but it stays a focus stop: on a TV the user can
 * still land on it and read [disabledNote], which says why it is disabled and shows only then.
 *
 * The outline of a focused row reaches 5dp outside it, so a list of rows needs 8dp of padding
 * around it and 10dp between rows.
 */
@Composable
fun SettingsValueRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    disabledNote: String? = null,
    tone: SettingsRowTone = SettingsRowTone.Normal,
    onClickLabel: String? = null,
) {
    Row(
        modifier
            .settingsRowSurface()
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = BorealisPressIndication,
                        onClickLabel = onClickLabel,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    // A disabled clickable is not focusable, and the row must stay a focus stop.
                    Modifier.focusable().semantics(mergeDescendants = true) {
                        role = Role.Button
                        disabled()
                    }
                }
            )
            .settingsRowSize(),
        horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(title, summary, disabledNote, enabled, tone, Modifier.weight(1f))
        if (value != null) ValueText(value, enabled)
        if (tone == SettingsRowTone.Normal) {
            Icon(
                BorealisIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(ROW_ICON_SIZE),
                tint = LocalBorealisColors.current.ink3,
            )
        }
    }
}

/**
 * A row that only reports a [value] beside its [title], such as the version in About. It draws like
 * a [SettingsValueRow] without the chevron, because nothing opens from it, and it has no click
 * action and no button role. It stays a focus stop, so a D-pad scrolls a list through it; a screen
 * reader reads the title and the value as one text.
 */
@Composable
fun SettingsInfoRow(title: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .settingsRowSurface()
            .focusable()
            .semantics(mergeDescendants = true) {}
            .settingsRowSize(),
        horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(title, null, null, true, SettingsRowTone.Normal, Modifier.weight(1f))
        ValueText(value, enabled = true)
    }
}

@Composable
private fun ValueText(value: String, enabled: Boolean) {
    Text(
        value,
        // The value takes what it needs up to a share of the row, so a long one wraps beside the
        // title instead of pushing it out.
        modifier = Modifier.maxWidthShare(VALUE_MAX_SHARE),
        color = LocalBorealisColors.current.ink3.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.End,
    )
}

/**
 * Limits the width to [share] of what the parent offers. A Row measures a child without weight
 * first, with all of its width on offer, so the value is limited before the title takes the rest.
 */
private fun Modifier.maxWidthShare(share: Float): Modifier = layout { measurable, constraints ->
    val limit = (constraints.maxWidth * share).roundToInt().coerceAtLeast(constraints.minWidth)
    val placeable = measurable.measure(constraints.copy(maxWidth = limit))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
