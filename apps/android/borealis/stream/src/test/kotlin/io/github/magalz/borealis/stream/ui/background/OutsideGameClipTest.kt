package io.github.magalz.borealis.stream.ui.background

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.stream.BackgroundMode
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.ui.StreamChromeRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The clip of a video style plays only while no decoder of the stream is held (ADR 0061, decision
 * 6): from the start until the connection begins to set the stream up, which is before the stream's
 * decoder is configured, and again after the connection has stopped. While one is held a clip shows
 * its still, and a drawn style keeps moving.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class OutsideGameClipTest {
    @get:Rule val composeRule = createComposeRule()

    private val clip = StreamVisuals(backgroundStyle = BackgroundStyle.LightMist)

    private fun show(session: StreamSession) {
        composeRule.setContent { StreamChromeRoot(session, {}, BorealisLook()) }
        composeRule.waitForIdle()
    }

    private fun change(edit: () -> Unit) {
        composeRule.runOnIdle(edit)
        composeRule.waitForIdle()
    }

    private fun assertClipPlays() = composeRule.onNodeWithTag(VIDEO).assertExists()

    private fun assertStillOnly() {
        composeRule.onNodeWithTag(VIDEO).assertDoesNotExist()
        composeRule.onNodeWithTag(STILL).assertIsDisplayed()
    }

    @Test
    fun `the clip plays while the start has not reached the connection`() {
        val session = sessionAt(visuals = clip)
        show(session)
        assertClipPlays()

        change { session.onStage(StartStage.STARTING_APP) }

        assertClipPlays()
    }

    @Test
    fun `the clip is released when the connection begins, and its still stays`() {
        val session = sessionAt(visuals = clip)
        show(session)
        assertClipPlays()

        change { session.onStage(StartStage.PREPARING_DISPLAY) }

        assertStillOnly()
    }

    @Test
    fun `an error before the connection plays the clip`() {
        val session = sessionAt(OutsideScreen.FAILED, clip)

        show(session)

        assertClipPlays()
    }

    @Test
    fun `the question about a running app plays the clip`() {
        show(sessionAt(OutsideScreen.CONFLICT, clip))

        assertClipPlays()
    }

    @Test
    fun `an error while the decoder is held shows the still`() {
        val session = sessionAt(visuals = clip)
        session.onStage(StartStage.PREPARING_DISPLAY)
        session.onFailed(aFailure)

        show(session)

        assertStillOnly()
    }

    @Test
    fun `Quitting shows the still while the decoder is held`() {
        val session = sessionAt(visuals = clip)
        session.onStage(StartStage.PREPARING_DISPLAY)
        session.quitApp()

        show(session)

        assertStillOnly()
    }

    @Test
    fun `Disconnecting shows the still until the connection has stopped`() {
        val session = sessionAt(visuals = clip)
        session.onStage(StartStage.PREPARING_DISPLAY)
        session.disconnect()

        show(session)

        assertStillOnly()
    }

    @Test
    fun `the clip plays again once the connection has stopped`() {
        val session = sessionAt(visuals = clip)
        session.onStage(StartStage.PREPARING_DISPLAY)
        session.disconnect()
        show(session)
        assertStillOnly()

        change { session.onConnectionStopped() }

        assertClipPlays()
    }

    @Test
    fun `a still option never makes a player, and shows the still`() {
        val session = sessionAt(visuals = clip.copy(background = BackgroundMode.STILL))

        show(session)

        assertStillOnly()
    }

    @Test
    fun `a drawn style keeps moving while the decoder is held`() {
        composeRule.mainClock.autoAdvance = false
        val session = sessionAt(OutsideScreen.STARTING, StreamVisuals())
        session.onStage(StartStage.PREPARING_DISPLAY)
        session.onFailed(aFailure)
        show(session)
        composeRule.mainClock.advanceTimeBy(1_000)
        val first = composeRule.frame(OUTSIDE_GAME_BACKGROUND_TAG)

        composeRule.mainClock.advanceTimeBy(5_000)

        assertWithMessage("the picture")
            .that(first.buffer.contentEquals(composeRule.frame(OUTSIDE_GAME_BACKGROUND_TAG).buffer))
            .isFalse()
    }
}
