package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import kotlin.math.abs

/** The band of the picture, from the top, in which [farthestFrom] looks: above a centred popup. */
private const val TOP_BAND = 10

/** How far this colour is from [other], summed over the three channels. */
internal fun Color.distanceFrom(other: Color) =
    abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)

/**
 * Where the picture is farthest from [page] in its top tenth, which is over the background and
 * above a popup: the place where a scrim over it shows most.
 */
internal fun PixelMap.farthestFrom(page: Color): Pair<Int, Int> =
    (0 until width)
        .flatMap { x -> (0 until height / TOP_BAND).map { y -> x to y } }
        .maxBy { (x, y) -> this[x, y].distanceFrom(page) }
