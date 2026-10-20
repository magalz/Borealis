package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

private const val PERCENT = 100f

/**
 * The background of a glass surface of [shape]. Over a [GlassHost] with glass, it shows the
 * background behind it, blurred, under a tint of the `surface` colour; where the glass is solid, or
 * outside any host, it is the `surface` colour, as a panel was before the glass; where the device
 * cannot blur, it is the tint over the background as it is.
 *
 * Text on it keeps its contrast with [GlassTextColors].
 */
@Composable
fun Modifier.glassSurface(shape: Shape): Modifier {
    val scene = LocalGlassScene.current
    val surface = LocalBorealisColors.current.surface
    val spec = scene.spec
    val backdrop = scene.backdrop
    return when {
        spec.solid -> background(surface, shape)
        backdrop == null -> background(surface.copy(alpha = spec.tintPercent / PERCENT), shape)
        else ->
            clip(shape)
                .then(GlassBackdropElement(scene, surface.copy(alpha = spec.tintPercent / PERCENT)))
    }
}

/**
 * Gives [content], which is on a glass surface, the text colours that keep 4.5:1 over the
 * background behind it: the ink-3 and ink-4 roles are replaced, through [LocalBorealisColors], by
 * the colours that [GlassRules] works out for the background, the tint and the blur. Where the
 * glass is solid, or outside any host, [content] sees the tokens as they are.
 */
@Composable
fun GlassTextColors(content: @Composable () -> Unit) {
    val scene = LocalGlassScene.current
    val colors = LocalBorealisColors.current
    val onGlass = remember(scene, colors) { colors.onGlass(scene.style, scene.spec) }
    CompositionLocalProvider(LocalBorealisColors provides onGlass, content = content)
}

private data class GlassBackdropElement(val scene: GlassScene, val tint: Color) :
    ModifierNodeElement<GlassBackdropNode>() {
    override fun create() = GlassBackdropNode(scene, tint)

    override fun update(node: GlassBackdropNode) {
        node.scene = scene
        node.tint = tint
        node.invalidateDraw()
    }
}

/**
 * A glass surface over a blurred backdrop. It draws the region of the host's blurred copy that is
 * behind it, scaled back up, then its tint, then its content. Where it is on the host is its
 * position in the root less the host's, which it hears of when it moves.
 */
private class GlassBackdropNode(var scene: GlassScene, var tint: Color) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var position = Offset.Unspecified

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val now = coordinates.positionInRoot()
        if (now != position) {
            position = now
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val backdrop = scene.backdrop
        if (backdrop != null) {
            val at = if (position.isSpecified) position - scene.origin else Offset.Zero
            translate(-at.x, -at.y) {
                scale(BACKDROP_DOWNSCALE.toFloat(), Offset.Zero) { drawLayer(backdrop) }
            }
        }
        drawRect(tint)
        drawContent()
    }
}
