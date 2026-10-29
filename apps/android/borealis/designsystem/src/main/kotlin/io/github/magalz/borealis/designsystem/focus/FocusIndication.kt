package io.github.magalz.borealis.designsystem.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BackdropHalo
import io.github.magalz.borealis.designsystem.components.LocalTextHalo
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalSystemAnimationsRemoved
import io.github.magalz.borealis.designsystem.theme.animationsRemoved
import io.github.magalz.borealis.designsystem.theme.systemAnimationsRemoved
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlinx.coroutines.launch

/**
 * The Borealis focus indication (ADR 0053): while the element has focus, an accent-soft outline
 * ([BorealisTokens.Focus.Outline] wide, [BorealisTokens.Focus.Offset] outside [shape]) is drawn
 * around it. With the Thicker focus setting, and with high contrast, it is
 * [BorealisTokens.Focus.OutlineThick] wide and in the ink colour, which keeps 3:1 on the page, the
 * surface and the brightest backdrop of a glass panel (ADR 0057). With [scaleOnFocus] the element
 * also grows to [BorealisTokens.Focus.TileScale], animated unless animations are removed (Reduce
 * motion, or the system when that follows it).
 *
 * Under [io.github.magalz.borealis.designsystem.components.LocalTextHalo], which a screen sets over
 * a surface that shows a picture it cannot measure (the glass over the video of a stream), the
 * outline has a casing: a stroke 1.5dp wider on each side, in the `page` colour at the opacity of
 * the halo. The outline keeps 3:1 (WCAG 1.4.11) against that, where against the lightest glass the
 * accent-soft one is 1.39:1 and the thick one in ink 2.1:1. Without the hook there is no casing.
 *
 * The outline shows only when focus did not come from touch (keyboard input mode), so a tap on a
 * phone never leaves an outline behind. It is [shape] laid out on the rectangle grown by the offset
 * and half the stroke, so it keeps the corner radii of [shape].
 *
 * The outline reaches about 5dp outside the element (2dp offset plus 3dp stroke), 7dp when it is
 * the thicker one, and a scaled tile grows by 2% of its size on each side more. A parent that clips
 * (a lazy grid or list, a scroll area) cuts it unless it has `contentPadding` of at least 8dp,
 * which holds the 7dp of the thicker outline (12dp for a scaled tile), and neighbours closer than
 * 10dp have outlines that overlap: use at least 10dp of spacing between focusable items.
 * [io.github.magalz.borealis.designsystem.theme.BorealisDimens] has the padding and the gap of a
 * list: `listPadding` is the 8dp.
 *
 * Put this modifier before the one that makes the element focusable (`clickable`, `focusable`): it
 * reads the focus of what follows it.
 */
fun Modifier.borealisFocusIndication(shape: Shape, scaleOnFocus: Boolean = false): Modifier =
    this then BorealisFocusElement(shape, scaleOnFocus)

/**
 * The colour of the focus outline of [colors]: accent-soft, or the ink of the thicker outline. The
 * ink is lighter than accent-soft and keeps 3:1 (WCAG 1.4.11) on the page, on the surface and on
 * the brightest backdrop that a glass panel is allowed, where accent-soft keeps 4.5:1.
 */
internal fun focusOutlineColor(colors: BorealisColors, thick: Boolean): Color =
    if (thick) colors.ink else colors.accentSoft

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
        // A node that has gone has nothing to draw or to animate.
        if (!isAttached || focusState.isFocused == focused) return
        focused = focusState.isFocused
        invalidateDraw()
        if (scaleOnFocus) animateScale(if (focused) BorealisTokens.Focus.TileScale else 1f)
    }

    private fun animateScale(target: Float) {
        // Read now, while attached: the coroutine can first run when the node has gone, and a node
        // that is not attached has no composition locals to read.
        val removed = motionRemoved()
        coroutineScope.launch {
            if (removed) {
                scale.snapTo(target)
            } else {
                scale.animateTo(target, tween(SCALE_MILLIS, easing = FastOutSlowInEasing))
            }
        }
    }

    /**
     * Reduce motion, now: the setting of the app, or the system when that follows it. The system is
     * what the theme watches, or, outside a theme, what the setting says at this moment.
     */
    private fun motionRemoved(): Boolean =
        animationsRemoved(
            currentValueOf(LocalBorealisLook).reduceMotion,
            currentValueOf(LocalSystemAnimationsRemoved)
                ?: systemAnimationsRemoved(currentValueOf(LocalContext).contentResolver),
        )

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
        val look = currentValueOf(LocalBorealisLook)
        val thick = look.thickFocus || look.highContrast
        val stroke =
            (if (thick) BorealisTokens.Focus.OutlineThick else BorealisTokens.Focus.Outline).toPx()
        // A stroke is centred on its path, so the path sits half a stroke past the 2dp gap.
        val grow = BorealisTokens.Focus.Offset.toPx() + stroke / 2
        val outline =
            shape.createOutline(
                Size(size.width + 2 * grow, size.height + 2 * grow),
                layoutDirection,
                this,
            )
        val colors = currentValueOf(LocalBorealisColors)
        val halo = currentValueOf(LocalTextHalo)
        translate(-grow, -grow) {
            if (halo != null) {
                drawOutline(
                    outline,
                    BackdropHalo.color(colors.page, halo),
                    style = Stroke(stroke + 2 * CASING.toPx()),
                )
            }
            drawOutline(outline, focusOutlineColor(colors, thick), style = Stroke(stroke))
        }
    }

    private companion object {
        const val SCALE_MILLIS = 150
        val CASING = 1.5.dp
    }
}
