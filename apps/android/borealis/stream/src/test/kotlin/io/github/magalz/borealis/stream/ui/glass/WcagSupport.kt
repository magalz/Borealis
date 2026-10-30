package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt

private const val BITS = 255.0
private const val NEAR_BLACK = 0.05
private val WEIGHTS = doubleArrayOf(0.2126, 0.7152, 0.0722)

/** What the tests ask of a text: WCAG 2.x level AA. */
internal const val AA_RATIO = 4.5

/**
 * The WCAG 2.x relative luminance of [color], worked out here and not by Compose, from the colour
 * as 8 bit sRGB values: the tests do not take the figure they check by from the code they check.
 */
internal fun wcagLuminance(color: Color): Double {
    val channels = listOf(color.red, color.green, color.blue).map { (it * BITS).roundToInt() }
    return WEIGHTS.indices.sumOf { WEIGHTS[it] * linear(channels[it] / BITS) }
}

private fun linear(channel: Double): Double =
    if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

/** The WCAG contrast ratio of [text] over a backdrop of luminance [backdrop]. */
internal fun contrastOver(text: Color, backdrop: Double): Double =
    (wcagLuminance(text) + NEAR_BLACK) / (backdrop + NEAR_BLACK)
