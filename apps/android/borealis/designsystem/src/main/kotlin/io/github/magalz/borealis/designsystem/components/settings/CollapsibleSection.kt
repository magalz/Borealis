package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.R
import io.github.magalz.borealis.designsystem.components.BorealisIcons
import io.github.magalz.borealis.designsystem.components.Kicker
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.rememberAnimationsRemoved

/** An outline sits 5dp outside its row, so neighbouring rows need 10dp between them. */
private val ROW_GAP = 10.dp
private const val ANIMATION_MILLIS = 250
private const val OPEN_DEGREES = 180f

/** Tags of the pieces of a [CollapsibleSection], for the tests. */
internal object CollapsibleSectionTags {
    const val CHEVRON = "section:chevron"
}

/**
 * A section of the phone list of settings that opens and closes: a header with the [title] and a
 * chevron, and under it the rows of [content], shown while [expanded]. The component is stateless:
 * a tap or OK on the header calls [onExpandedChange] with the opposite state, and the caller keeps
 * what is open, so it can survive a recreation or a trip to another screen.
 *
 * The header is a full-width button of at least 48dp on a phone, announced as a heading with its
 * state, Expanded or Collapsed; the chevron beside the title is only drawn. A collapsed section
 * composes none of its rows, so a screen reader and the D-pad do not reach them. Opening and
 * closing animate the height and turn the chevron; when the system removes animations they snap.
 *
 * The outline of a focused header reaches 5dp outside it, so a list of sections needs 8dp of
 * padding around it and 10dp between sections. The rows are spaced by 10dp, as a list of rows is.
 */
@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val removed = rememberAnimationsRemoved()
    Column(modifier) {
        SectionHeader(title, expanded, removed) { onExpandedChange(!expanded) }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion(removed)),
            exit = shrinkVertically(motion(removed)),
        ) {
            Column(
                Modifier.padding(top = ROW_GAP),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                content = content,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, removed: Boolean, onClick: () -> Unit) {
    val expandedState = stringResource(R.string.borealis_section_expanded)
    val collapsedState = stringResource(R.string.borealis_section_collapsed)
    val turn = animateFloatAsState(if (expanded) OPEN_DEGREES else 0f, motion(removed))
    Row(
        Modifier.settingsRowSurface()
            .clickable(
                interactionSource = null,
                indication = BorealisPressIndication,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                heading()
                stateDescription = if (expanded) expandedState else collapsedState
            }
            .settingsRowSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kicker(title)
        Icon(
            BorealisIcons.ChevronDown,
            contentDescription = null,
            modifier =
                Modifier.testTag(CollapsibleSectionTags.CHEVRON).size(ROW_ICON_SIZE).graphicsLayer {
                    rotationZ = turn.value
                },
            tint = LocalBorealisColors.current.ink3,
        )
    }
}

/** How a section moves: at once when the system removes animations. */
private fun <T> motion(removed: Boolean): FiniteAnimationSpec<T> =
    if (removed) snap() else tween(ANIMATION_MILLIS, easing = FastOutSlowInEasing)
