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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

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
 * The shadow of [DotStatus.Online] is a soft shadow round the dot in the page colour, which is what
 * the glass of a card lies on. It is strongest at the edge of the dot, where it is
 * [SHADOW_PEAK_ALPHA] of the page colour, and fades to nothing at [SHADOW_RADIUS] from the centre
 * of the dot: 5dp outside its box, and short of the 6dp that a host card leaves between the dot and
 * its status text. Each stop is a fraction of the radius and the share of the peak there. A darker
 * neighbour raises the contrast of the dot (WCAG 1.4.11), so the shadow touches the dot.
 *
 * On a TV the glass is darker, only a little above the page colour (luma 22 against 16), so a
 * shadow in the page colour read as a faint ring (the device round of Sprint 11). The shadow of a
 * TV is black, which darkens the glass about as clearly as the phone's does, and reaches
 * [SHADOW_RADIUS_TV] from the centre: 5.75dp outside the box, still short of the 6dp to the text.
 */
private val SHADOW_RADIUS = 10.dp
private val SHADOW_RADIUS_TV = 10.75.dp
private const val SHADOW_PEAK_ALPHA = 0.85f
private val SHADOW_STOPS =
    arrayOf(0.5f to 1f, 0.6f to 0.7f, 0.7f to 0.45f, 0.8f to 0.22f, 0.9f to 0.07f, 1f to 0f)

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
 * An online dot also has a soft dark shadow, in the page colour on a phone and a handheld and black
 * on a TV, so that it stands out from the card it is on. It is drawn outside the box of the dot,
 * which stays 10dp square and moves nothing, it is still, so Reduce motion has nothing to remove,
 * and High contrast draws none: it draws solid, crisp shapes.
 */
@Composable
fun StatusDot(status: DotStatus, contentDescription: String?, modifier: Modifier = Modifier) {
    val colors = LocalBorealisColors.current
    val shadows = !LocalBorealisLook.current.highContrast
    val onTv = LocalFormFactor.current == FormFactor.Tv
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
                if (shadows) {
                    if (onTv) drawShadow(Color.Black, SHADOW_RADIUS_TV)
                    else drawShadow(colors.page, SHADOW_RADIUS)
                }
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

private fun DrawScope.drawShadow(color: Color, shadowRadius: Dp) {
    val radius = shadowRadius.toPx()
    val stops =
        Array(SHADOW_STOPS.size) {
            SHADOW_STOPS[it].first to
                color.copy(alpha = SHADOW_STOPS[it].second * SHADOW_PEAK_ALPHA)
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
