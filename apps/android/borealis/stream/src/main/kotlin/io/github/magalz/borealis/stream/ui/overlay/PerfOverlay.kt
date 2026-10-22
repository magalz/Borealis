package io.github.magalz.borealis.stream.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.SessionInfo
import kotlin.math.roundToInt

/** Marks the overlay, so a test can measure it. */
internal const val PERF_OVERLAY_TAG = "perf_overlay"

private const val BACKGROUND_ALPHA = 0.74f
private val PADDING_HORIZONTAL = 10.dp
private val PADDING_VERTICAL = 6.dp
private val LINE_GAP = 2.dp

/**
 * The performance readout over a corner of the stream: one line for Compact, a line for each figure
 * for Detailed, nothing for Off. [perf] is null until the first reading arrives. It is plain text
 * with no pointer input and no focus: the stream keeps the touches and the keys. Mono type, at the
 * kicker size of the device: 11sp on a phone, 12sp on a handheld and 13sp (the TV minimum) on a TV.
 * With the letter spacing and the line height of the Text spacing the detailed readout can be
 * taller than the room it has, and it takes no touch and no focus to scroll: it is then scaled down
 * to the room, so that none of it is cut.
 */
@Composable
internal fun PerfOverlay(
    mode: OverlayMode,
    perf: PerfSnapshot?,
    info: SessionInfo,
    modifier: Modifier = Modifier,
) {
    val format = rememberPerfFormat()
    val lines =
        when (mode) {
            OverlayMode.OFF -> return
            OverlayMode.COMPACT -> listOf(format.compact(perf, info))
            OverlayMode.DETAILED -> format.detailed(perf, info)
        }
    val colors = LocalBorealisColors.current
    val type = LocalBorealisType.current
    val codes = type.codes(type.kicker)
    Column(
        modifier
            .shrinkToFitHeight()
            .testTag(PERF_OVERLAY_TAG)
            .background(colors.page.copy(alpha = BACKGROUND_ALPHA), MaterialTheme.shapes.medium)
            .padding(PADDING_HORIZONTAL, PADDING_VERTICAL),
        verticalArrangement = Arrangement.spacedBy(LINE_GAP),
    ) {
        for (line in lines) {
            Text(
                line,
                color = colors.ink,
                style =
                    type.spaced(
                        TextStyle(fontFamily = codes.fontFamily, fontSize = codes.fontSize)
                    ),
            )
        }
    }
}

/**
 * Scales what follows down, from its top left corner, when it is taller than the height on offer,
 * so that it fits; what fits is left as it is, without a layer.
 */
private fun Modifier.shrinkToFitHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
    val scale =
        if (constraints.hasBoundedHeight && placeable.height > constraints.maxHeight) {
            constraints.maxHeight.toFloat() / placeable.height
        } else {
            1f
        }
    layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
        if (scale == 1f) {
            placeable.place(0, 0)
        } else {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
