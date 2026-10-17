@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.optionsClickable
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/** The one inline action of a host card: Open, Pair or Wake. */
class HostCardAction(val label: String, val onClick: () -> Unit)

private val CONTENT_GAP = 12.dp
private val LINE_GAP = 4.dp
private val DOT_GAP = 6.dp
private val TAG_GAP = 6.dp
private val CHIP_PADDING_HORIZONTAL = 8.dp
private val CHIP_PADDING_VERTICAL = 2.dp

/**
 * A host: its [name], a status dot with [statusText], a secondary [detail] line (an address or a
 * short note), the capability [tags] below it, the app it is running if any, and at most one inline
 * [action]. A tap or OK runs [onClick]; every way of asking for options (long press, hold OK, X,
 * Menu) runs [onOptions], named [optionsLabel] for a screen reader. The card and the action are two
 * focus stops: the D-pad goes from the card to the action (down on a TV, towards the end on a
 * phone) and back. On a phone the action sits at the end of the row; on a TV or handheld it sits
 * under the text, where a card in a grid has the room.
 *
 * On a TV or handheld a card is at least as high as its height token, which is the height of the
 * fullest card (online, with its tags, the app it runs and Open), so the cards of a grid are the
 * same size whatever they show. A card with less keeps its text at the top and its action at the
 * bottom edge. A card whose text needs more room (a large font) grows rather than cutting it. On a
 * phone a card is as high as its content.
 */
@Composable
fun HostCard(
    name: String,
    status: DotStatus,
    statusText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tags: List<String> = emptyList(),
    runningApp: String? = null,
    action: HostCardAction? = null,
    onOptions: (() -> Unit)? = null,
    optionsLabel: String? = null,
    // Not used yet: the test commit adds the parameter so the label tests compile and fail.
    onClickLabel: String? = null,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.large
    val inline = LocalFormFactor.current == FormFactor.Phone
    val padding = LocalBorealisSpacing.current.cardPadding
    // A phone has a single column of cards, so each is as high as its content; a grid on a TV or
    // handheld has them all the same height.
    val height = if (inline) 0.dp else BorealisTokens.Size.HostCard.HeightTv
    val cardFocus = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    Box(
        modifier
            .heightIn(min = height)
            .borealisFocusIndication(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .clip(shape)
            .focusRequester(cardFocus)
            .then(
                // The action is inside the card, so the focus search would never leave the card
                // for it: point the D-pad at it. It is beside the text on a phone, under it else.
                if (action == null) {
                    Modifier
                } else {
                    Modifier.focusProperties {
                        if (inline) end = actionFocus else down = actionFocus
                    }
                }
            )
            .optionsClickable(onClick, onOptions, Role.Button, optionsLabel)
            .padding(padding)
    ) {
        val actionModifier =
            Modifier.focusRequester(actionFocus).focusProperties {
                // Properties are inherited from the card, so the way back is set here, and the way
                // on is reset to the default.
                if (inline) {
                    start = cardFocus
                    end = FocusRequester.Default
                } else {
                    up = cardFocus
                    down = FocusRequester.Default
                }
            }
        HostCardContent(
            inline = inline,
            modifier = Modifier.heightIn(min = (height - padding * 2).coerceAtLeast(0.dp)),
            action =
                action?.let { { modifier -> HostActionButton(it, actionModifier.then(modifier)) } },
        ) { infoModifier ->
            HostInfo(name, status, statusText, detail, tags, runningApp, infoModifier)
        }
    }
}

/**
 * The text and the [action] of a card. On a phone both are centred in the row, with the action at
 * its end; else the text is at the top and the action sits at the bottom edge, so it is in the same
 * place on every card. [info] and [action] get the modifier that places them.
 */
@Composable
private fun HostCardContent(
    inline: Boolean,
    modifier: Modifier,
    action: (@Composable (Modifier) -> Unit)?,
    info: @Composable (Modifier) -> Unit,
) {
    if (inline) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            info(Modifier.weight(1f))
            action?.invoke(Modifier)
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
            info(if (action == null) Modifier else Modifier.padding(bottom = CONTENT_GAP))
            action?.invoke(Modifier)
        }
    }
}

@Composable
private fun HostActionButton(action: HostCardAction, modifier: Modifier) {
    BorealisButton(
        action.label,
        action.onClick,
        modifier,
        style = BorealisButtonStyle.Secondary,
    )
}

@Composable
private fun HostInfo(
    name: String,
    status: DotStatus,
    statusText: String,
    detail: String?,
    tags: List<String>,
    runningApp: String?,
    modifier: Modifier,
) {
    val colors = LocalBorealisColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(LINE_GAP)) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TAG_GAP),
                verticalArrangement = Arrangement.spacedBy(TAG_GAP),
            ) {
                tags.forEach { Tag(it) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(status, contentDescription = null)
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        }
        if (runningApp != null) {
            val pill = LocalBorealisShapes.current.pill
            Text(
                runningApp,
                style = MaterialTheme.typography.bodySmall,
                color = colors.accentSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.background(colors.accentTint, pill)
                        .border(1.dp, colors.accentLine, pill)
                        .padding(
                            horizontal = CHIP_PADDING_HORIZONTAL,
                            vertical = CHIP_PADDING_VERTICAL,
                        ),
            )
        }
    }
}
