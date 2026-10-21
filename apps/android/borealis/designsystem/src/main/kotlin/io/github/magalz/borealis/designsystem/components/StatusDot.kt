package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/**
 * The reachability of a host as the dot shows it. [Attention] is a host that answers but needs the
 * user, such as an online host that is not paired yet.
 */
enum class DotStatus {
    Online,
    Attention,
    Offline,
    Unknown,
}

private val DOT_SIZE = 10.dp
private val RING_STROKE = 2.dp

/** The triangle of [DotStatus.Attention]: its apex and its base, as fractions of the box. */
private const val TRIANGLE_TOP = 0.05f
private const val TRIANGLE_BOTTOM = 0.95f
private const val TRIANGLE_LEFT = 0.02f
private const val TRIANGLE_RIGHT = 0.98f
private const val TRIANGLE_APEX = 0.5f

/**
 * A small status dot. Each state has a colour and a shape, so that it is not told by colour alone
 * (WCAG 1.4.1): [DotStatus.Online] is a filled circle in success, [DotStatus.Attention] a filled
 * triangle in warning, and [DotStatus.Offline] and [DotStatus.Unknown] a hollow ring in offline
 * grey. Pass a [contentDescription] when the dot is not next to a text that says the same; with
 * null it is decorative.
 */
@Composable
fun StatusDot(status: DotStatus, contentDescription: String?, modifier: Modifier = Modifier) {
    val colors = LocalBorealisColors.current
    Canvas(
        modifier
            .size(DOT_SIZE)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        when (status) {
            DotStatus.Online -> drawCircle(colors.success)
            DotStatus.Attention -> drawPath(triangle(), colors.warning)
            DotStatus.Offline,
            DotStatus.Unknown -> {
                // The stroke is centred on its path, so the path is half a stroke inside the box.
                val stroke = RING_STROKE.toPx()
                drawCircle(
                    colors.offline,
                    radius = (size.minDimension - stroke) / 2,
                    style = Stroke(stroke),
                )
            }
        }
    }
}

private fun DrawScope.triangle(): Path =
    Path().apply {
        moveTo(size.width * TRIANGLE_APEX, size.height * TRIANGLE_TOP)
        lineTo(size.width * TRIANGLE_RIGHT, size.height * TRIANGLE_BOTTOM)
        lineTo(size.width * TRIANGLE_LEFT, size.height * TRIANGLE_BOTTOM)
        close()
    }
