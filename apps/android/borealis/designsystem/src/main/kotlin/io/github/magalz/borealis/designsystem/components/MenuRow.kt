@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

/** How a row of a menu looks: danger for what loses something. */
enum class MenuRowStyle {
    Normal,
    Danger,
}

private const val DISABLED_ALPHA = 0.38f
private val PHONE_MIN_HEIGHT = 44.dp
private val TV_MIN_HEIGHT = 48.dp
private val PADDING_HORIZONTAL = 16.dp
private val PADDING_VERTICAL = 4.dp
private val TRAILING_GAP = 12.dp

/**
 * One row of a menu: a [title], optionally what it does to the user's session or work under it as
 * [consequence], and a [trailing] control or chevron at the end. [MenuRowStyle.Danger] sets the
 * title in the danger ink. A tap or OK runs [onClick].
 *
 * A row that changes an option on [onClick] gives the current [value], which a screen reader says
 * after the title, and [onClickLabel], which names what activating the row does.
 *
 * A disabled row is dimmed and does not react to OK, but it stays a focus stop: on a TV the user
 * can still land on it and read the [note] that says why. The note stays at full strength. The row
 * does not dim or disable its [trailing] control: the caller passes `enabled = false` to the
 * control itself, or leaves it out, so a control is dimmed once and by the one that owns it.
 *
 * The outline of a focused row reaches 5dp outside it, so a list of rows needs 8dp of padding
 * around it and 10dp between rows, as in the [ActionSheet].
 */
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
    value: String? = null,
    onClickLabel: String? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val minHeight =
        if (LocalFormFactor.current == FormFactor.Phone) PHONE_MIN_HEIGHT else TV_MIN_HEIGHT
    val dim = if (enabled) 1f else DISABLED_ALPHA
    val rowFocus = remember { FocusRequester() }
    val trailingFocus = remember { FocusRequester() }
    Row(
        modifier
            .focusRequester(rowFocus)
            .then(
                // The control is inside the row, so the focus search would not reach it: point at
                // it.
                if (trailing == null) Modifier else Modifier.focusProperties { end = trailingFocus }
            )
            .liftedWhenFocused()
            .borealisFocusIndication(shape)
            .clip(shape)
            .then(if (value == null) Modifier else Modifier.semantics { stateDescription = value })
            .rowAction(enabled, onClickLabel, onClick)
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(horizontal = PADDING_HORIZONTAL, vertical = PADDING_VERTICAL),
        horizontalArrangement = Arrangement.spacedBy(TRAILING_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, consequence, note, style, dim, Modifier.weight(1f))
        if (trailing != null) TrailingSlot(rowFocus, trailingFocus, trailing)
    }
}

/** A tap or OK runs [onClick]; a disabled row only stays a focus stop that is announced so. */
private fun Modifier.rowAction(
    enabled: Boolean,
    onClickLabel: String?,
    onClick: () -> Unit,
): Modifier =
    if (enabled) {
        clickable(
            interactionSource = null,
            indication = BorealisPressIndication,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        // A disabled clickable is not focusable, and the row must stay a focus stop.
        focusable().semantics(mergeDescendants = true) {
            role = Role.Button
            disabled()
        }
    }

@Composable
private fun RowText(
    title: String,
    consequence: String?,
    note: String?,
    style: MenuRowStyle,
    dim: Float,
    modifier: Modifier,
) {
    val colors = LocalBorealisColors.current
    Column(modifier) {
        Text(
            title,
            modifier = Modifier.alpha(dim),
            color = if (style == MenuRowStyle.Danger) colors.dangerInk else colors.ink,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (consequence != null) {
            Text(
                consequence,
                modifier = Modifier.alpha(dim),
                color = colors.ink3,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (note != null) {
            Text(note, color = colors.ink3, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The control at the end of a row, a focus group the D-pad enters from the row and leaves to it.
 */
@Composable
private fun TrailingSlot(
    rowFocus: FocusRequester,
    trailingFocus: FocusRequester,
    trailing: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Box(
        Modifier.focusRequester(trailingFocus)
            // Inherited from the row: reset, or Right would loop back to the control.
            .focusProperties { end = FocusRequester.Default }
            .onKeyEvent { event ->
                // The row is an ancestor of the control, so the search never finds it to the left:
                // when nothing is left of the focused segment, go back to the row. (A focus group's
                // `exit` property does not return focus to an ancestor.)
                val left = event.key == Key.DirectionLeft
                if (left && event.type == KeyEventType.KeyDown) {
                    if (!focusManager.moveFocus(FocusDirection.Left)) rowFocus.requestFocus()
                }
                left
            }
            .focusGroup()
    ) {
        trailing()
    }
}
