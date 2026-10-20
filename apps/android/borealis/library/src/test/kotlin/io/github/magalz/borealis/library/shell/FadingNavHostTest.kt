package io.github.magalz.borealis.library.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Library screens have no page of their own, so the background shows through them. A screen
 * that leaves therefore goes at once, and the next one fades in over the background: faded out
 * together they would show two screens one over the other.
 *
 * The window of a test is white. Hosts is a red page and Add host a blue square in the corner, so a
 * pixel says which of the two is drawn and how much.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class FadingNavHostTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    private fun show() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            navController = rememberNavController()
            FadingNavHost(navController) {
                composable<Hosts> { Box(Modifier.fillMaxSize().background(Color.Red)) }
                composable<AddHost> { Box(Modifier.size(SQUARE_DP.dp).background(Color.Blue)) }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    private fun pixel(x: Int, y: Int): Color =
        composeRule.onRoot().captureToImage().toPixelMap()[x, y]

    @Test
    fun `the screen that is left is not drawn after the first frame, and the next fades in`() {
        show()
        composeRule.runOnIdle { navController.navigate(AddHost) }

        composeRule.mainClock.advanceTimeBy(TWO_FRAMES_MILLIS)

        // Not the red page, not even faded: the white window.
        assertThat(pixel(OUTSIDE_PX, OUTSIDE_PX).green).isGreaterThan(OPAQUE_WHITE)
        // The square has begun to appear: blue over white, not yet blue.
        assertThat(pixel(IN_SQUARE_PX, IN_SQUARE_PX).red).isGreaterThan(BEGUN)

        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        assertThat(pixel(IN_SQUARE_PX, IN_SQUARE_PX).red).isLessThan(DONE)
    }

    @Test
    fun `going back leaves the screen at once too, and the one before fades in`() {
        show()
        composeRule.runOnIdle { navController.navigate(AddHost) }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.mainClock.advanceTimeBy(TWO_FRAMES_MILLIS)

        // The blue square is gone: a red page at the start of its fade over white has red at 1,
        // and a square that was still drawn would have taken it down.
        assertThat(pixel(IN_SQUARE_PX, IN_SQUARE_PX).red).isGreaterThan(OPAQUE_WHITE)
        // The red page has begun to appear: green is not yet down.
        assertThat(pixel(OUTSIDE_PX, OUTSIDE_PX).green).isGreaterThan(BEGUN)

        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        assertThat(pixel(OUTSIDE_PX, OUTSIDE_PX).green).isLessThan(DONE)
    }

    private companion object {
        const val SQUARE_DP = 50
        const val IN_SQUARE_PX = 40
        const val OUTSIDE_PX = 300
        const val SETTLE_MILLIS = 400L
        const val TWO_FRAMES_MILLIS = 32L
        const val OPAQUE_WHITE = 0.95f
        const val BEGUN = 0.3f
        const val DONE = 0.05f
    }
}
