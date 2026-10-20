package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.optionsClickable
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes

/** Covers are 3 by 4: the ratio of the captures, which have room for ten covers at 960dp. */
private const val COVER_ASPECT_RATIO = 3f / 4f
private const val HIDDEN_ALPHA = 0.4f
private const val INITIALS_COUNT = 2
private val COVER_PADDING = 10.dp
private val BADGE_PADDING = 8.dp
private val BADGE_DOT_SIZE = 6.dp
private val BADGE_GAP = 6.dp
private val LABEL_GAP = 8.dp

/**
 * An app of a host in the grid: a placeholder cover (the app initials until Phase 3 brings covers)
 * with the name under it. [runningLabel], when not null, adds the "running" badge; [hidden] dims
 * the tile. The cover is the focusable target: a tap or OK runs [onClick], every way of asking for
 * options runs [onOptions] (named [optionsLabel] for a screen reader), and it grows on focus. The
 * cover is announced by the app name and its state: [runningLabel] and, for a hidden app,
 * [hiddenLabel], so the state is spoken and not only shown.
 */
@Composable
fun CoverTile(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    runningLabel: String? = null,
    hidden: Boolean = false,
    hiddenLabel: String? = null,
    onOptions: (() -> Unit)? = null,
    optionsLabel: String? = null,
) {
    val colors = LocalBorealisColors.current
    val shape = LocalBorealisShapes.current.cover
    val state =
        listOfNotNull(runningLabel, hiddenLabel.takeIf { hidden }).joinToString(", ").ifEmpty {
            null
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
        CoverPlaceholder(
            name,
            Modifier.fillMaxWidth()
                .aspectRatio(COVER_ASPECT_RATIO)
                .borealisFocusIndication(shape, scaleOnFocus = true)
                .alpha(if (hidden) HIDDEN_ALPHA else 1f),
            interaction =
                Modifier.optionsClickable(onClick, onOptions, Role.Button, optionsLabel).semantics {
                    contentDescription = name
                    if (state != null) stateDescription = state
                },
        ) {
            if (runningLabel != null) RunningBadge(runningLabel)
        }
        Text(
            name,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.labelLarge,
            color = colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The stand-in for a cover until the host serves covers (Phase 3): the app initials on a raised
 * surface with the cover radius. [interaction] comes after the clip, so a ripple keeps the radius;
 * [content] draws over the cover (a badge).
 */
@Composable
internal fun CoverPlaceholder(
    name: String,
    modifier: Modifier = Modifier,
    interaction: Modifier = Modifier,
    initialsAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = LocalBorealisColors.current
    val shape = LocalBorealisShapes.current.cover
    val style = MaterialTheme.typography.titleLarge
    val initialsStyle = remember(style) { style.copy(fontFamily = BorealisFonts.Mono) }
    Box(
        modifier
            .glassOrSolid(shape, colors.surfaceRaised)
            .border(1.dp, colors.line, shape)
            .clip(shape)
            .then(interaction)
    ) {
        Text(
            initialsOf(name),
            modifier =
                Modifier.align(initialsAlignment).padding(COVER_PADDING).clearAndSetSemantics {},
            style = initialsStyle,
            color = colors.ink,
        )
        content()
    }
}

@Composable
private fun BoxScope.RunningBadge(label: String) {
    val colors = LocalBorealisColors.current
    val pill = LocalBorealisShapes.current.pill
    Row(
        Modifier.align(Alignment.BottomStart)
            .padding(BADGE_PADDING)
            .background(colors.page, pill)
            .border(1.dp, colors.accentLine, pill)
            .padding(horizontal = BADGE_PADDING, vertical = BADGE_GAP / 2),
        horizontalArrangement = Arrangement.spacedBy(BADGE_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(BADGE_DOT_SIZE).background(colors.accentSoft, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.ink)
    }
}

/** The first letter of each of the first two words, in capitals: "Steam Big Picture" is "SB". */
internal fun initialsOf(name: String): String =
    name
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .take(INITIALS_COUNT)
        .joinToString("") { it.take(1).uppercase() }

private val WHITESPACE = Regex("\\s+")
