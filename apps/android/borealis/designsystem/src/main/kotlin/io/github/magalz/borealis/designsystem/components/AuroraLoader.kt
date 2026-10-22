package io.github.magalz.borealis.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.rememberAnimationsRemoved

/** The two bands, in the 108-unit geometry of `design/icon/generate.py` (`TEAL` and `VIOLET`). */
private const val TEAL_BAND =
    "M0 66C20 44 38 62 54 36C64 20 80 30 108 14L108 30C84 42 68 32 57 50C42 72 22 54 0 80Z"
private const val VIOLET_BAND =
    "M0 76C22 56 42 74 58 52C70 38 84 46 108 36L108 48C86 56 72 50 61 62C46 80 24 64 0 88Z"

/** The width of the icon geometry, and the rows of it the bands use: they sit between 10 and 92. */
private const val GEOMETRY_WIDTH = 108f
private const val GEOMETRY_TOP = 10f
private const val GEOMETRY_HEIGHT = 82f

private const val FILL_MILLIS = 1100
private const val CYCLE_MILLIS = 1400

// The teal of the accent, as the icon has it: its accent and accent-strong (`TEAL_TOP` and
// `TEAL_BOTTOM` of `generate.py`). The loader and the icon are drawn in these colours, whatever
// accent the viewer chooses.
private val TEAL_TOP = Color(0x4B, 0xEC, 0xD0).copy(alpha = 0.95f)
private val TEAL_BOTTOM = Color(0x06, 0xAF, 0x97).copy(alpha = 0.10f)
private val VIOLET_TOP = Color(0xC4, 0xB5, 0xFD).copy(alpha = 0.85f)
private val VIOLET_BOTTOM = Color(0x7C, 0x3A, 0xED).copy(alpha = 0.10f)

/**
 * The Borealis loading indicator: the two aurora bands of the app icon, teal and violet, filling
 * from the left edge to the right and starting again. It is [size] wide and as high as the bands
 * need (82/108 of the width). When the system removes animations the bands are shown full and
 * still.
 */
@Composable
fun AuroraLoader(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val fill: State<Float>? = if (rememberAnimationsRemoved()) null else rememberFill()
    val teal = remember { PathParser().parsePathString(TEAL_BAND).toPath() }
    val violet = remember { PathParser().parsePathString(VIOLET_BAND).toPath() }
    val tealBrush = remember {
        Brush.verticalGradient(listOf(TEAL_TOP, TEAL_BOTTOM), startY = 14f, endY = 80f)
    }
    val violetBrush = remember {
        Brush.verticalGradient(listOf(VIOLET_TOP, VIOLET_BOTTOM), startY = 36f, endY = 88f)
    }
    Canvas(modifier.size(size, size * (GEOMETRY_HEIGHT / GEOMETRY_WIDTH))) {
        // The state is read here, so a frame redraws the bands and does not recompose.
        val filled = GEOMETRY_WIDTH * (fill?.value ?: 1f).coerceIn(0f, 1f)
        scale(this.size.width / GEOMETRY_WIDTH, pivot = Offset.Zero) {
            translate(top = -GEOMETRY_TOP) {
                clipRect(0f, 0f, filled, GEOMETRY_WIDTH) {
                    drawPath(violet, violetBrush)
                    drawPath(teal, tealBrush)
                }
            }
        }
    }
}

@Composable
private fun rememberFill(): State<Float> =
    rememberInfiniteTransition(label = "aurora")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    keyframes {
                        durationMillis = CYCLE_MILLIS
                        0f at 0 using LinearEasing
                        1f at FILL_MILLIS
                    },
                    RepeatMode.Restart,
                ),
            label = "aurora fill",
        )
