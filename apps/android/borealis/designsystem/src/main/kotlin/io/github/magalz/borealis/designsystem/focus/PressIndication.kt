package io.github.magalz.borealis.designsystem.focus

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import kotlinx.coroutines.launch

private const val PRESSED_ALPHA = 0.1f

/**
 * What a press looks like in Borealis: a faint ink wash over the element while it is held. It is
 * the only indication the components use. Material's ripple also tints a focused element, which on
 * a TV would fill the element next to the focus outline (ADR 0053); the outline alone marks focus.
 */
internal object BorealisPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressNode(interactionSource)

    // The factory has no state, so every instance is the same indication.
    override fun equals(other: Any?) = other === BorealisPressIndication

    override fun hashCode() = System.identityHashCode(this)
}

private class PressNode(private val interactionSource: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> setPressed(true)
                    is PressInteraction.Release,
                    is PressInteraction.Cancel -> setPressed(false)
                }
            }
        }
    }

    private fun setPressed(value: Boolean) {
        pressed = value
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) {
            drawRect(currentValueOf(LocalBorealisColors).ink, alpha = PRESSED_ALPHA)
        }
    }
}
