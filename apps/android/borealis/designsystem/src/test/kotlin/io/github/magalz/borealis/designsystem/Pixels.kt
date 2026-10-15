package io.github.magalz.borealis.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs

/** The tests run at xhdpi, where one dp is two pixels. */
const val PIXELS_PER_DP = 2

private const val TOLERANCE = 0.02f

/** The pixel at ([x], [y]) dp from the node's top-left corner. */
fun SemanticsNodeInteraction.pixelAt(x: Float, y: Float): Color =
    captureToImage().toPixelMap()[(x * PIXELS_PER_DP).toInt(), (y * PIXELS_PER_DP).toInt()]

/** Asserts that the pixel at ([x], [y]) dp from the node's top-left corner has this colour. */
fun SemanticsNodeInteraction.assertPixel(x: Float, y: Float, expected: Color) {
    val actual = pixelAt(x, y)
    val distance =
        abs(actual.red - expected.red) +
            abs(actual.green - expected.green) +
            abs(actual.blue - expected.blue)
    assertWithMessage("pixel at ($x, $y) dp is $actual, expected $expected")
        .that(distance < TOLERANCE)
        .isTrue()
}
