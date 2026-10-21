package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.magalz.borealis.designsystem.R
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.glass.GlassTextColors
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/**
 * An item of the [NavigationRail]. The screens pass the [icon]: the design system has no icon set,
 * so it does not pull in the Material icons library.
 */
class RailItem(val label: String, val icon: ImageVector)

private val RAIL_PADDING = 12.dp
private val ITEM_PADDING_HORIZONTAL = 12.dp
private val ITEM_PADDING_VERTICAL = 10.dp
private val ICON_GAP = 12.dp
private val ICON_SIZE = 20.dp
private val MARK_SIZE = 24.dp
/**
 * The mark is wider than an icon, so it starts inside the item padding and shares their centre
 * line.
 */
private val FOOTER_PADDING_HORIZONTAL = ITEM_PADDING_HORIZONTAL - (MARK_SIZE - ICON_SIZE) / 2
/** The name starts where the labels of the items do. */
private val FOOTER_GAP =
    ITEM_PADDING_HORIZONTAL + ICON_SIZE + ICON_GAP - FOOTER_PADDING_HORIZONTAL - MARK_SIZE

/**
 * The TV and handheld navigation rail: a glass surface (the chrome colour where the glass is solid)
 * with one item per destination, an icon and a label in the rail text style. The selected item has
 * the accent tint and accent text. Every item is focusable with the Borealis focus indication. It
 * is as wide as [BorealisDimens.navWidth] says, so a screen does not give it a width. A D-pad that
 * enters the rail from the content lands on the selected item, not on the nearest one.
 *
 * The [footer], when there is one, sits at the bottom of the room the items leave, and the items do
 * not move for it. It takes no focus, whatever it holds. A rail too short for it does not show it,
 * so it never overlaps an item. It draws no background: it is on the glass of the rail, and its
 * text has the glass colours.
 */
@Composable
fun NavigationRail(
    items: List<RailItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    val selectedFocus = remember { FocusRequester() }
    Column(
        modifier
            .width(BorealisDimens.navWidth(LocalFormFactor.current))
            .fillMaxHeight()
            .glassOrSolid(RectangleShape, LocalBorealisColors.current.chrome)
            .padding(RAIL_PADDING)
            .selectableGroup()
            // Entering the rail by the D-pad lands on the selected item, not the nearest.
            // A request for one item (a tap, an initial focus) is not an entry and stays on it.
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection != FocusDirection.Enter)
                        selectedFocus.requestFocus()
                }
            }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(BorealisDimens.listGap),
    ) {
        GlassTextColors {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                RailEntry(
                    item,
                    selected = selected,
                    onClick = { onSelect(index) },
                    modifier = if (selected) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
            if (footer != null) FooterSlot(footer, Modifier.weight(1f))
        }
    }
}

/**
 * The room the items leave, with the [footer] at its bottom. A footer taller than the room is not
 * placed, so it is neither drawn nor seen by a screen reader.
 */
@Composable
private fun FooterSlot(footer: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(
        content = { Box { footer() } },
        modifier = modifier.focusProperties { canFocus = false },
    ) { measurables, constraints ->
        // Measured as tall as it wants, so a footer that does not fit is not squeezed to fit.
        val placeable =
            measurables
                .single()
                .measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placeable.height <= constraints.maxHeight) {
                placeable.place(0, constraints.maxHeight - placeable.height)
            }
        }
    }
}

/**
 * The footer of the [NavigationRail]: the Borealis mark, the [name] and the [version] of the app,
 * or the name alone when the version is `null`. A screen reader reads the sentence [description]
 * (for example "Borealis, version 0.1.0") and nothing else of it. A long version wraps and then
 * ends in an ellipsis.
 */
@Composable
fun RailFooter(
    name: String,
    version: String?,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBorealisColors.current
    val label = MaterialTheme.typography.labelSmall
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = FOOTER_PADDING_HORIZONTAL),
        horizontalArrangement = Arrangement.spacedBy(FOOTER_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.borealis_mark),
            contentDescription = null,
            modifier = Modifier.size(MARK_SIZE),
            tint = colors.ink3,
        )
        Column {
            Text(
                name,
                style = label,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (version != null) {
                Text(
                    version,
                    style =
                        label.copy(
                            fontWeight = BorealisTokens.FontWeight.Regular,
                            letterSpacing = 0.sp,
                        ),
                    color = colors.ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
