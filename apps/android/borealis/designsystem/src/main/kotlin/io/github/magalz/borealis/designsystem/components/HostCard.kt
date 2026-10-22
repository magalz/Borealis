package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.optionsClickable
import io.github.magalz.borealis.designsystem.glass.GlassTextColors
import io.github.magalz.borealis.designsystem.glass.glassSurface
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

private val LINE_GAP = 4.dp
private val DOT_GAP = 6.dp
private val TAG_GAP = 6.dp
private val CHIP_PADDING_HORIZONTAL = 8.dp
private val CHIP_PADDING_VERTICAL = 2.dp

/**
 * A host: its [name], a status dot with [statusText], a secondary [detail] line (an address or a
 * short note), the capability [tags] below it and the app it is running if any. The card is one
 * focus stop with no button of its own: a tap or OK runs [onClick], the action of the host's state
 * (open it, pair it or wake it), which [onClickLabel] names for a screen reader ("Open"). Every way
 * of asking for options (long press, hold OK, X, Menu) runs [onOptions], named [optionsLabel]. The
 * status line says what the card does, so it carries no label of its own.
 *
 * A screen reader gets the card as one sentence: the name, the status, the detail, the tags and the
 * app that runs, as [runningLabel] says it ("Running Desktop") or, without one, as [runningApp]
 * shows it. The dot says nothing of its own, and the tags are read as text with the card.
 *
 * The card is a glass surface: in a glass host it shows the background through, blurred, and its
 * secondary text has the glass colours. Where the glass is solid, or outside a host, it is the
 * `surface` colour.
 *
 * On a TV a card is at least as high as its height token, which is the height of the fullest card
 * (online, with one row of tags and the app it runs, plus a few dp of margin), so the cards of a
 * grid are the same size whatever they show; the text stays at the top. The equal height assumes
 * one row of tags: a card whose tags wrap, or whose text needs more room (a large font), grows
 * rather than cutting it and is then taller than its neighbours. On a handheld or a phone a card is
 * as high as its content, so a short screen is not left with empty space in each card.
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
    runningLabel: String? = null,
    onClickLabel: String? = null,
    onOptions: (() -> Unit)? = null,
    optionsLabel: String? = null,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.large
    val padding = LocalBorealisSpacing.current.cardPadding
    val said =
        listOfNotNull(
                name,
                statusText,
                detail,
                tags.joinToString(", ").ifEmpty { null },
                runningLabel ?: runningApp,
            )
            .joinToString(", ")
    // Only the TV grid has its cards all the same height; a phone or a handheld, which has little
    // room to spare, sizes each card by its content.
    val height =
        if (LocalFormFactor.current == FormFactor.Tv) BorealisTokens.Size.HostCard.HeightTv
        else 0.dp
    GlassTextColors {
        HostInfo(
            name,
            status,
            statusText,
            detail,
            tags,
            runningApp,
            modifier
                .heightIn(min = height)
                .borealisFocusIndication(shape)
                .glassSurface(shape)
                .border(1.dp, colors.line, shape)
                .clip(shape)
                .optionsClickable(onClick, onOptions, Role.Button, optionsLabel, onClickLabel)
                .semantics { contentDescription = said }
                .padding(padding),
        )
    }
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
        // No limit on the lines: at a large text size a long name wraps and the card grows, where
        // an ellipsis would cut it.
        Text(name, style = MaterialTheme.typography.titleMedium)
        if (detail != null) {
            // No limit on the lines, as for the name: at Wider, with the words spaced out, the
            // detail needs a third line, and the card grows where an ellipsis would cut it.
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
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
