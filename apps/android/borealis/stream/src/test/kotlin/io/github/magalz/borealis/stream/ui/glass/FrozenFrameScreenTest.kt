package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.rootFrame
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.stream.MenuGlass
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aDefaults
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.ui.ERROR_PANEL_TAG
import io.github.magalz.borealis.stream.ui.StreamChromeRoot
import io.github.magalz.borealis.stream.ui.background.OUTSIDE_GAME_BACKGROUND_TAG
import kotlin.math.abs
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The error popup over the last frame of the stream, frozen (the owner's decision of 2026-10-29):
 * the frame is behind the popup whenever a copy exists, blurred where the device can blur and the
 * settings allow it, and the panel is the glass of the menu or the solid surface by the rules of
 * `frozenFrameLook`. With no copy the popup is on the background of the screens outside the game.
 * The fake video is a strip of black down the left edge of a light grey frame, so a blur shows as a
 * soft edge, and the frame is light, so the glass and the scrim show in the pixels.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class FrozenFrameScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val actions = RecordingActions()
    private var look by mutableStateOf(BorealisLook())
    private val source = FakeVideoSource(completing = true, paint = ::paintFrame)
    private val frozen = FrozenFrame(source, { IntOffset.Zero })
    private val surface = BorealisColors().surface

    @After fun `the battery saver is off again`() = setPowerSave(false)

    private fun paintFrame(bitmap: Bitmap) {
        bitmap.eraseColor(LIGHT_GREY)
        for (x in 0 until bitmap.width * STRIP_PERCENT / PERCENT) {
            for (y in 0 until bitmap.height) bitmap.setPixel(x, y, BLACK)
        }
    }

    private fun failedSession(menuGlass: MenuGlass = MenuGlass.BLUR) =
        aSession(actions, aDefaults.copy(visuals = StreamVisuals(menuGlass = menuGlass))).also {
            it.onFailed(aFailure)
        }

    private fun show(session: StreamSession = failedSession(), copied: Boolean = true) {
        if (copied) frozen.request()
        composeRule.setContent {
            CompositionLocalProvider(LocalFrozenFrame provides frozen) {
                StreamChromeRoot(session, {}, look)
            }
        }
        composeRule.waitForIdle()
    }

    private fun picture(): PixelMap = composeRule.rootFrame()

    /** A pixel of the margin beside the popup, over the light grey of the frame. */
    private fun margin(): Color = picture()[MARGIN_X, 2]

    /** The largest step in luminance between neighbouring pixels across the strip at the edge. */
    private fun edgeStep(): Float {
        val map = picture()
        val y = map.height / 2
        return (0 until EDGE_REACH).maxOf { x ->
            abs(map[x + 1, y].luminance() - map[x, y].luminance())
        }
    }

    private fun panel(): Color = composeRule.onNodeWithTag(ERROR_PANEL_TAG).leftPixel()

    private fun isSolid(pixel: Color) =
        abs(pixel.red - surface.red) + abs(pixel.green - surface.green) < SOLID

    // The frame or the background

    @Test
    fun `the popup stands on the last frame, dimmed by its scrim, and not on the background`() {
        show()

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(ERROR_PANEL_TAG).assertIsDisplayed()
        assertThat(margin().luminance()).isGreaterThan(FRAME_SHOWS)
        assertThat(margin().luminance()).isLessThan(LIGHT_GREY_LUMINANCE)
    }

    @Test
    fun `with no copy the popup stands on the background of the screens outside the game`() {
        show(copied = false)

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ERROR_PANEL_TAG).assertIsDisplayed()
        assertThat(margin().luminance()).isLessThan(FRAME_SHOWS)
    }

    @Test
    fun `a copy that failed leaves the popup on the background`() {
        val failing = FakeVideoSource(completing = true)
        val broken = FrozenFrame(failing, { IntOffset.Zero })
        failing.bounds = null
        broken.request()
        composeRule.setContent {
            CompositionLocalProvider(LocalFrozenFrame provides broken) {
                StreamChromeRoot(failedSession(), {}, look)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertIsDisplayed()
    }

    @Test
    fun `a frame that arrives after the failure puts the popup on it`() {
        show(copied = false)
        val before = margin().luminance()

        composeRule.runOnIdle { frozen.request() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertDoesNotExist()
        assertThat(margin().luminance()).isGreaterThan(before + FRAME_SHOWS / 2)
    }

    @Test
    fun `a screen that is not a failure does not use the frame`() {
        show(aSession(actions))

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertIsDisplayed()
        assertThat(margin().luminance()).isLessThan(FRAME_SHOWS)
    }

    @Test
    fun `the buttons of the popup work over the frame`() {
        show()

        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions.calls).containsExactly("retry")
    }

    // The blur

    @Test
    fun `the frame is blurred behind the popup, and sharp where Transparency is Off`() {
        show()
        val blurred = edgeStep()

        look = BorealisLook(transparency = Transparency.Off)
        composeRule.waitForIdle()
        val sharp = edgeStep()

        assertThat(sharp).isGreaterThan(SHARP_EDGE)
        assertThat(blurred).isLessThan(sharp / 2)
    }

    @Test
    fun `high contrast shows the frame sharp`() {
        show()

        look = BorealisLook(highContrast = true)
        composeRule.waitForIdle()

        assertThat(edgeStep()).isGreaterThan(SHARP_EDGE)
    }

    @Test
    fun `the frame is blurred around a solid panel too`() {
        show(failedSession(MenuGlass.SOLID))
        val blurred = edgeStep()

        look = BorealisLook(highContrast = true)
        composeRule.waitForIdle()

        assertThat(blurred).isLessThan(edgeStep() / 2)
    }

    // The panel

    @Test
    fun `the panel is the glass of the menu over the blurred frame`() {
        show()

        assertThat(isSolid(panel())).isFalse()
        assertThat(panel().luminance()).isGreaterThan(GLASS_OVER_LIGHT)
    }

    @Test
    fun `the panel is the solid surface where the menu glass option is Solid`() {
        show(failedSession(MenuGlass.SOLID))

        assertThat(isSolid(panel())).isTrue()
    }

    @Test
    fun `Transparency Off makes the panel the solid surface`() {
        look = BorealisLook(transparency = Transparency.Off)
        show()

        assertThat(isSolid(panel())).isTrue()
    }

    @Test
    fun `high contrast makes the panel the solid surface`() {
        look = BorealisLook(highContrast = true)
        show()

        assertThat(isSolid(panel())).isTrue()
    }

    @Test
    fun `Reduce motion leaves the glass, because the frame is still`() {
        look = BorealisLook(reduceMotion = ReduceMotion.On)
        show()

        assertThat(isSolid(panel())).isFalse()
    }

    @Test
    fun `the battery saver leaves the glass, because the frame was copied once`() {
        setPowerSave(true)
        show()

        assertThat(isSolid(panel())).isFalse()
        assertThat(powerSaveReceivers()).isEmpty()
    }

    private companion object {
        const val LIGHT_GREY = 0xFFC0C0C0.toInt()
        const val BLACK = 0xFF000000.toInt()
        val LIGHT_GREY_LUMINANCE = Color(LIGHT_GREY).luminance()
        const val STRIP_PERCENT = 3
        const val PERCENT = 100
        const val MARGIN_X = 60
        const val EDGE_REACH = 70
        const val FRAME_SHOWS = 0.035f
        const val SHARP_EDGE = 0.01f
        const val GLASS_OVER_LIGHT = 0.12f
        const val SOLID = 0.01f
    }
}
