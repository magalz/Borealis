package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.optionsClickable
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType

/** Covers are 3 by 4: the ratio of the captures, which have room for ten covers at 960dp. */
private const val COVER_ASPECT_RATIO = 3f / 4f
private const val HIDDEN_ALPHA = 0.4f
private const val INITIALS_COUNT = 2
private val COVER_PADDING = 10.dp
private val BADGE_PADDING = 8.dp
private val BADGE_DOT_SIZE = 6.dp
private val BADGE_GAP = 6.dp
private val DOT_BADGE_SIZE = 20.dp
private val DOT_BADGE_DOT_SIZE = 8.dp
private val LABEL_GAP = 8.dp

/**
 * An app of a host in the grid: a placeholder cover (the app initials until Phase 3 brings covers)
 * with the name under it. [runningLabel], when not null, adds the "running" badge; [hidden] dims
 * the tile. The cover is the focusable target: a tap or OK runs [onClick], every way of asking for
 * options runs [onOptions] (named [optionsLabel] for a screen reader), and it grows on focus. The
 * cover is announced by the app name and its state: [runningLabel] and, for a hidden app,
 * [hiddenLabel], so the state is spoken and not only shown. The name under the cover is a
 * [BackdropText], for it stands on the background.
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
        // The name stands on the background, under the cover, and not on glass.
        BackdropText(
            name,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.labelLarge,
            ink = BackdropInk.Ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The stand-in for a cover until the host serves covers (Phase 3): the app initials on a glass
 * surface with the cover radius (the raised surface colour where the glass is solid). [interaction]
 * comes after the clip, so a ripple keeps the radius; [content] draws over the cover (a badge).
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
    val type = LocalBorealisType.current
    val style = MaterialTheme.typography.titleLarge
    val initialsStyle = remember(type, style) { type.codes(style) }
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

/** The test tag of the Running badge, with its label or the dot alone. */
internal const val RUNNING_BADGE_TAG = "running-badge"

/**
 * The Running badge in the bottom start corner of a cover: a dot and the [label], or, when the
 * label does not fit on one line in the width of the cover at the text size in force, the dot
 * alone. The label is never split inside a word and never ends in an ellipsis: a cover of 73dp, on
 * a phone with 4 columns, has no room for "Running" at any text size. The tile says that its app
 * runs also to a screen reader, through its state, so the dot alone loses nothing there.
 */
@Composable
private fun BoxScope.RunningBadge(label: String) {
    BoxWithConstraints(Modifier.align(Alignment.BottomStart)) {
        val measurer = rememberTextMeasurer()
        val style = LocalTextStyle.current.merge(MaterialTheme.typography.labelMedium)
        val labelWidth = measurer.measure(label, style, softWrap = false, maxLines = 1).size.width
        val chrome =
            with(LocalDensity.current) {
                (BADGE_PADDING * 4 + BADGE_DOT_SIZE + BADGE_GAP).roundToPx()
            }
        if (chrome + labelWidth <= constraints.maxWidth) LabelledBadge(label) else DotBadge()
    }
}

@Composable
private fun LabelledBadge(label: String) {
    val colors = LocalBorealisColors.current
    val pill = LocalBorealisShapes.current.pill
    Row(
        Modifier.testTag(RUNNING_BADGE_TAG)
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

@Composable
private fun DotBadge() {
    val colors = LocalBorealisColors.current
    Box(
        Modifier.testTag(RUNNING_BADGE_TAG)
            .padding(BADGE_PADDING)
            .size(DOT_BADGE_SIZE)
            .background(colors.page, CircleShape)
            .border(1.dp, colors.accentLine, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(DOT_BADGE_DOT_SIZE).background(colors.accentSoft, CircleShape))
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
