package io.github.magalz.borealis.designsystem.theme

import androidx.compose.ui.graphics.Color
import io.github.magalz.borealis.designsystem.glass.contrast
import io.github.magalz.borealis.designsystem.glass.luminance
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The measures behind the accessibility colours, for the tests: the WCAG contrast ratio, a colour
 * as someone with a dichromacy sees it, and the colour difference CIEDE2000.
 */

/**
 * The WCAG 2.x contrast ratio of two opaque colours, the same whichever is the lighter, worked out
 * by the contrast function of the glass rules of the app, which is for the lighter text.
 */
internal fun contrastRatio(a: Color, b: Color): Double =
    max(contrast(a, luminance(b)), contrast(b, luminance(a)))

/**
 * The kinds of dichromacy of the Machado, Oliveira and Fernandes (2009) model at severity 1.0, the
 * one that `simulate_cvd` in `design/accessibility/contrast.py` has. A matrix acts on linear RGB;
 * each row adds up to 1, so white and the greys are seen as they are.
 */
internal enum class Dichromacy(val matrix: List<List<Double>>) {
    Protanopia(
        listOf(
            listOf(0.152286, 1.052583, -0.204868),
            listOf(0.114503, 0.786281, 0.099216),
            listOf(-0.003882, -0.048116, 1.051998),
        )
    ),
    Deuteranopia(
        listOf(
            listOf(0.367322, 0.860646, -0.227968),
            listOf(0.280085, 0.672501, 0.047413),
            listOf(-0.011820, 0.042940, 0.968881),
        )
    ),
    Tritanopia(
        listOf(
            listOf(1.255528, -0.076749, -0.178779),
            listOf(-0.078411, 0.930809, 0.147602),
            listOf(0.004733, 0.691367, 0.303900),
        )
    ),
}

/** This colour as someone with [kind] sees it, to 8 bits a channel as an app draws it. */
internal fun Color.seenBy(kind: Dichromacy): Color {
    val linear = listOf(red, green, blue).map { toLinear(it.toDouble()) }
    val seen =
        kind.matrix.map { row -> row.indices.sumOf { row[it] * linear[it] }.coerceIn(0.0, 1.0) }
    val (r, g, b) = seen.map { (toSrgb(it) * CHANNEL_MAX).roundToInt() }
    return Color(r, g, b)
}

private const val CHANNEL_MAX = 255.0
private const val SRGB_THRESHOLD = 0.04045
private const val SRGB_LINEAR_THRESHOLD = 0.0031308

private fun toLinear(channel: Double) =
    if (channel <= SRGB_THRESHOLD) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

private fun toSrgb(linear: Double) =
    if (linear <= SRGB_LINEAR_THRESHOLD) linear * 12.92 else 1.055 * linear.pow(1 / 2.4) - 0.055

/** The colour difference CIEDE2000 of two opaque colours; 2 or so is the least that is seen. */
internal fun deltaE2000(a: Color, b: Color): Double {
    val (l1, a1, b1) = a.toLab()
    val (l2, a2, b2) = b.toLab()
    val meanChroma = (hypot(a1, b1) + hypot(a2, b2)) / 2
    val g = 0.5 * (1 - sqrt(meanChroma.pow(7) / (meanChroma.pow(7) + 25.0.pow(7))))
    val a1p = (1 + g) * a1
    val a2p = (1 + g) * a2
    val c1 = hypot(a1p, b1)
    val c2 = hypot(a2p, b2)
    val h1 = hue(b1, a1p)
    val h2 = hue(b2, a2p)
    val dL = l2 - l1
    val dC = c2 - c1
    val dh = hueDifference(c1, c2, h1, h2)
    val dH = 2 * sqrt(c1 * c2) * sin(Math.toRadians(dh / 2))
    val meanL = (l1 + l2) / 2
    val meanC = (c1 + c2) / 2
    val meanH = meanHue(c1, c2, h1, h2)
    val t =
        1 - 0.17 * cos(Math.toRadians(meanH - 30)) +
            0.24 * cos(Math.toRadians(2 * meanH)) +
            0.32 * cos(Math.toRadians(3 * meanH + 6)) - 0.20 * cos(Math.toRadians(4 * meanH - 63))
    val rotation =
        -sin(Math.toRadians(2 * 30 * exp(-((meanH - 275) / 25).pow(2)))) *
            2 *
            sqrt(meanC.pow(7) / (meanC.pow(7) + 25.0.pow(7)))
    val sl = 1 + 0.015 * (meanL - 50).pow(2) / sqrt(20 + (meanL - 50).pow(2))
    val sc = 1 + 0.045 * meanC
    val sh = 1 + 0.015 * meanC * t
    return sqrt(
        (dL / sl).pow(2) + (dC / sc).pow(2) + (dH / sh).pow(2) + rotation * (dC / sc) * (dH / sh)
    )
}

private fun hue(b: Double, a: Double): Double =
    if (a == 0.0 && b == 0.0) 0.0 else Math.toDegrees(atan2(b, a)).let { (it + 360) % 360 }

private fun hueDifference(c1: Double, c2: Double, h1: Double, h2: Double): Double {
    if (c1 * c2 == 0.0) return 0.0
    val d = h2 - h1
    return when {
        abs(d) <= 180 -> d
        d > 180 -> d - 360
        else -> d + 360
    }
}

private fun meanHue(c1: Double, c2: Double, h1: Double, h2: Double): Double =
    when {
        c1 * c2 == 0.0 -> h1 + h2
        abs(h1 - h2) <= 180 -> (h1 + h2) / 2
        h1 + h2 < 360 -> (h1 + h2 + 360) / 2
        else -> (h1 + h2 - 360) / 2
    }

/** CIELAB under D65: the lightness, then a and b. */
private fun Color.toLab(): Triple<Double, Double, Double> {
    val r = toLinear(red.toDouble())
    val g = toLinear(green.toDouble())
    val b = toLinear(blue.toDouble())
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z
    val fx = lab(x)
    val fy = lab(y)
    val fz = lab(z)
    return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

private const val WHITE_X = 0.95047
private const val WHITE_Z = 1.08883
private const val LAB_EPSILON = 216.0 / 24389.0
private const val LAB_KAPPA = 24389.0 / 27.0

private fun lab(t: Double) = if (t > LAB_EPSILON) cbrt(t) else (LAB_KAPPA * t + 16) / 116
