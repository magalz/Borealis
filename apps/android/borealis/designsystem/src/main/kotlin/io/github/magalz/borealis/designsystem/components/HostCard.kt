@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** The one inline action of a host card: Open, Pair or Wake. */
class HostCardAction(val label: String, val onClick: () -> Unit)

private val CONTENT_GAP = 12.dp
private val LINE_GAP = 4.dp
private val DOT_GAP = 6.dp
private val CHIP_PADDING_HORIZONTAL = 8.dp
private val CHIP_PADDING_VERTICAL = 2.dp

/**
 * A host: its [name], a status dot with [statusText], a secondary [detail] line (version and
 * address), the app it is running if any, and at most one inline [action]. A tap or OK runs
 * [onClick]; every way of asking for options (long press, hold OK, X, Menu) runs [onOptions]. On a
 * phone the action sits at the end of the row; on a TV or handheld it sits under the text, where a
 * card in a grid has the room.
 */
@Composable
fun HostCard(
    name: String,
    status: DotStatus,
    statusText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    runningApp: String? = null,
    action: HostCardAction? = null,
    onOptions: (() -> Unit)? = null,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .borealisFocusIndication(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .clip(shape)
            .optionsClickable(onClick, onOptions, Role.Button)
            .padding(LocalBorealisSpacing.current.cardPadding)
    ) {
        if (LocalFormFactor.current == FormFactor.Phone) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HostInfo(name, status, statusText, detail, runningApp, Modifier.weight(1f))
                action?.let { HostActionButton(it) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CONTENT_GAP)) {
                HostInfo(name, status, statusText, detail, runningApp, Modifier)
                action?.let { HostActionButton(it) }
            }
        }
    }
}

@Composable
private fun HostActionButton(action: HostCardAction) {
    BorealisButton(action.label, action.onClick, style = BorealisButtonStyle.Secondary)
}

@Composable
private fun HostInfo(
    name: String,
    status: DotStatus,
    statusText: String,
    detail: String?,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
