package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook

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

/**
 * The glow of [DotStatus.Online] is a soft ring of the success colour round the dot, and it reaches
 * [GLOW_RADIUS] from the centre of the dot: 5dp outside its box, and short of the 6dp that a host
 * card leaves between the dot and its status text. It starts 1dp from the edge of the dot, so that
 * what is next to the dot is the background and the dot keeps 3:1 against it (WCAG 1.4.11) in every
 * palette: a glow that starts at the edge of the dot is over that limit on the brightest glass for
 * the blue-yellow palette. The strength rises to [GLOW_PEAK_ALPHA] of the success colour and falls
 * to nothing; each stop is a fraction of [GLOW_RADIUS] and the share of the peak there.
 */
private val GLOW_RADIUS = 10.dp
private const val GLOW_PEAK_ALPHA = 0.45f
private val GLOW_STOPS = arrayOf(0.6f to 0f, 0.7f to 1f, 0.8f to 0.75f, 0.9f to 0.35f, 1f to 0f)

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
 *
 * An online dot also has a soft glow in the success colour, so that an online host stands out. It
 * is drawn outside the box of the dot, which stays 10dp square and moves nothing, it is still, so
 * Reduce motion has nothing to remove, and High contrast draws none: it draws solid, crisp shapes.
 */
@Composable
fun StatusDot(status: DotStatus, contentDescription: String?, modifier: Modifier = Modifier) {
    val colors = LocalBorealisColors.current
    val glows = !LocalBorealisLook.current.highContrast
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
            DotStatus.Online -> {
                if (glows) drawGlow(colors.success)
                drawCircle(colors.success)
            }
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

private fun DrawScope.drawGlow(color: Color) {
    val radius = GLOW_RADIUS.toPx()
    val stops =
        Array(GLOW_STOPS.size) {
            GLOW_STOPS[it].first to color.copy(alpha = GLOW_STOPS[it].second * GLOW_PEAK_ALPHA)
        }
    drawCircle(Brush.radialGradient(colorStops = stops, center = center, radius = radius), radius)
}

private fun DrawScope.triangle(): Path =
    Path().apply {
        moveTo(size.width * TRIANGLE_APEX, size.height * TRIANGLE_TOP)
        lineTo(size.width * TRIANGLE_RIGHT, size.height * TRIANGLE_BOTTOM)
        lineTo(size.width * TRIANGLE_LEFT, size.height * TRIANGLE_BOTTOM)
        close()
    }
