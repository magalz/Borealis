@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.liftedWhenFocused
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

private val TV_LIST_WIDTH = 220.dp
private val HANDHELD_LIST_WIDTH = 170.dp
private val PANE_GAP = 12.dp
/** An outline sits 5dp outside its item, so neighbours need 10dp between them. */
private val ITEM_GAP = 10.dp
private val AREA_PADDING = 8.dp
private val GROUP_PADDING_HORIZONTAL = 12.dp
private val GROUP_PADDING_VERTICAL = 10.dp

/**
 * The settings of a TV or handheld in two panes: the [groups] by title in a list on the left, and
 * on the right whatever [content] shows for the group at [selectedIndex]. The pane scrolls and
 * spaces its rows 10dp apart; [content] puts the rows of the group in it, with 8dp of padding
 * around them already given.
 *
 * Selection follows focus: when the D-pad moves focus to a group, [onSelect] is called with it, so
 * the pane changes as the user moves. A tap on a group selects it too. Right from the list enters
 * the pane at the row nearest to the group; Left from the pane, when no row uses the key, returns
 * to the selected group, whichever row it leaves.
 */
@Composable
fun <K> SettingsTwoPane(
    groups: List<SettingsGroup<K>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(K) -> Unit,
) {
    IndexedTwoPane(
        groups.map { it.title },
        groups.indexOfFirst { it.key == selected },
        { onSelect(groups[it].key) },
        modifier,
    ) {
        content(groups[it].key)
    }
}

/** One group of a [SettingsTwoPane]: the [key] that identifies it and its [title]. */
class SettingsGroup<out K>(val key: K, val title: String)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun IndexedTwoPane(
    groups: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.(Int) -> Unit,
) {
    val selectedGroup = remember { FocusRequester() }
    val listWidth =
        if (LocalFormFactor.current == FormFactor.Handheld) HANDHELD_LIST_WIDTH else TV_LIST_WIDTH
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PANE_GAP)) {
        Column(
            Modifier.width(listWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(AREA_PADDING)
                .selectableGroup()
                // Entering the list by the D-pad lands on the selected group, not the nearest. A
                // request for one group (a tap, an initial focus) is not an entry and stays on it.
                .focusProperties {
                    enter = { direction ->
                        if (direction != FocusDirection.Enter) selectedGroup
                        else FocusRequester.Default
                    }
                }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            groups.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                GroupEntry(
                    title,
                    selected,
                    onSelect = { onSelect(index) },
                    modifier = if (selected) Modifier.focusRequester(selectedGroup) else Modifier,
                )
            }
        }
        Box(
            Modifier.weight(1f)
                .fillMaxHeight()
                .onKeyEvent { event ->
                    // No row used Left: go back to the selected group.
                    val left = event.key == Key.DirectionLeft
                    if (left && event.type == KeyEventType.KeyDown) selectedGroup.requestFocus()
                    left
                }
                .focusGroup()
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AREA_PADDING),
                verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
            ) {
                content(selectedIndex)
            }
        }
    }
}

@Composable
private fun GroupEntry(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .fillMaxWidth()
            .liftedWhenFocused()
            // Selection follows focus. The selected group already has it when focus returns to it.
            .onFocusChanged { if (it.isFocused && !selected) onSelect() }
            .borealisFocusIndication(shape)
            .background(if (selected) colors.accentTint else Color.Transparent, shape)
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = BorealisPressIndication,
                role = Role.Tab,
                onClick = onSelect,
            )
            .padding(horizontal = GROUP_PADDING_HORIZONTAL, vertical = GROUP_PADDING_VERTICAL)
    ) {
        Text(
            title,
            color = if (selected) colors.accent else colors.ink2,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
