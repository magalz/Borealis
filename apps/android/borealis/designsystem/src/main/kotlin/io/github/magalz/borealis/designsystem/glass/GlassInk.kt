package io.github.magalz.borealis.designsystem.glass

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt

/** WCAG 2.x: the contrast that text must keep, and the near black added to each luminance. */
internal const val AA_RATIO = 4.5
private const val NEAR_BLACK = 0.05

/** The text on glass is aimed at 4.5:1 with 2 percent to spare, for the blur of a real device. */
private const val AA_MARGIN = 1.02

/** The walk from a secondary ink towards ink goes one percent of the way at a time. */
private const val WALK_STEPS = 100

private const val COLOUR_MAX = 255.0
private const val CHANNELS = 3

/** The sRGB transfer function and the weights of WCAG 2.x. */
private const val SRGB_THRESHOLD = 0.04045
private const val SRGB_LINEAR_SLOPE = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_GAMMA = 2.4
private val LUMINANCE_WEIGHTS = doubleArrayOf(0.2126, 0.7152, 0.0722)

/** The luminance of the text that keeps 4.5:1 over [backdrop] with the margin. */
internal fun secondaryTarget(backdrop: Double): Double =
    AA_RATIO * AA_MARGIN * (backdrop + NEAR_BLACK) - NEAR_BLACK

/** The contrast ratio of [text] over a backdrop of luminance [backdrop], as WCAG 2.x has it. */
internal fun contrast(text: Color, backdrop: Double): Double =
    (luminance(text) + NEAR_BLACK) / (backdrop + NEAR_BLACK)

/**
 * The first colour on the way from [from] to [towards], in sRGB, one percent at a time, whose
 * luminance reaches [target]; it is [from] itself if that already does. The step is checked as it
 * is worked out, in doubles, and rounded to 8 bits only then: a colour of Compose is 8 bits, and a
 * step that rounds up to the target would stop the walk a step early.
 */
internal fun walk(from: Color, towards: Color, target: Double): Color {
    val start = from.channels()
    val end = towards.channels()
    for (step in 0..WALK_STEPS) {
        val mixed = DoubleArray(CHANNELS) { start[it] + (end[it] - start[it]) * step / WALK_STEPS }
        if (luminance(mixed) >= target) {
            return if (step == 0) from
            else Color(rounded(mixed[0]), rounded(mixed[1]), rounded(mixed[2]))
        }
    }
    return towards
}

/** The WCAG relative luminance of [color]. */
internal fun luminance(color: Color): Double = luminance(color.channels())

/** The red, green and blue of [color], from 0 to 255. */
private fun Color.channels() = doubleArrayOf(to8Bits(red), to8Bits(green), to8Bits(blue))

private fun to8Bits(channel: Float) = (channel * COLOUR_MAX).roundToInt().toDouble()

private fun rounded(channel: Double) = channel.roundToInt()

/** The WCAG relative luminance of red, green and blue from 0 to 255. */
private fun luminance(channels: DoubleArray): Double =
    LUMINANCE_WEIGHTS.indices.sumOf { LUMINANCE_WEIGHTS[it] * linear(channels[it] / COLOUR_MAX) }

private fun linear(channel: Double): Double =
    if (channel <= SRGB_THRESHOLD) channel / SRGB_LINEAR_SLOPE
    else ((channel + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
