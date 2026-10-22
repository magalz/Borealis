package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import io.github.magalz.borealis.designsystem.components.BorealisIcons
import io.github.magalz.borealis.designsystem.components.OverlaySheet
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.ignoreKeysHeldOnOpen
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.theme.borealisTypography

/**
 * One choice of a [ChoiceSheet]: the [key] it returns, its [label] and an optional [summary] under
 * the label. A [labelFont] draws the label in that font, at the size that the app has in it, so a
 * choice of fonts shows each name as the app would draw it. The summary stays in the font of the
 * app.
 */
class ChoiceOption<out T>(
    val key: T,
    val label: String,
    val summary: String? = null,
    val labelFont: FontChoice? = null,
)

private val CONTENT_PADDING = 20.dp
/**
 * The first option's outline reaches 5dp above it, 7dp when thicker: leave room under the title.
 */
private val LIST_TOP_PADDING = 16.dp
/** The list sits 8dp from the edge, so the 12dp padding of a row puts its text at 20dp. */
private val OPTION_PADDING_HORIZONTAL = 12.dp
private val OPTION_PADDING_VERTICAL = 10.dp
private val MARK_GAP = 12.dp

/**
 * A modal list of choices for one setting, such as Resolution or Frame rate: [options] under a
 * [title], with the one whose key is [selected] marked, by the accent tint and by its label and a
 * check in the soft accent (which keep 4.5:1 over the tint on the glass), and holding the initial
 * focus (the first option when none is selected). On a phone it is a bottom sheet; on a TV or
 * handheld a centred dialog, 420dp wide on a TV and a fraction of the window on a handheld (see
 * [BorealisDimens.sheetWidth]). The list scrolls and the title stays when the window is too short
 * for all of it. Over a glass host that blurs its window the sheet draws no scrim, its surface is
 * the tint of the glass over the blurred window, and its text has the glass colours.
 *
 * A tap or OK on an option calls [onSelect] with its key and then [onDismiss]: the screen takes the
 * choice and closes the sheet. Back, or a tap outside the sheet, calls [onDismiss] alone, so
 * nothing changes.
 */
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
    OverlaySheet(onDismiss) { OptionList(title, options, selected, choose) }
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
    Column(Modifier.fillMaxWidth().ignoreKeysHeldOnOpen().semantics { paneTitle = title }) {
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
        // its row, 7dp when it is the thicker one.
        Column(
            Modifier.weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(
                    start = BorealisDimens.listPadding,
                    top = LIST_TOP_PADDING,
                    end = BorealisDimens.listPadding,
                    bottom = CONTENT_PADDING,
                ),
            verticalArrangement = Arrangement.spacedBy(BorealisDimens.listGap),
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
    val formFactor = LocalFormFactor.current
    val labelStyle =
        remember(option.labelFont, formFactor) {
            option.labelFont?.let { borealisTypography(formFactor, it).titleMedium }
        } ?: MaterialTheme.typography.titleMedium
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
                style = labelStyle,
                color = if (selected) colors.accentSoft else colors.ink,
            )
            if (option.summary != null) {
                Text(
                    option.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
            }
        }
        if (selected) {
            Icon(
                BorealisIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(ROW_ICON_SIZE),
                tint = colors.accentSoft,
            )
        }
    }
}
