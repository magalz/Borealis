package io.github.magalz.borealis.designsystem.components

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.pixelAt
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The loader is 108dp wide here, so a unit of its 108-unit icon geometry is one dp. Its box starts
 * at unit 10 from the top: a unit (x, y) is at (x, y - 10) dp from the node's top-left corner. The
 * four probes sit inside the green band and inside the violet band, near each end.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AuroraLoaderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the bands fill from the left and the loader starts empty`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        assertEmpty(GREEN_LEFT, GREEN_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
    }

    @Test
    fun `part way through the bands cover the left of the box and not the right`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeBy(HALF_WAY_MILLIS)

        assertDrawn(GREEN_LEFT, VIOLET_LEFT)
        assertEmpty(GREEN_RIGHT, VIOLET_RIGHT)
    }

    @Test
    fun `at the end of the fill both bands are full`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeBy(FULL_MILLIS)

        assertDrawn(GREEN_LEFT, GREEN_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
    }

    @Test
    fun `the fill repeats from empty`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeBy(RESTARTED_MILLIS)

        assertEmpty(GREEN_LEFT, GREEN_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
    }

    @Test
    fun `with animations removed the bands are full and static from the first frame`() {
        removeAnimations()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        assertDrawn(GREEN_LEFT, GREEN_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
        composeRule.mainClock.advanceTimeBy(RESTARTED_MILLIS)
        assertDrawn(GREEN_LEFT, GREEN_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
    }

    private fun removeAnimations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    private fun assertDrawn(vararg probes: Pair<Float, Float>) = probes.forEach {
        assertWithMessage("probe $it is drawn")
            .that(distanceFromPage(it))
            .isGreaterThan(MIN_DISTANCE)
    }

    private fun assertEmpty(vararg probes: Pair<Float, Float>) = probes.forEach {
        assertWithMessage("probe $it is empty").that(distanceFromPage(it)).isLessThan(MAX_EMPTY)
    }

    private fun distanceFromPage(probe: Pair<Float, Float>): Float {
        val pixel = composeRule.onNodeWithTag(FRAME).pixelAt(probe.first, probe.second - TOP)
        val page = BorealisTokens.Color.Page
        return abs(pixel.red - page.red) +
            abs(pixel.green - page.green) +
            abs(pixel.blue - page.blue)
    }

    @Composable
    private fun Specimen() {
        BorealisTheme {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page)) {
                AuroraLoader(size = 108.dp)
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
        const val TOP = 10f
        const val MIN_DISTANCE = 0.1f
        const val MAX_EMPTY = 0.02f

        // Probes in the 108-unit geometry of the icon: inside the band, near each end.
        val GREEN_LEFT = 8f to 66f
        val GREEN_RIGHT = 100f to 26f
        val VIOLET_LEFT = 8f to 76f
        val VIOLET_RIGHT = 100f to 44f

        // The fill takes 1100ms, holds full to 1400ms and starts again.
        const val HALF_WAY_MILLIS = 400L
        const val FULL_MILLIS = 1250L
        const val RESTARTED_MILLIS = 1450L
    }
}
