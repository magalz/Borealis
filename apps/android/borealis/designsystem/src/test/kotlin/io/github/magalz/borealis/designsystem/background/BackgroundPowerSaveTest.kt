package io.github.magalz.borealis.designsystem.background

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * With the battery saver on the background behaves as it does when the system removes animations:
 * one still frame and the still of a clip, no player and no frames. It follows the saver while it
 * is on screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BackgroundPowerSaveTest {
    @get:Rule val composeRule = createComposeRule()

    private val players = FakeBackgroundPlayers()
    private val lifecycle = TestLifecycle()

    private fun show(style: BackgroundStyle) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalBackgroundPlayerFactory provides players,
                LocalLifecycleOwner provides lifecycle,
            ) {
                BorealisBackground(style, Modifier.fillMaxSize().testTag(BG))
            }
        }
        composeRule.waitForIdle()
    }

    private fun frame(): PixelMap = composeRule.onNodeWithTag(BG).captureToImage().toPixelMap()

    private fun assertSame(first: PixelMap, second: PixelMap, what: String) =
        assertWithMessage(what).that(first.buffer.contentEquals(second.buffer)).isTrue()

    private fun assertMoved(first: PixelMap, second: PixelMap, what: String) =
        assertWithMessage(what).that(first.buffer.contentEquals(second.buffer)).isFalse()

    @Test
    fun `with the saver on a Compose style is one still frame that never changes`() {
        setPowerSave(true)
        composeRule.mainClock.autoAdvance = false
        show(BackgroundStyle.AuroraCurtains)
        val first = frame()

        composeRule.mainClock.advanceTimeBy(5_000)

        assertSame(first, frame(), "the picture")
    }

    @Test
    fun `with the saver on a video style shows its still and makes no player`() {
        setPowerSave(true)
        show(BackgroundStyle.Starfield)

        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
        composeRule.onNodeWithTag(BackgroundTags.VIDEO).assertDoesNotExist()
        assertThat(players.created).isEmpty()
    }

    @Test
    fun `the saver coming on stops the frames and going off starts them again`() {
        composeRule.mainClock.autoAdvance = false
        show(BackgroundStyle.SoftGlow)
        composeRule.mainClock.advanceTimeBy(1_000)
        val running = frame()
        composeRule.mainClock.advanceTimeBy(5_000)
        assertMoved(running, frame(), "the picture before the saver")

        composeRule.runOnUiThread { setPowerSave(true) }
        composeRule.mainClock.advanceTimeBy(1_000)
        val resting = frame()
        composeRule.mainClock.advanceTimeBy(5_000)
        assertSame(resting, frame(), "the picture with the saver")

        composeRule.runOnUiThread { setPowerSave(false) }
        composeRule.mainClock.advanceTimeBy(1_000)
        val again = frame()
        composeRule.mainClock.advanceTimeBy(5_000)
        assertMoved(again, frame(), "the picture after the saver")
    }

    @Test
    fun `the saver coming on releases the player and shows the still, going off plays again`() {
        show(BackgroundStyle.LightMist)
        val first = players.live.single()
        assertThat(first.playing).isTrue()

        composeRule.runOnUiThread { setPowerSave(true) }
        composeRule.waitForIdle()
        assertThat(first.released).isTrue()
        assertThat(players.live).isEmpty()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()

        composeRule.runOnUiThread { setPowerSave(false) }
        composeRule.waitForIdle()
        assertThat(players.live).hasSize(1)
        assertThat(players.live.single().playing).isTrue()
    }

    private companion object {
        const val BG = "background"
    }
}
