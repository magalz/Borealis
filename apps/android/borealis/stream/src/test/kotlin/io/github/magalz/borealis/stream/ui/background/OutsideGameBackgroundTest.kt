package io.github.magalz.borealis.stream.ui.background

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.testing.isNear
import io.github.magalz.borealis.designsystem.theme.BackgroundIntensity
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.stream.BackgroundMode
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.aSessionInfo
import io.github.magalz.borealis.stream.ui.StreamChromeRoot
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The background on the screens outside the game (ADR 0061, decisions 1 and 2): every one of them
 * sits on it, with no difference between them, it goes when the phase becomes Streaming, and the
 * option of the viewer says whether it moves, stands still or is the plain page colour.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class OutsideGameBackgroundTest {
    @get:Rule val composeRule = createComposeRule()

    private var look by mutableStateOf(BorealisLook())

    private fun show(session: StreamSession) {
        composeRule.setContent { StreamChromeRoot(session, {}, look) }
        composeRule.waitForIdle()
    }

    private fun frame(): PixelMap = composeRule.frame(OUTSIDE_GAME_BACKGROUND_TAG)

    private fun assertBackground(screen: OutsideScreen) {
        show(sessionAt(screen))

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertIsDisplayed()
    }

    @Test fun `Starting stands on the background`() = assertBackground(OutsideScreen.STARTING)

    @Test
    fun `the question about a running app stands on it`() = assertBackground(OutsideScreen.CONFLICT)

    @Test fun `a failure stands on it`() = assertBackground(OutsideScreen.FAILED)

    @Test fun `Quitting stands on it`() = assertBackground(OutsideScreen.QUITTING)

    @Test fun `Disconnecting stands on it`() = assertBackground(OutsideScreen.DISCONNECTING)

    @Test
    fun `the background goes when the phase becomes Streaming, not when the stream is reported`() {
        val session = sessionAt()
        show(session)

        // The connection reports the stream and waits for the first frame: still Starting.
        session.onConnectionStarted(aSessionInfo)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertIsDisplayed()

        session.onFirstFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertDoesNotExist()
    }

    @Test
    fun `a moving background moves`() {
        composeRule.mainClock.autoAdvance = false
        show(sessionAt(visuals = StreamVisuals(background = BackgroundMode.MOVING)))
        composeRule.mainClock.advanceTimeBy(1_000)
        val first = frame()

        composeRule.mainClock.advanceTimeBy(5_000)

        assertWithMessage("the picture").that(first.buffer.contentEquals(frame().buffer)).isFalse()
    }

    @Test
    fun `a still background is one frame that never changes`() {
        composeRule.mainClock.autoAdvance = false
        show(sessionAt(visuals = StreamVisuals(background = BackgroundMode.STILL)))
        composeRule.mainClock.advanceTimeBy(1_000)
        val first = frame()

        composeRule.mainClock.advanceTimeBy(5_000)

        assertWithMessage("the picture").that(first.buffer.contentEquals(frame().buffer)).isTrue()
    }

    @Test
    fun `with the background off the screens stand on the page colour`() {
        show(sessionAt(visuals = StreamVisuals(background = BackgroundMode.OFF)))

        assertThat(frame()[CORNER, CORNER].isNear(BorealisColors().page)).isTrue()
    }

    @Test
    fun `a style of the viewer that is off is the page colour, whatever the option`() {
        show(sessionAt(visuals = StreamVisuals(backgroundStyle = BackgroundStyle.Off)))

        assertThat(frame()[CORNER, CORNER].isNear(BorealisColors().page)).isTrue()
    }

    @Test
    fun `the intensity of the look dims the background as it does in the Library`() {
        show(sessionAt(visuals = StreamVisuals(background = BackgroundMode.STILL)))
        val full = frame().distanceFromPage()

        composeRule.runOnIdle {
            look = BorealisLook(backgroundIntensity = BackgroundIntensity.Quarter)
        }
        composeRule.waitForIdle()
        val dimmed = frame().distanceFromPage()

        assertThat(full).isGreaterThan(0.0)
        assertThat(dimmed).isLessThan(full)
    }

    /** How far the picture is from the page colour, on average over its pixels and channels. */
    private fun PixelMap.distanceFromPage(): Double {
        val page = BorealisColors().page
        var sum = 0.0
        for (pixel in buffer) {
            val colour = Color(pixel)
            sum += abs(colour.red - page.red)
            sum += abs(colour.green - page.green)
            sum += abs(colour.blue - page.blue)
        }
        return sum / buffer.size
    }

    private companion object {
        const val CORNER = 2
    }
}
