package io.github.magalz.borealis.stream.ui.background

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.assertHalo
import io.github.magalz.borealis.designsystem.testing.assertNoHalo
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.testing.isNear
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.ui.STARTING_PANEL_TAG
import io.github.magalz.borealis.stream.ui.StreamChromeRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The accessibility settings still win outside the game (ADR 0061, decision 3): Reduce motion and
 * the battery saver keep the background still, and a clip shows its still; Transparency Off and
 * High contrast give the panel of the steps a solid surface. The text that stands on the background
 * has the halo of the Library (decision 4), unless the background is the plain page.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class OutsideGameAccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    private var look by mutableStateOf(BorealisLook())

    private fun show(session: StreamSession, shown: BorealisLook = BorealisLook()) {
        look = shown
        composeRule.setContent { StreamChromeRoot(session, {}, look) }
        composeRule.waitForIdle()
    }

    private fun picture() = composeRule.frame(OUTSIDE_GAME_BACKGROUND_TAG)

    /** Whether the picture of the background changes over a few seconds of animation time. */
    private fun moves(): Boolean {
        composeRule.mainClock.advanceTimeBy(1_000)
        val first = picture()
        composeRule.mainClock.advanceTimeBy(5_000)
        return !first.buffer.contentEquals(picture().buffer)
    }

    private fun assertStillOnly() {
        composeRule.onNodeWithTag(VIDEO).assertDoesNotExist()
        composeRule.onNodeWithTag(STILL).assertIsDisplayed()
    }

    @Test
    fun `Reduce motion keeps a drawn background still`() {
        composeRule.mainClock.autoAdvance = false
        show(sessionAt(), BorealisLook(reduceMotion = ReduceMotion.On))

        assertWithMessage("the picture moves").that(moves()).isFalse()
    }

    @Test
    fun `Reduce motion shows the still of a clip and makes no player`() {
        val clip = StreamVisuals(backgroundStyle = BackgroundStyle.Starfield)

        show(sessionAt(visuals = clip), BorealisLook(reduceMotion = ReduceMotion.On))

        assertStillOnly()
    }

    @Test
    fun `the battery saver keeps a drawn background still, and is followed live`() {
        composeRule.mainClock.autoAdvance = false
        show(sessionAt())
        assertWithMessage("the picture moves before the saver").that(moves()).isTrue()

        composeRule.runOnUiThread { setPowerSave(true) }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertWithMessage("the picture moves with the saver").that(moves()).isFalse()

        composeRule.runOnUiThread { setPowerSave(false) }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertWithMessage("the picture moves after the saver").that(moves()).isTrue()
    }

    @Test
    fun `the battery saver releases a clip and shows its still, going off plays it again`() {
        show(sessionAt(visuals = StreamVisuals(backgroundStyle = BackgroundStyle.FlowingLight)))
        composeRule.onNodeWithTag(VIDEO).assertExists()

        composeRule.runOnUiThread { setPowerSave(true) }
        composeRule.waitForIdle()
        assertStillOnly()

        composeRule.runOnUiThread { setPowerSave(false) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VIDEO).assertExists()
    }

    /** The colour at the top edge of the steps panel, in the middle, away from its corners. */
    private fun panelEdge(): Color = composeRule.frame(STARTING_PANEL_TAG)[PANEL_MIDDLE, EDGE]

    private fun showSteps(shown: BorealisLook) = show(sessionAt(), shown)

    private fun assertSolidPanel(shown: BorealisLook) {
        showSteps(shown)

        val edge = panelEdge()
        assertWithMessage("the panel edge is $edge")
            .that(edge.isNear(borealisColors(shown).surface))
            .isTrue()
    }

    @Test
    fun `Transparency Off gives the steps a solid surface`() =
        assertSolidPanel(BorealisLook(transparency = Transparency.Off))

    @Test
    fun `High contrast gives the steps a solid surface`() =
        assertSolidPanel(BorealisLook(highContrast = true))

    @Test
    fun `with the default glass the steps panel is a tint over the background, not the solid one`() {
        showSteps(BorealisLook())
        val glass = panelEdge()

        composeRule.runOnIdle { look = BorealisLook(transparency = Transparency.Off) }
        composeRule.waitForIdle()

        assertWithMessage("the panel edge with glass is $glass, as solid as with Transparency Off")
            .that(glass)
            .isNotEqualTo(panelEdge())
    }

    @Test
    fun `the text of Quitting has the halo`() {
        show(sessionAt(OutsideScreen.QUITTING))

        composeRule.assertHalo("Quitting Desktop…")
    }

    @Test
    fun `the text of a failure is on the panel of the popup, so it has no halo`() {
        show(sessionAt(OutsideScreen.FAILED))

        composeRule.assertNoHalo("Couldn't start Desktop")
        composeRule.assertNoHalo("The stream could not start.")
    }

    @Test
    fun `the heading of the starting screen has the halo`() {
        show(sessionAt())

        composeRule.assertHalo("Starting Desktop", substring = true)
    }

    @Test
    fun `the halo is not drawn when the style is the plain page`() {
        show(
            sessionAt(OutsideScreen.QUITTING, StreamVisuals(backgroundStyle = BackgroundStyle.Off))
        )

        composeRule.assertNoHalo("Quitting Desktop…", drawn = true)
    }

    private companion object {
        const val PANEL_MIDDLE = 100
        const val EDGE = 1
    }
}
