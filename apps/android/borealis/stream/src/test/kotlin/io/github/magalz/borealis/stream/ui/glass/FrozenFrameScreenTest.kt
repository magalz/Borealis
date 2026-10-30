package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.IntOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.assertHalo
import io.github.magalz.borealis.designsystem.testing.assertNoHalo
import io.github.magalz.borealis.designsystem.testing.rootFrame
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
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
 * soft edge, and the frame is light, so the glass and the scrim show in the pixels. Over the frame
 * the scrim is the page at 35 percent and the glass of the panel is the surface at 85 percent, the
 * firm popup of the stream (owner, 2026-10-30); those are read over a frame of one grey.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class FrozenFrameScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val actions = RecordingActions()
    private var look by mutableStateOf(BorealisLook())
    private val source = FakeVideoSource(completing = true, paint = ::paintFrame)
    private val frozen = FrozenFrame(source, { IntOffset.Zero })

    /** The frame the chrome is given: a test takes it away, to see what the popup draws without. */
    private var inUse: FrozenFrame? by mutableStateOf(frozen)
    private val surface = BorealisColors().surface
    private val page = BorealisColors().page

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

    private fun show(
        session: StreamSession = failedSession(),
        copied: Boolean = true,
        keyboard: Boolean = false,
    ) {
        if (copied) frozen.request()
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalFrozenFrame provides inUse) {
                StreamChromeRoot(session, {}, look)
            }
        }
        if (keyboard) composeRule.setContentInKeyboardMode(content)
        else composeRule.setContent(content)
        composeRule.waitForIdle()
    }

    /** The copy is asked for and does not end until the test says so. */
    private fun showWhilePending(keyboard: Boolean = false) {
        source.completing = false
        show(keyboard = keyboard)
        assertThat(frozen.pending).isTrue()
    }

    /** The node of the announcement of the popup: another popup is another node. */
    private fun announcement() =
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
            .fetchSemanticsNode()
            .id

    private fun endTheCopy(ok: Boolean = true) {
        composeRule.runOnIdle { source.finish(ok) }
        composeRule.waitForIdle()
    }

    private fun picture(): PixelMap = composeRule.rootFrame()

    /** A pixel of the margin beside the popup, over the light grey of the frame. */
    private fun margin(): Color = picture()[MARGIN_X, 2]

    /**
     * The frame is one grey from edge to edge, so the pixels over it are the colour to the digit.
     */
    private fun greyFrame() {
        source.paint = { it.eraseColor(LIGHT_GREY) }
    }

    /** The scrim over [behind]: the page of the look at 35 percent, not the 72 of the dialogs. */
    private fun scrimOver(behind: Color) =
        BorealisColors().page.copy(alpha = SCRIM).compositeOver(behind)

    private fun assertNear(pixel: Color, expected: Color) {
        assertThat(pixel.red).isWithin(TOLERANCE).of(expected.red)
        assertThat(pixel.green).isWithin(TOLERANCE).of(expected.green)
        assertThat(pixel.blue).isWithin(TOLERANCE).of(expected.blue)
    }

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

    // The scrim

    @Test
    fun `the scrim over the blurred frame is the page at 35 percent`() {
        greyFrame()
        show()

        assertNear(margin(), scrimOver(Color(LIGHT_GREY)))
    }

    @Test
    fun `the scrim is the same over a frame that shows sharp`() {
        greyFrame()
        look = BorealisLook(transparency = Transparency.Off)
        show()

        assertNear(margin(), scrimOver(Color(LIGHT_GREY)))
    }

    @Test
    fun `the scrim is the same around a solid panel`() {
        greyFrame()
        show(failedSession(MenuGlass.SOLID))

        assertNear(margin(), scrimOver(Color(LIGHT_GREY)))
    }

    @Test
    fun `the scrim is over the black of the frame while the copy is pending`() {
        showWhilePending()

        assertNear(margin(), scrimOver(Color.Black))
    }

    // Not over the game: no frame, so the popup is over the background

    /**
     * The popup of a failure that has no frame draws what it draws with no frozen frame at all,
     * over the background of the screens outside the game (ADR 0057, decision 8: no scrim on API 31
     * and later, the glass of the background). Reduce motion holds the background still, as the
     * Library does, so two pictures of it are the same, and the test looks at the place where it is
     * farthest from the page, so that a scrim over it would show. The popup is the same session
     * each time; only the frame changes: none given, then one that was never asked for, or whose
     * copy failed.
     */
    private fun assertDrawsAsWithNoFrame(copyFails: Boolean) {
        inUse = null
        look = BorealisLook(reduceMotion = ReduceMotion.On)
        show(stillFailure(), copied = false)
        val without = picture()
        val (x, y) = without.farthestFrom(page)
        assertThat(without[x, y].distanceFrom(page)).isGreaterThan(MIN_GLOW)

        composeRule.runOnIdle {
            inUse = frozen
            if (copyFails) {
                source.bounds = null
                frozen.request()
            }
        }
        composeRule.waitForIdle()

        assertThat(frozen.frame).isNull()
        assertThat(frozen.pending).isFalse()
        assertNear(picture()[x, y], without[x, y])
    }

    private fun stillFailure() = aSession(actions).also { it.onFailed(aFailure) }

    @Test
    fun `with no copy asked for, the popup draws what it draws with no frozen frame`() =
        assertDrawsAsWithNoFrame(copyFails = false)

    @Test
    fun `with a copy that failed, the popup draws what it draws with no frozen frame`() =
        assertDrawsAsWithNoFrame(copyFails = true)

    // The copy that has not ended

    @Test
    fun `no background shows while the copy is pending, only the black of the frame under the scrim`() {
        showWhilePending()

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(ERROR_PANEL_TAG).assertIsDisplayed()
        assertThat(margin().luminance()).isLessThan(FRAME_SHOWS)
    }

    @Test
    fun `the popup is composed once when the frame arrives, so its focus and announcement stay`() {
        showWhilePending(keyboard = true)
        composeRule.onNodeWithText("Close").requestFocus()
        val announced = announcement()

        endTheCopy()

        assertThat(margin().luminance()).isGreaterThan(FRAME_SHOWS)
        composeRule.onNodeWithText("Close").assertIsFocused()
        assertThat(announcement()).isEqualTo(announced)
    }

    @Test
    fun `the background appears when the pending copy fails, and the popup is not composed again`() {
        showWhilePending(keyboard = true)
        composeRule.onNodeWithText("Close").requestFocus()
        val announced = announcement()

        endTheCopy(ok = false)

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsFocused()
        assertThat(announcement()).isEqualTo(announced)
    }

    @Test
    fun `the popup of a failure with no copy asked for is composed once as well`() {
        show(copied = false, keyboard = true)
        composeRule.onNodeWithText("Close").requestFocus()
        val announced = announcement()

        composeRule.runOnIdle { frozen.request() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Close").assertIsFocused()
        assertThat(announcement()).isEqualTo(announced)
    }

    @Test
    fun `a screen that is not a failure does not use the frame`() {
        show(aSession(actions))

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertIsDisplayed()
        assertThat(margin().luminance()).isLessThan(FRAME_SHOWS)
    }

    @Test
    fun `the label of the filled button has no halo, which would smudge it, the others have it`() {
        show()

        composeRule.assertNoHalo("Try again")
        composeRule.assertHalo("Close")
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
    fun `the panel is the surface at 85 percent over the blurred frame`() {
        greyFrame()
        show()

        assertThat(isSolid(panel())).isFalse()
        assertNear(panel(), surface.copy(alpha = POPUP_TINT).compositeOver(Color(LIGHT_GREY)))
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
        const val MIN_GLOW = 0.05f
        const val SCRIM = 0.35f
        const val POPUP_TINT = 0.85f
        const val TOLERANCE = 0.012f
        const val SOLID = 0.01f
    }
}
