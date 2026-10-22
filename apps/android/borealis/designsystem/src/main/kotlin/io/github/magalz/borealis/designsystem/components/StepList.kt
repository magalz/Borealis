package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.R
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

private val MARKER_SIZE = 22.dp
private val STEP_GAP = 10.dp
private val MARKER_GAP = 12.dp
private val GLYPH_STROKE = 2.dp

// The glyphs are drawn on a unit square: the check is two strokes, the cross is two diagonals.
private const val CHECK_LEFT_X = 0.3f
private const val CHECK_LEFT_Y = 0.52f
private const val CHECK_BOTTOM_X = 0.44f
private const val CHECK_BOTTOM_Y = 0.66f
private const val CHECK_RIGHT_X = 0.72f
private const val CHECK_RIGHT_Y = 0.36f
private const val CROSS_NEAR = 0.35f
private const val CROSS_FAR = 0.65f

private enum class StepState {
    Done,
    Current,
    Later,
    Failed,
}

/**
 * A numbered list of [steps] for something that goes through stages (the launch of a stream, the
 * pairing). The steps before [current] are done and show a check; [current] is in progress and has
 * the accent; the rest are muted. A step at [failedAt] shows the failure in the danger colour
 * instead of progress. A screen reader gets a list whose steps say Done, In progress or Failed, and
 * the numbers of the markers are not read.
 */
@Composable
fun StepList(
    steps: List<String>,
    current: Int,
    modifier: Modifier = Modifier,
    failedAt: Int? = null,
) {
    Column(
        modifier.semantics { collectionInfo = CollectionInfo(steps.size, 1) },
        verticalArrangement = Arrangement.spacedBy(STEP_GAP),
    ) {
        steps.forEachIndexed { index, step ->
            val state =
                when {
                    index == failedAt -> StepState.Failed
                    index < current -> StepState.Done
                    index == current -> StepState.Current
                    else -> StepState.Later
                }
            StepRow(step, number = index + 1, state, index)
        }
    }
}

@Composable
private fun StepRow(text: String, number: Int, state: StepState, index: Int) {
    val colors = LocalBorealisColors.current
    val done = stringResource(R.string.borealis_step_done)
    val inProgress = stringResource(R.string.borealis_step_in_progress)
    val failed = stringResource(R.string.borealis_step_failed)
    Row(
        Modifier.semantics(mergeDescendants = true) {
            collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
            selected = state == StepState.Current
            when (state) {
                StepState.Done -> stateDescription = done
                StepState.Current -> stateDescription = inProgress
                StepState.Failed -> stateDescription = failed
                StepState.Later -> Unit
            }
        },
        horizontalArrangement = Arrangement.spacedBy(MARKER_GAP),
    ) {
        StepMarker(number, state)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                when (state) {
                    StepState.Done -> colors.ink2
                    StepState.Current -> colors.ink
                    StepState.Later -> colors.ink4
                    StepState.Failed -> colors.dangerInk
                },
        )
    }
}

/** The circle with the number of a step, a check once it is done or a cross when it failed. */
@Composable
private fun StepMarker(number: Int, state: StepState) {
    val colors = LocalBorealisColors.current
    val (fill, line, ink) =
        when (state) {
            StepState.Done -> Triple(Color.Transparent, colors.success, colors.successInk)
            StepState.Current -> Triple(colors.accentTint, colors.accentLine, colors.accentSoft)
            StepState.Later -> Triple(Color.Transparent, colors.lineStrong, colors.ink4)
            StepState.Failed -> Triple(Color.Transparent, colors.danger, colors.dangerInk)
        }
    Box(
        Modifier.size(MARKER_SIZE)
            .background(fill, CircleShape)
            .border(1.dp, line, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            StepState.Done -> Glyph(ink, check = true)
            StepState.Failed -> Glyph(ink, check = false)
            StepState.Current,
            StepState.Later ->
                Text(
                    number.toString(),
                    color = ink,
                    textAlign = TextAlign.Center,
                    // A mono digit centres in the circle.
                    style = smallCodesStyle(),
                )
        }
    }
}

@Composable
private fun Glyph(color: Color, check: Boolean) {
    Canvas(Modifier.size(MARKER_SIZE)) {
        val stroke = GLYPH_STROKE.toPx()
        fun at(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        if (check) {
            drawLine(
                color,
                at(CHECK_LEFT_X, CHECK_LEFT_Y),
                at(CHECK_BOTTOM_X, CHECK_BOTTOM_Y),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                color,
                at(CHECK_BOTTOM_X, CHECK_BOTTOM_Y),
                at(CHECK_RIGHT_X, CHECK_RIGHT_Y),
                stroke,
                StrokeCap.Round,
            )
        } else {
            drawLine(
                color,
                at(CROSS_NEAR, CROSS_NEAR),
                at(CROSS_FAR, CROSS_FAR),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                color,
                at(CROSS_FAR, CROSS_NEAR),
                at(CROSS_NEAR, CROSS_FAR),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}
