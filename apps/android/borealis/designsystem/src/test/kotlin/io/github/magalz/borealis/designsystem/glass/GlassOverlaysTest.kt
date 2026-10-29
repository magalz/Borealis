package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.background.BackgroundTags
import io.github.magalz.borealis.designsystem.background.FakeBackgroundPlayers
import io.github.magalz.borealis.designsystem.background.LocalBackgroundPlayerFactory
import io.github.magalz.borealis.designsystem.background.TestLifecycle
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The dialogs and sheets that open over a glass host: they are counted, and while one is open over
 * a device that can blur, the host blurs its window and holds its background still. Nothing here
 * wires a dialog: a test composable stands for it.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GlassOverlaysTest {
    @get:Rule val composeRule = createComposeRule()

    private val players = FakeBackgroundPlayers()
    private val lifecycle = TestLifecycle()

    private var first by mutableStateOf(false)
    private var second by mutableStateOf(false)
    private var overlays: GlassOverlays? = null
    private var glassSeenByOverlay: OverlayGlass? = null

    // The counter

    @Test
    fun `an overlay that is composed is open and one that is gone is not`() {
        showHost { Overlays() }
        assertThat(overlays?.isOpen).isFalse()

        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()
        assertThat(overlays?.isOpen).isTrue()

        composeRule.runOnIdle { first = false }
        composeRule.waitForIdle()
        assertThat(overlays?.isOpen).isFalse()
    }

    @Test
    fun `two overlays count twice, so the host stays open until both are gone`() {
        showHost { Overlays() }

        composeRule.runOnIdle {
            first = true
            second = true
        }
        composeRule.waitForIdle()
        assertThat(overlays?.isOpen).isTrue()

        composeRule.runOnIdle { first = false }
        composeRule.waitForIdle()
        assertThat(overlays?.isOpen).isTrue()

        composeRule.runOnIdle { second = false }
        composeRule.waitForIdle()
        assertThat(overlays?.isOpen).isFalse()
    }

    @Test
    fun `an overlay outside any host is not counted and does not fail`() {
        composeRule.setContent { OverlayOpen() }
        composeRule.waitForIdle()
    }

    // The decision that a dialog reads

    @Test
    fun `an overlay over a blurred window knows it, with the tint of its panel`() {
        showHost { Overlays() }
        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()

        assertThat(glassSeenByOverlay).isEqualTo(OverlayGlass(tint = 0.4f))
    }

    @Test
    fun `the panel of an overlay takes the tint that the floor of the background raises`() {
        showHost(BackgroundStyle.NorthernLights) { Overlays() }
        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()

        assertThat(glassSeenByOverlay).isEqualTo(OverlayGlass(tint = 0.7f))
    }

    @Test
    @Config(sdk = [API_NO_BLUR])
    fun `on API 30 an overlay is not over a blurred window, and keeps its scrim`() {
        showHost { Overlays() }
        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()

        assertThat(glassSeenByOverlay).isNull()
    }

    @Test
    fun `an overlay is not over a blurred window where the glass is off`() {
        showHost(look = BorealisLook(transparency = Transparency.Off)) { Overlays() }
        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()

        assertThat(glassSeenByOverlay).isNull()
    }

    @Test
    fun `an overlay outside any host is not over a blurred window`() {
        composeRule.setContent {
            OverlayOpen()
            glassSeenByOverlay = rememberOverlayGlass()
        }
        composeRule.waitForIdle()

        assertThat(glassSeenByOverlay).isNull()
    }

    // The window blur

    @Test
    fun `an open overlay blurs the whole window, and the window is sharp again when it closes`() {
        showHost(
            background = { HalfAndHalf() },
            content = { Overlays() },
        )
        assertThat(edgeStep()).isGreaterThan(SHARP)

        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()
        assertThat(edgeStep()).isLessThan(SMOOTH)

        composeRule.runOnIdle { first = false }
        composeRule.waitForIdle()
        assertThat(edgeStep()).isGreaterThan(SHARP)
    }

    @Test
    @Config(sdk = [API_NO_BLUR])
    fun `on API 30 an open overlay does not blur the window`() {
        showHost(background = { HalfAndHalf() }, content = { Overlays() })

        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()

        assertThat(edgeStep()).isGreaterThan(SHARP)
    }

    @Test
    fun `where the glass is off an open overlay does not blur the window`() {
        showHost(
            look = BorealisLook(transparency = Transparency.Off),
            background = { HalfAndHalf() },
            content = { Overlays() },
        )

        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()

        assertThat(edgeStep()).isGreaterThan(SHARP)
    }

    // The frozen background

    @Test
    fun `an open overlay holds a clip on its still, and the clip plays again when it closes`() {
        showHost(BackgroundStyle.NorthernLights, useBackground = true) { Overlays() }
        composeRule.runOnIdle { players.live.single().showFirstFrame() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertDoesNotExist()
        val playing = players.live.single()

        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
        assertThat(playing.released).isTrue()
        assertThat(players.live).isEmpty()

        composeRule.runOnIdle { first = false }
        composeRule.waitForIdle()
        assertThat(players.live).hasSize(1)
    }

    @Test
    fun `the still stays over a clip that starts again until the new player has a frame`() {
        showHost(BackgroundStyle.NorthernLights, useBackground = true) { Overlays() }
        composeRule.runOnIdle { players.live.single().showFirstFrame() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()

        composeRule.runOnIdle { first = false }
        composeRule.waitForIdle()

        // The new player has drawn nothing yet: without the still, the texture would show black.
        assertThat(players.live).hasSize(1)
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()

        composeRule.runOnIdle { players.live.single().showFirstFrame() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertDoesNotExist()
    }

    @Test
    fun `an open overlay holds a drawn background still, and it moves again when it closes`() {
        composeRule.mainClock.autoAdvance = false
        showHost(BackgroundStyle.AuroraCurtains, useBackground = true) { Overlays() }
        composeRule.mainClock.advanceTimeBy(1_000)
        val running = composeRule.frame(ROOT)
        composeRule.mainClock.advanceTimeBy(5_000)
        assertThat(composeRule.frame(ROOT).buffer).isNotEqualTo(running.buffer)

        composeRule.runOnUiThread { first = true }
        composeRule.mainClock.advanceTimeBy(1_000)
        val frozen = composeRule.frame(ROOT)
        composeRule.mainClock.advanceTimeBy(5_000)
        assertThat(composeRule.frame(ROOT).buffer).isEqualTo(frozen.buffer)

        composeRule.runOnUiThread { first = false }
        composeRule.mainClock.advanceTimeBy(1_000)
        val again = composeRule.frame(ROOT)
        composeRule.mainClock.advanceTimeBy(5_000)
        assertThat(composeRule.frame(ROOT).buffer).isNotEqualTo(again.buffer)
    }

    @Test
    @Config(sdk = [API_NO_BLUR])
    fun `on API 30 an open overlay is open but holds nothing still, which is what the clip reads`() {
        showHost { Overlays() }

        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()

        assertThat(overlays?.isOpen).isTrue()
        assertThat(overlays?.windowBlurred).isFalse()
    }

    @Test
    fun `where the glass is off an open overlay leaves the clip playing`() {
        showHost(
            BackgroundStyle.NorthernLights,
            look = BorealisLook(transparency = Transparency.Off),
            useBackground = true,
        ) {
            Overlays()
        }

        composeRule.runOnIdle { first = true }
        composeRule.waitForIdle()

        assertThat(players.live).hasSize(1)
    }

    // Helpers

    /** The two test overlays: each one is open while its flag is set. */
    @Composable
    private fun Overlays() {
        overlays = LocalGlassOverlays.current
        if (first) {
            OverlayOpen()
            glassSeenByOverlay = rememberOverlayGlass()
        }
        if (second) OverlayOpen()
    }

    /**
     * Shows a host over the given look. The background is [background], or with [useBackground] the
     * host's own, the one of [style], on the fake players.
     */
    private fun showHost(
        style: BackgroundStyle = BackgroundStyle.AuroraCurtains,
        look: BorealisLook = BorealisLook(),
        useBackground: Boolean = false,
        background: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().background(Color.White))
        },
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalBorealisLook provides look,
                LocalBackgroundPlayerFactory provides players,
                LocalLifecycleOwner provides lifecycle,
            ) {
                if (useBackground) {
                    GlassHost(style, Modifier.testTag(ROOT), content = content)
                } else {
                    GlassHostOver(style, background, Modifier.testTag(ROOT), content)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun HalfAndHalf() {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize().background(Color.White))
            Box(Modifier.weight(1f).fillMaxSize().background(Color.Black))
        }
    }

    /** The largest step in red between two neighbouring pixels of a row across the middle. */
    private fun edgeStep(): Float {
        val map = composeRule.frame(ROOT)
        val y = map.height / 2
        return (map.width / 2 - EDGE_REACH until map.width / 2 + EDGE_REACH).maxOf { x ->
            abs(map[x + 1, y].red - map[x, y].red)
        }
    }

    private companion object {
        const val ROOT = "root"
        const val API_NO_BLUR = 30
        const val EDGE_REACH = 150
        const val SHARP = 0.5f
        const val SMOOTH = 0.1f
    }
}
