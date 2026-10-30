package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.background.BackgroundTags
import io.github.magalz.borealis.designsystem.background.FakeBackgroundPlayers
import io.github.magalz.borealis.designsystem.background.LocalBackgroundPlayerFactory
import io.github.magalz.borealis.designsystem.background.TestLifecycle
import io.github.magalz.borealis.designsystem.testing.frame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The two ways the owner of a [GlassHost] holds its background back (ADR 0061, decisions 3 and 6):
 * [frozen] stops every style, and [clipsPlay] off takes the decoder away from a clip style, which
 * shows its still, while a drawn style goes on moving.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GlassHostBackgroundControlsTest {
    @get:Rule val composeRule = createComposeRule()

    private val players = FakeBackgroundPlayers()
    private val lifecycle = TestLifecycle()
    private var frozen by mutableStateOf(false)
    private var clipsPlay by mutableStateOf(true)

    private fun show(style: BackgroundStyle) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalBackgroundPlayerFactory provides players,
                LocalLifecycleOwner provides lifecycle,
            ) {
                GlassHost(
                    style,
                    Modifier.fillMaxSize().testTag(ROOT),
                    frozen = frozen,
                    clipsPlay = clipsPlay,
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun change(edit: () -> Unit) {
        composeRule.runOnIdle(edit)
        composeRule.waitForIdle()
    }

    private fun assertSame(first: PixelMap, second: PixelMap, what: String) =
        assertWithMessage(what).that(first.buffer.contentEquals(second.buffer)).isTrue()

    private fun assertMoved(first: PixelMap, second: PixelMap, what: String) =
        assertWithMessage(what).that(first.buffer.contentEquals(second.buffer)).isFalse()

    @Test
    fun `a frozen host holds a drawn style on one frame that never changes`() {
        composeRule.mainClock.autoAdvance = false
        frozen = true
        show(BackgroundStyle.AuroraCurtains)
        val first = composeRule.frame(ROOT)

        composeRule.mainClock.advanceTimeBy(5_000)

        assertSame(first, composeRule.frame(ROOT), "the picture of a frozen host")
    }

    @Test
    fun `a frozen host shows the still of a clip and makes no player`() {
        frozen = true
        show(BackgroundStyle.LightMist)

        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
        composeRule.onNodeWithTag(BackgroundTags.VIDEO).assertDoesNotExist()
        assertThat(players.created).isEmpty()
    }

    @Test
    fun `a clip that may not play at the start makes no player and shows its still`() {
        clipsPlay = false
        show(BackgroundStyle.Starfield)

        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
        composeRule.onNodeWithTag(BackgroundTags.VIDEO).assertDoesNotExist()
        assertThat(players.created).isEmpty()
    }

    @Test
    fun `a clip that may no longer play is released and shows its still, and plays when it may`() {
        show(BackgroundStyle.NorthernLights)
        val first = players.live.single()
        assertThat(first.playing).isTrue()

        change { clipsPlay = false }

        assertThat(first.released).isTrue()
        assertThat(players.live).isEmpty()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()

        change { clipsPlay = true }

        assertThat(players.live).hasSize(1)
        assertThat(players.live.single().playing).isTrue()
    }

    @Test
    fun `a drawn style goes on moving while clips may not play`() {
        composeRule.mainClock.autoAdvance = false
        clipsPlay = false
        show(BackgroundStyle.AuroraCurtains)
        composeRule.mainClock.advanceTimeBy(1_000)
        val first = composeRule.frame(ROOT)

        composeRule.mainClock.advanceTimeBy(5_000)

        assertMoved(first, composeRule.frame(ROOT), "the picture of a drawn style")
    }

    private companion object {
        const val ROOT = "root"
    }
}
