package io.github.magalz.borealis.designsystem.background

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The background of the Library on a phone at xhdpi (720 x 1280 pixels): what each style draws,
 * that the animated ones loop and move without recomposing, that a system without animations gets
 * one still frame, and what the video styles do with their player.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BorealisBackgroundTest {
    @get:Rule val composeRule = createComposeRule()

    private val players = FakeBackgroundPlayers()
    private val lifecycle = TestLifecycle()

    // Off and the two Compose styles

    @Test
    fun `Off paints the plain page colour everywhere`() {
        show(BackgroundStyle.Off)

        val shot = frame()
        assertThat(shot.maxLift(0f, 1f)).isLessThan(NONE)
        assertThat(shot.minLift(0f, 1f)).isGreaterThan(-NONE)
    }

    @Test
    fun `the curtains light the top of the screen and leave the bottom quarter to the page`() {
        removeAnimations()
        show(BackgroundStyle.AuroraCurtains)

        val shot = frame()
        assertThat(shot.maxLift(0f, 0.5f)).isIn(LIT_RANGE)
        assertThat(shot.maxLift(0.75f, 1f)).isLessThan(NONE)
        assertThat(shot.minLift(0f, 1f)).isGreaterThan(-NONE)
    }

    @Test
    fun `the glow lights the top of the screen and, fainter, the bottom`() {
        removeAnimations()
        show(BackgroundStyle.SoftGlow)

        val shot = frame()
        assertThat(shot.maxLift(0f, 0.5f)).isIn(LIT_RANGE)
        assertThat(shot.maxLift(0.75f, 1f)).isIn(FAINT_RANGE)
        assertThat(shot.minLift(0f, 1f)).isGreaterThan(-NONE)
    }

    @Test
    fun `every style fills the screen with a fixed frame and none crashes`() {
        removeAnimations()
        var style by mutableStateOf(BackgroundStyle.Off)
        composeRule.setContent {
            Specimen { BorealisBackground(style, Modifier.fillMaxSize().testTag(BG)) }
        }

        BackgroundStyle.entries.forEach { next ->
            composeRule.runOnIdle { style = next }
            composeRule.waitForIdle()

            val shot = frame()
            assertWithMessage("$next fills the screen").that(shot.width).isEqualTo(WIDTH)
            assertWithMessage("$next fills the screen").that(shot.height).isEqualTo(HEIGHT)
            when {
                next.clip != null ->
                    composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
                next != BackgroundStyle.Off ->
                    assertWithMessage("$next draws").that(shot.maxLift(0f, 1f)).isIn(LIT_RANGE)
            }
        }
    }

    // Motion

    @Test fun `the curtains move with the frames`() = assertMoves(BackgroundStyle.AuroraCurtains)

    @Test fun `the glow moves with the frames`() = assertMoves(BackgroundStyle.SoftGlow)

    @Test
    fun `the curtains close their 20 second loop without a seam`() =
        assertClosesLoop(BackgroundStyle.AuroraCurtains)

    @Test
    fun `the glow closes its 20 second loop without a seam`() =
        assertClosesLoop(BackgroundStyle.SoftGlow)

    @Test
    fun `with animations removed the curtains are one still frame that never changes`() =
        assertStill(BackgroundStyle.AuroraCurtains)

    @Test
    fun `with animations removed the glow is one still frame that never changes`() =
        assertStill(BackgroundStyle.SoftGlow)

    @Test
    fun `the frames redraw the background and recompose nothing`() {
        composeRule.mainClock.autoAdvance = false
        var counter by mutableIntStateOf(0)
        composeRule.setContent {
            Specimen {
                // A control: a state read in composition does recompose, and the count sees it.
                Text("$counter")
                BorealisBackground(
                    BackgroundStyle.AuroraCurtains,
                    Modifier.fillMaxSize().testTag(BG),
                )
            }
        }
        settle()
        val settled = recompositions()
        repeat(2) {
            composeRule.runOnIdle { counter++ }
            settle()
        }
        assertThat(recompositions()).isGreaterThan(settled)

        val before = recompositions()
        val shotBefore = frame()
        composeRule.mainClock.advanceTimeBy(5_000)
        settle()

        assertThat(frame().differingPixels(shotBefore)).isGreaterThan(0)
        assertThat(recompositions()).isEqualTo(before)
    }

    // The video styles

    @Test
    fun `a style that is not a video makes no player`() {
        var style by mutableStateOf(BackgroundStyle.Off)
        composeRule.setContent { Specimen { BorealisBackground(style, Modifier.fillMaxSize()) } }

        BackgroundStyle.entries
            .filter { it.clip == null }
            .forEach { next ->
                composeRule.runOnIdle { style = next }
                composeRule.waitForIdle()

                assertWithMessage("a player for $next").that(players.created).isEmpty()
            }
    }

    @Test
    fun `each video style plays its own clip on the surface as the only player`() {
        var style by mutableStateOf(BackgroundStyle.NorthernLights)
        composeRule.setContent { Specimen { BorealisBackground(style, Modifier.fillMaxSize()) } }
        composeRule.waitForIdle()

        BackgroundStyle.entries
            .filter { it.clip != null }
            .forEach { next ->
                composeRule.runOnIdle { style = next }
                composeRule.waitForIdle()

                val player = players.live.single()
                assertThat(player.video).isEqualTo(next.clip?.video)
                assertThat(player.playing).isTrue()
                composeRule.onNodeWithTag(BackgroundTags.VIDEO).assertIsDisplayed()
            }
    }

    @Test
    fun `the surface is no part of what a screen reader reads`() {
        show(BackgroundStyle.LightMist)

        assertThat(players.live.single().surface.importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
    }

    @Test
    fun `changing the style releases the player, and Off holds no decoder`() {
        var style by mutableStateOf(BackgroundStyle.NorthernLights)
        composeRule.setContent { Specimen { BorealisBackground(style, Modifier.fillMaxSize()) } }
        composeRule.waitForIdle()
        val first = players.live.single()

        composeRule.runOnIdle { style = BackgroundStyle.Starfield }
        composeRule.waitForIdle()
        assertThat(first.released).isTrue()
        assertThat(players.live).hasSize(1)
        assertThat(players.created).hasSize(2)

        composeRule.runOnIdle { style = BackgroundStyle.AuroraCurtains }
        composeRule.waitForIdle()
        assertThat(players.live).isEmpty()
        assertThat(players.created).hasSize(2)
    }

    @Test
    fun `leaving the composition releases the player`() {
        var shown by mutableStateOf(true)
        composeRule.setContent {
            Specimen {
                if (shown) BorealisBackground(BackgroundStyle.LightMist, Modifier.fillMaxSize())
            }
        }
        composeRule.waitForIdle()
        assertThat(players.live).hasSize(1)

        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()

        assertThat(players.live).isEmpty()
    }

    @Test
    fun `the player pauses with the app, is released when the activity stops and comes back`() {
        show(BackgroundStyle.FlowingLight)
        val first = players.live.single()
        assertThat(first.playing).isTrue()

        lifecycle.moveTo(Lifecycle.State.STARTED)
        assertThat(first.playing).isFalse()
        assertThat(first.released).isFalse()

        lifecycle.moveTo(Lifecycle.State.RESUMED)
        assertThat(first.playing).isTrue()

        lifecycle.moveTo(Lifecycle.State.CREATED)
        assertThat(first.released).isTrue()
        assertThat(players.live).isEmpty()

        lifecycle.moveTo(Lifecycle.State.STARTED)
        assertThat(players.created).hasSize(2)
        assertThat(players.live).hasSize(1)

        lifecycle.moveTo(Lifecycle.State.RESUMED)
        assertThat(players.live.single().playing).isTrue()
    }

    @Test
    fun `with animations removed a video style shows its still and makes no player`() {
        removeAnimations()
        show(BackgroundStyle.NorthernLights)

        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
        composeRule.onNodeWithTag(BackgroundTags.VIDEO).assertDoesNotExist()
        assertThat(players.created).isEmpty()
    }

    @Test
    fun `the still covers the video until its first frame is on the surface`() {
        show(BackgroundStyle.Starfield)
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()

        composeRule.runOnIdle { players.live.single().showFirstFrame() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BackgroundTags.STILL).assertDoesNotExist()
        composeRule.onNodeWithTag(BackgroundTags.VIDEO).assertIsDisplayed()
    }

    // Helpers

    private fun show(style: BackgroundStyle) {
        composeRule.setContent {
            Specimen { BorealisBackground(style, Modifier.fillMaxSize().testTag(BG)) }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Specimen(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalBackgroundPlayerFactory provides players,
            LocalLifecycleOwner provides lifecycle,
            content = content,
        )
    }

    /** Takes a frame at 1 s and another, a quarter of a loop later. */
    private fun assertMoves(style: BackgroundStyle) {
        composeRule.mainClock.autoAdvance = false
        show(style)
        composeRule.mainClock.advanceTimeBy(1_000)
        val start = frame()
        composeRule.mainClock.advanceTimeBy(5_000)

        assertThat(start.maxLift(0f, 0.5f)).isIn(LIT_RANGE)
        assertThat(frame().differingPixels(start)).isGreaterThan(0)
    }

    /**
     * The frame at the end of a loop is the frame at its start: every motion has a whole number of
     * cycles in it. The frames of a test clock do not fall on the loop, so the position is set.
     */
    private fun assertClosesLoop(style: BackgroundStyle) {
        val cycle = mutableFloatStateOf(0f)
        composeRule.setContent {
            Specimen { DrawnBackground(style, cycle, Modifier.fillMaxSize().testTag(BG)) }
        }
        composeRule.waitForIdle()
        val start = frame()
        composeRule.runOnIdle { cycle.floatValue = 0.5f }
        composeRule.waitForIdle()
        val middle = frame()
        composeRule.runOnIdle { cycle.floatValue = 0.99999f }
        composeRule.waitForIdle()
        val end = frame()

        assertThat(start.maxLift(0f, 0.5f)).isIn(LIT_RANGE)
        assertThat(middle.differingPixels(start)).isGreaterThan(0)
        // The end is a hair of the cycle before the start: a pixel may move by a level or two.
        assertThat(end.differingPixels(start, tolerance = 2)).isEqualTo(0)
    }

    private fun assertStill(style: BackgroundStyle) {
        removeAnimations()
        composeRule.mainClock.autoAdvance = false
        show(style)
        val first = frame()
        assertThat(first.maxLift(0f, 0.5f)).isIn(LIT_RANGE)

        composeRule.mainClock.advanceTimeBy(5_000)

        assertThat(frame().differingPixels(first)).isEqualTo(0)
    }

    /**
     * Lets the recomposer take the changes. Its count runs one recomposition behind, so a control
     * needs two.
     */
    private fun settle() {
        repeat(2) { composeRule.mainClock.advanceTimeBy(100) }
    }

    private fun frame(): PixelMap = composeRule.onNodeWithTag(BG).captureToImage().toPixelMap()

    /** How many changes the recomposers of the test have taken: a count of recompositions. */
    @OptIn(ExperimentalComposeApi::class)
    private fun recompositions(): Long =
        Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    private companion object {
        const val BG = "background"
        const val WIDTH = 720
        const val HEIGHT = 1280
        const val LOOP_MILLIS = 20_000L
        const val SAMPLE_STEP = 8

        // The sum of red, green and blue above the page colour: negative below it.
        const val NONE = 0.01f

        // Faint is a glow far from its centre; lit is a band or a glow near it. A white frame,
        // which
        // is what shows where nothing is drawn, is far above both.
        val FAINT_RANGE = Range.open(0.04f, 0.3f)
        val LIT_RANGE = Range.open(0.3f, 1.2f)

        val PAGE_SUM = BorealisTokens.Color.Page.let { it.red + it.green + it.blue }

        fun Color.lift() = red + green + blue - PAGE_SUM

        /** The most light above the page between [from] and [to], as fractions of the height. */
        fun PixelMap.maxLift(from: Float, to: Float) = samples(from, to).max()

        fun PixelMap.minLift(from: Float, to: Float) = samples(from, to).min()

        /**
         * How many pixels have a channel that differs by more than [tolerance] of 255, which fails
         * a test without printing a million of them.
         */
        fun PixelMap.differingPixels(other: PixelMap, tolerance: Int = 0) =
            buffer.indices.count { i ->
                (0..BLUE_SHIFT_END step CHANNEL_BITS).any { shift ->
                    abs(((buffer[i] shr shift) and 0xFF) - ((other.buffer[i] shr shift) and 0xFF)) >
                        tolerance
                }
            }

        const val CHANNEL_BITS = 8
        const val BLUE_SHIFT_END = 16

        fun PixelMap.samples(from: Float, to: Float): List<Float> =
            ((height * from).toInt() until (height * to).toInt() step SAMPLE_STEP).flatMap { y ->
                (0 until width step SAMPLE_STEP).map { x -> this[x, y].lift() }
            }
    }
}
