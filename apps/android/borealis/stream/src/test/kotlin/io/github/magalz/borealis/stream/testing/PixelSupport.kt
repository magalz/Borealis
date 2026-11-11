package io.github.magalz.borealis.stream.testing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs

/** The band of the picture, from the top, in which [farthestFrom] looks: above a centred popup. */
private const val TOP_BAND = 10

/** How far apart two pixels may be, per channel, for [assertNear] to take them as the same. */
internal const val PIXEL_TOLERANCE = 0.012f

/** How far apart two colours may be, summed over the channels, for [near] to take them as one. */
private const val NEAR = 0.08f

/** How far this colour is from [other], summed over the three channels. */
internal fun Color.distanceFrom(other: Color) =
    abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)

/** Whether this colour is within [NEAR] of [other], summed over the three channels. */
internal fun Color.near(other: Color) = distanceFrom(other) < NEAR

/** Asserts that [pixel] is [expected], each channel within [PIXEL_TOLERANCE]. */
internal fun assertNear(pixel: Color, expected: Color) {
    assertThat(pixel.red).isWithin(PIXEL_TOLERANCE).of(expected.red)
    assertThat(pixel.green).isWithin(PIXEL_TOLERANCE).of(expected.green)
    assertThat(pixel.blue).isWithin(PIXEL_TOLERANCE).of(expected.blue)
}

/**
 * Where the picture is farthest from [page] in its top tenth, which is over the background and
 * above a popup: the place where a scrim over it shows most.
 */
internal fun PixelMap.farthestFrom(page: Color): Pair<Int, Int> =
    (0 until width)
        .flatMap { x -> (0 until height / TOP_BAND).map { y -> x to y } }
        .maxBy { (x, y) -> this[x, y].distanceFrom(page) }
