package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * More than the 0.5dp that the casing of the thicker focus outline needs past the 8dp padding of a
 * list (it reaches 8.5dp outside a row).
 */
private val LAYER_BLEED = 2.dp

/**
 * The content of a panel over the video glass, on an offscreen layer of its own where [onGlass]:
 * the text and its halos are drawn into the layer once and it is kept, so the glass behind it,
 * drawn again at each copy of the video, composites it and does not draw them again. A layer with
 * no offscreen buffer would be replayed.
 *
 * The layer clips what is drawn to its bounds, and the focus outline of a control and its casing
 * reach outside the control, so the bounds of the layer are [LAYER_BLEED] larger than the content
 * on each side. The content keeps its size and its place in the layout: the layer takes the room
 * from the padding around it and gives it back.
 *
 * Not on the solid surface, where nothing behind the content changes, and a buffer would only
 * change how what is on it is anti-aliased. No test can see that the layer is kept: the clip to its
 * bounds is what the tests see (`MenuGlassCasingTest`). It is the same chain of modifiers either
 * way, so a change of the glass does not compose the panel again.
 */
internal fun Modifier.glassContentLayer(onGlass: Boolean): Modifier {
    val bleed = if (onGlass) LAYER_BLEED else 0.dp
    return layout { measurable, constraints ->
            val extra = 2 * bleed.roundToPx()
            val placeable = measurable.measure(constraints.offset(extra, extra))
            layout(
                (placeable.width - extra).coerceAtLeast(0),
                (placeable.height - extra).coerceAtLeast(0),
            ) {
                placeable.place(-bleed.roundToPx(), -bleed.roundToPx())
            }
        }
        .graphicsLayer {
            compositingStrategy =
                if (onGlass) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .padding(bleed)
}
