@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

/**
 * One choice of a [ChoiceSheet]: the [key] it returns, its [label] and an optional [summary] under
 * the label.
 */
class ChoiceOption<out T>(val key: T, val label: String, val summary: String? = null)

private val DIALOG_WIDTH = 420.dp
private val CONTENT_PADDING = 20.dp
/** The list sits 8dp from the edge, so the 12dp padding of a row puts its text at 20dp. */
private val LIST_PADDING = 8.dp
/** An outline sits 5dp outside its row, so neighbouring rows need 10dp between them. */
private val OPTION_GAP = 10.dp
/** The first option's outline reaches 5dp above it: leave room under the title for it. */
private val LIST_TOP_PADDING = 16.dp
private val OPTION_PADDING_HORIZONTAL = 12.dp
private val OPTION_PADDING_VERTICAL = 10.dp
private val MARK_GAP = 12.dp

/**
 * A modal list of choices for one setting, such as Resolution or Frame rate: [options] under a
 * [title], with the one whose key is [selected] marked and holding the initial focus (the first
 * option when none is selected). On a phone it is a bottom sheet; on a TV or handheld a centred
 * dialog. The list scrolls and the title stays when the window is too short for all of it.
 *
 * A tap or OK on an option calls [onSelect] with its key and then [onDismiss]: the screen takes the
 * choice and closes the sheet. Back, or a tap outside the sheet, calls [onDismiss] alone, so
 * nothing changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val choose: (ChoiceOption<T>) -> Unit = {
        onSelect(it.key)
        onDismiss()
    }
    if (LocalFormFactor.current == FormFactor.Phone) {
        val colors = LocalBorealisColors.current
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = LocalBorealisShapes.current.sheet,
            containerColor = colors.surface,
            contentColor = colors.ink,
            scrimColor = colors.scrim,
        ) {
            OptionList(title, options, selected, choose)
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val colors = LocalBorealisColors.current
            val shape = MaterialTheme.shapes.extraLarge
            Column(
                Modifier.width(DIALOG_WIDTH)
                    .background(colors.surface, shape)
                    .border(1.dp, colors.line, shape)
                    .clip(shape)
            ) {
                OptionList(title, options, selected, choose)
            }
        }
    }
}

@Composable
private fun <T> OptionList(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onChoose: (ChoiceOption<T>) -> Unit,
) {
    val initial = remember { FocusRequester() }
    val initialIndex = options.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().semantics { paneTitle = title }) {
        Text(
            title,
            modifier =
                Modifier.padding(
                        start = CONTENT_PADDING,
                        top = CONTENT_PADDING,
                        end = CONTENT_PADDING,
                    )
                    .semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        // The padding is inside the scroll area: the area clips, and an outline reaches 5dp outside
        // its row.
        Column(
            Modifier.weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(
                    start = LIST_PADDING,
                    top = LIST_TOP_PADDING,
                    end = LIST_PADDING,
                    bottom = CONTENT_PADDING,
                ),
            verticalArrangement = Arrangement.spacedBy(OPTION_GAP),
        ) {
            options.forEachIndexed { index, option ->
                OptionRow(
                    option,
                    selected = option.key == selected,
                    onClick = { onChoose(option) },
                    modifier =
                        if (index == initialIndex) Modifier.initialFocus(initial) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun <T> OptionRow(
    option: ChoiceOption<T>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .fillMaxWidth()
            .borealisFocusIndication(shape)
            .background(if (selected) colors.accentTint else Color.Transparent, shape)
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = BorealisPressIndication,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = OPTION_PADDING_HORIZONTAL, vertical = OPTION_PADDING_VERTICAL),
        horizontalArrangement = Arrangement.spacedBy(MARK_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                option.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.accent else colors.ink,
            )
            if (option.summary != null) {
                Text(
                    option.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
            }
        }
        if (selected) CheckMark()
    }
}
