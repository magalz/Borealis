package io.github.magalz.borealis.designsystem.focus

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlinx.coroutines.launch

/**
 * The Borealis focus indication (ADR 0053): while the element has focus, an accent-soft outline
 * ([BorealisTokens.Focus.Outline] wide, [BorealisTokens.Focus.Offset] outside [shape]) is drawn
 * around it. With [scaleOnFocus] the element also grows to [BorealisTokens.Focus.TileScale],
 * animated unless the system has animations turned off.
 *
 * The outline shows only when focus did not come from touch (keyboard input mode), so a tap on a
 * phone never leaves an outline behind. It is [shape] laid out on the rectangle grown by the offset
 * and half the stroke, so it keeps the corner radii of [shape].
 *
 * The outline reaches about 5dp outside the element (2dp offset plus 3dp stroke), and a scaled tile
 * grows by 2% of its size on each side more. A parent that clips (a lazy grid or list, a scroll
 * area) cuts it unless it has `contentPadding` of at least 10dp, and neighbours closer than 10dp
 * have outlines that overlap: use at least 10dp of spacing between focusable items.
 *
 * Put this modifier before the one that makes the element focusable (`clickable`, `focusable`): it
 * reads the focus of what follows it.
 */
fun Modifier.borealisFocusIndication(shape: Shape, scaleOnFocus: Boolean = false): Modifier =
    this then BorealisFocusElement(shape, scaleOnFocus)

private data class BorealisFocusElement(val shape: Shape, val scaleOnFocus: Boolean) :
    ModifierNodeElement<BorealisFocusNode>() {
    override fun create() = BorealisFocusNode(shape, scaleOnFocus)

    override fun update(node: BorealisFocusNode) {
        node.shape = shape
        node.scaleOnFocus = scaleOnFocus
        node.invalidateDraw()
    }
}

private class BorealisFocusNode(var shape: Shape, var scaleOnFocus: Boolean) :
    Modifier.Node(),
    FocusEventModifierNode,
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private var focused = false
    private val scale = Animatable(1f)

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == focused) return
        focused = focusState.isFocused
        invalidateDraw()
        if (scaleOnFocus) animateScale(if (focused) BorealisTokens.Focus.TileScale else 1f)
    }

    private fun animateScale(target: Float) {
        coroutineScope.launch {
            if (animationsRemoved()) {
                scale.snapTo(target)
            } else {
                scale.animateTo(target, tween(SCALE_MILLIS, easing = FastOutSlowInEasing))
            }
        }
    }

    /** Reduced motion: the system setting "Remove animations" sets the animator scale to zero. */
    private fun animationsRemoved(): Boolean {
        val resolver = currentValueOf(LocalContext).contentResolver
        return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    override fun ContentDrawScope.draw() {
        val showOutline =
            focused && currentValueOf(LocalInputModeManager).inputMode == InputMode.Keyboard
        val factor = scale.value
        withTransform({ scale(factor, factor, pivot = Offset(size.width / 2, size.height / 2)) }) {
            this@draw.drawContent()
            if (showOutline) drawFocusOutline()
        }
    }

    private fun DrawScope.drawFocusOutline() {
        val stroke = BorealisTokens.Focus.Outline.toPx()
        // A stroke is centred on its path, so the path sits half a stroke past the 2dp gap.
        val grow = BorealisTokens.Focus.Offset.toPx() + stroke / 2
        val outline =
            shape.createOutline(
                Size(size.width + 2 * grow, size.height + 2 * grow),
                layoutDirection,
                this,
            )
        translate(-grow, -grow) {
            drawOutline(
                outline,
                currentValueOf(LocalBorealisColors).accentSoft,
                style = Stroke(stroke),
            )
        }
    }

    private companion object {
        const val SCALE_MILLIS = 150
    }
}
