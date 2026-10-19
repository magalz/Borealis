package io.github.magalz.borealis.designsystem.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

private const val MARKER_TAG = "screen-backdrop-marker"
private val MARKER = Color(0xFF, 0x00, 0xFF)

/** One dp at xhdpi: inside the gutter of every screen, where no content is drawn. */
private const val MARGIN_PX = 2

/**
 * The page colour behind a screen, which is what a window of the app is (`Theme.Borealis`): the
 * screens leave their background to the shell, and a window of a test is white. [content] is
 * [Modifier.fillMaxSize]d.
 */
@Composable
fun OnPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(BorealisTokens.Color.Page)) { content() }
}

/**
 * [content] over a bright marker: a screen that paints a background of its own hides the marker,
 * and [assertMarkerShows] fails.
 */
@Composable
fun OverMarker(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().testTag(MARKER_TAG).background(MARKER)) { content() }
}

/** Asserts that the left margin of the screen shown with [OverMarker] still shows the marker. */
fun ComposeTestRule.assertMarkerShows() {
    val shot = onNodeWithTag(MARKER_TAG).captureToImage().toPixelMap()
    for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
        val y = (shot.height * fraction).toInt()
        val pixel = shot[MARGIN_PX, y]
        if (pixel != MARKER) {
            throw AssertionError("the screen paints over the marker at ($MARGIN_PX, $y): $pixel")
        }
    }
}
