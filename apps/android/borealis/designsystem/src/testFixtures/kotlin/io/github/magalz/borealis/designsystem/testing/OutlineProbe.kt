package io.github.magalz.borealis.designsystem.testing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import kotlin.math.abs
import kotlin.math.roundToInt

/** A side of an element. */
enum class Edge {
    Left,
    Top,
    Right,
    Bottom,
}

/**
 * The colour on the screen [dp] outside [edge] of [node], level with the middle of that edge. A
 * test that has the focus on [node] reads the focus outline there: it is cut off where a parent
 * clips it, and the colour is then the one of whatever lies behind.
 */
fun ComposeTestRule.colourOutside(node: SemanticsNodeInteraction, edge: Edge, dp: Float): Color {
    val root = onRoot()
    val distance = dp * root.fetchSemanticsNode().layoutInfo.density.density
    val bounds = node.fetchSemanticsNode().boundsInRoot
    val (x, y) =
        when (edge) {
            Edge.Left -> bounds.left - distance to bounds.center.y
            Edge.Top -> bounds.center.x to bounds.top - distance
            Edge.Right -> bounds.right + distance to bounds.center.y
            Edge.Bottom -> bounds.center.x to bounds.bottom + distance
        }
    val image = root.captureToImage().toPixelMap()
    val column = x.roundToInt()
    val row = y.roundToInt()
    // A point off the screen is no colour at all, which no outline is.
    if (column !in 0 until image.width || row !in 0 until image.height) return Color.Unspecified
    return image[column, row]
}

/** Whether two opaque colours are the same, give or take a few levels of 255. */
fun Color.isNear(other: Color): Boolean =
    abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < NEAR

private const val NEAR = 0.04f
