@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType

/**
 * An item of the [NavigationRail]. The screens pass the [icon]: the design system has no icon set,
 * so it does not pull in the Material icons library.
 */
class RailItem(val label: String, val icon: ImageVector)

private val RAIL_PADDING = 12.dp
/** An outline sits 5dp outside its item, so neighbours need 10dp between them. */
private val ITEM_GAP = 10.dp
private val ITEM_PADDING_HORIZONTAL = 12.dp
private val ITEM_PADDING_VERTICAL = 10.dp
private val ICON_GAP = 12.dp
private val ICON_SIZE = 20.dp

/**
 * The TV and handheld navigation rail: the chrome surface with one item per destination, an icon
 * and a label in the rail text style. The selected item has the accent tint and accent text. Every
 * item is focusable with the Borealis focus indication. A D-pad that enters the rail from the
 * content lands on the selected item, not on the nearest one.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NavigationRail(
    items: List<RailItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedFocus = remember { FocusRequester() }
    Column(
        modifier
            .fillMaxHeight()
            .background(LocalBorealisColors.current.chrome)
            .padding(RAIL_PADDING)
            .selectableGroup()
            .focusProperties { enter = { selectedFocus } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            RailEntry(
                item,
                selected = selected,
                onClick = { onSelect(index) },
                modifier = if (selected) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun RailEntry(
    item: RailItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    val content = if (selected) colors.accent else colors.ink2
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
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = ITEM_PADDING_HORIZONTAL, vertical = ITEM_PADDING_VERTICAL),
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
            tint = content,
        )
        Text(item.label, style = LocalBorealisType.current.rail, color = content)
    }
}
