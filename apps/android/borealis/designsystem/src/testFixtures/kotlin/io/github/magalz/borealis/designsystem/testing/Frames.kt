package io.github.magalz.borealis.designsystem.testing

import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot

/** The pixels of the node tagged [tag] as drawn now. */
fun ComposeTestRule.frame(tag: String): PixelMap = onNodeWithTag(tag).captureToImage().toPixelMap()

/** The pixels of the whole window as drawn now. */
fun ComposeTestRule.rootFrame(): PixelMap = onRoot().captureToImage().toPixelMap()

/**
 * How many changes the recomposers of the test have taken: a count of recompositions, to compare
 * before and after an action.
 */
@OptIn(ExperimentalComposeApi::class)
fun recompositions(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
