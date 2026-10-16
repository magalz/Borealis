package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

private val WIDTH = 12.dp
private val HEIGHT = 6.dp
private val STROKE = 1.5.dp
private const val RIGHT_DEGREES = -90f

/** A chevron that points down, or right when [down] is false: the mark of a row that expands. */
@Composable
internal fun Chevron(color: Color, modifier: Modifier = Modifier, down: Boolean = true) {
    Canvas(modifier.size(WIDTH, HEIGHT).rotate(if (down) 0f else RIGHT_DEGREES)) {
        val stroke = STROKE.toPx()
        val middle = Offset(size.width / 2, size.height)
        drawLine(color, Offset.Zero, middle, stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), middle, stroke, StrokeCap.Round)
    }
}
