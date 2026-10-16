package io.github.magalz.borealis.stream.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.SessionInfo

/** Marks the overlay, so a test can measure it. */
internal const val PERF_OVERLAY_TAG = "perf_overlay"

private const val BACKGROUND_ALPHA = 0.74f
private val PADDING_HORIZONTAL = 10.dp
private val PADDING_VERTICAL = 6.dp
private val LINE_GAP = 2.dp

/**
 * The performance readout over a corner of the stream: one line for Compact, a line for each figure
 * for Detailed, nothing for Off. [perf] is null until the first reading arrives. It is plain text
 * with no pointer input and no focus: the stream keeps the touches and the keys. Mono type, 11sp on
 * a phone and 12sp (the TV minimum) on a TV or handheld.
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
    val size =
        if (LocalFormFactor.current == FormFactor.Phone) {
            BorealisTokens.Size.Phone.Kicker
        } else {
            BorealisTokens.Size.Tv.Minimum
        }
    Column(
        modifier
            .testTag(PERF_OVERLAY_TAG)
            .background(colors.page.copy(alpha = BACKGROUND_ALPHA), MaterialTheme.shapes.medium)
            .padding(PADDING_HORIZONTAL, PADDING_VERTICAL),
        verticalArrangement = Arrangement.spacedBy(LINE_GAP),
    ) {
        for (line in lines) {
            Text(
                line,
                color = colors.ink,
                style = TextStyle(fontFamily = BorealisFonts.Mono, fontSize = size),
            )
        }
    }
}
