package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.background.BackgroundTags
import io.github.magalz.borealis.designsystem.background.FakeBackgroundPlayers
import io.github.magalz.borealis.designsystem.background.LocalBackgroundPlayerFactory
import io.github.magalz.borealis.designsystem.background.TestLifecycle
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A panel inside the layer of a glass host, as the popups of the stream are (ADR 0061, decision 5),
 * does what a dialog window does over the host (ADR 0057, decision 8): while it is composed it
 * blurs the background and holds it still, and it knows the glass that it draws. It is not a
 * window, so the blur reaches the background only: the panel and what else is in the layer stay
 * sharp. Nothing here wires a dialog: a test composable stands for the panel.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PanelGlassTest {
    @get:Rule val composeRule = createComposeRule()

    private val players = FakeBackgroundPlayers()
    private val lifecycle = TestLifecycle()

    private var open by mutableStateOf(false)
    private var overlays: GlassOverlays? = null
    private var seen: PanelGlass? = null

    // The blur of the background

    @Test
    fun `an open panel blurs the background, which is sharp again when the panel is gone`() {
        showHost(background = { HalfAndHalf() })
        assertThat(edgeStep(ROOT, BACKGROUND_REACH)).isGreaterThan(SHARP)

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()
        assertThat(edgeStep(ROOT, BACKGROUND_REACH)).isLessThan(SMOOTH)

        composeRule.runOnIdle { open = false }
        composeRule.waitForIdle()
        assertThat(edgeStep(ROOT, BACKGROUND_REACH)).isGreaterThan(SHARP)
    }

    @Test
    fun `the panel and the rest of the layer stay sharp over the blurred background`() {
        showHost(background = { HalfAndHalf() }, content = { Sharp(OTHER, Alignment.BottomStart) })
        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        assertThat(edgeStep(ROOT, BACKGROUND_REACH)).isLessThan(SMOOTH)
        assertThat(edgeStep(PANEL, PANEL_REACH)).isGreaterThan(SHARP)
        assertThat(edgeStep(OTHER, PANEL_REACH)).isGreaterThan(SHARP)
    }

    @Test
    @Config(sdk = [API_NO_BLUR])
    fun `on API 30 an open panel does not blur the background`() {
        showHost(background = { HalfAndHalf() })

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        assertThat(edgeStep(ROOT, BACKGROUND_REACH)).isGreaterThan(SHARP)
    }

    @Test
    fun `where the glass is off an open panel does not blur the background`() {
        showHost(
            look = BorealisLook(transparency = Transparency.Off),
            background = { HalfAndHalf() },
        )

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        assertThat(edgeStep(ROOT, BACKGROUND_REACH)).isGreaterThan(SHARP)
    }

    // The frozen background

    @Test
    fun `an open panel holds a clip on its still, and the clip plays again when it is gone`() {
        showHost(BackgroundStyle.NorthernLights, useBackground = true)
        composeRule.runOnIdle { players.live.single().showFirstFrame() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertDoesNotExist()
        val playing = players.live.single()

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
        assertThat(playing.released).isTrue()
        assertThat(players.live).isEmpty()

        composeRule.runOnIdle { open = false }
        composeRule.waitForIdle()
        assertThat(players.live).hasSize(1)
    }

    @Test
    fun `an open panel holds a drawn background still, and it moves again when it is gone`() {
        composeRule.mainClock.autoAdvance = false
        showHost(BackgroundStyle.AuroraCurtains, useBackground = true)
        composeRule.mainClock.advanceTimeBy(1_000)
        val running = composeRule.frame(ROOT)
        composeRule.mainClock.advanceTimeBy(5_000)
        assertThat(composeRule.frame(ROOT).buffer).isNotEqualTo(running.buffer)

        composeRule.runOnUiThread { open = true }
        composeRule.mainClock.advanceTimeBy(1_000)
        val frozen = composeRule.frame(ROOT)
        composeRule.mainClock.advanceTimeBy(5_000)
        assertThat(composeRule.frame(ROOT).buffer).isEqualTo(frozen.buffer)

        composeRule.runOnUiThread { open = false }
        composeRule.mainClock.advanceTimeBy(1_000)
        val again = composeRule.frame(ROOT)
        composeRule.mainClock.advanceTimeBy(5_000)
        assertThat(composeRule.frame(ROOT).buffer).isNotEqualTo(again.buffer)
    }

    // The glass that the panel draws

    @Test
    fun `an open panel is counted, and it is not once it is gone`() {
        showHost()
        assertThat(overlays?.isOpen).isFalse()

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()
        assertThat(overlays?.isOpen).isTrue()

        composeRule.runOnIdle { open = false }
        composeRule.waitForIdle()
        assertThat(overlays?.isOpen).isFalse()
    }

    @Test
    fun `a panel over a blurred window has the tint and the text colours of the glass`() {
        showHost()
        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        val glass = seen!!
        assertThat(glass.tint).isEqualTo(TINT)
        val spec = glassSpec(BorealisLook(), BackgroundStyle.AuroraCurtains, SDK_S)
        assertThat(glass.colors)
            .isEqualTo(BorealisColors().onGlass(BackgroundStyle.AuroraCurtains, spec))
    }

    @Test
    fun `the panel takes the tint that the floor of the background raises`() {
        showHost(BackgroundStyle.NorthernLights)
        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        assertThat(seen?.tint).isEqualTo(FLOOR_TINT)
    }

    @Test
    @Config(sdk = [API_NO_BLUR])
    fun `on API 30 a panel is open but has no glass, so it keeps its scrim`() {
        showHost()
        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        assertThat(overlays?.isOpen).isTrue()
        assertThat(seen).isNull()
    }

    @Test
    fun `where the glass is off a panel has no glass`() {
        showHost(look = BorealisLook(transparency = Transparency.Off))
        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        assertThat(seen).isNull()
    }

    @Test
    fun `a panel outside any host has no glass and does not fail`() {
        composeRule.setContent { seen = rememberPanelGlass() }
        composeRule.waitForIdle()

        assertThat(seen).isNull()
    }

    // Helpers

    /** The panel: a sharp box, which is composed, and so open, while the flag is set. */
    @Composable
    private fun Panel() {
        overlays = LocalGlassOverlays.current
        if (open) {
            seen = rememberPanelGlass()
            Sharp(PANEL, Alignment.TopStart)
        }
    }

    /** A box whose middle is a step from white to black, in a corner of the layer. */
    @Composable
    private fun Sharp(tag: String, at: Alignment) {
        Box(Modifier.fillMaxSize(), contentAlignment = at) {
            Box(Modifier.size(BOX).testTag(tag)) { HalfAndHalf() }
        }
    }

    private fun showHost(
        style: BackgroundStyle = BackgroundStyle.AuroraCurtains,
        look: BorealisLook = BorealisLook(),
        useBackground: Boolean = false,
        background: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().background(Color.White))
        },
        content: @Composable () -> Unit = {},
    ) {
        val withPanel: @Composable () -> Unit = {
            content()
            Panel()
        }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalBorealisLook provides look,
                LocalBackgroundPlayerFactory provides players,
                LocalLifecycleOwner provides lifecycle,
            ) {
                if (useBackground) {
                    GlassHost(style, Modifier.testTag(ROOT), content = withPanel)
                } else {
                    GlassHostOver(style, background, Modifier.testTag(ROOT), withPanel)
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

    /** The largest step in red between two neighbouring pixels of the middle row of [tag]. */
    private fun edgeStep(tag: String, reach: Int): Float {
        val map = composeRule.frame(tag)
        val y = map.height / 2
        return (map.width / 2 - reach until map.width / 2 + reach).maxOf { x ->
            abs(map[x + 1, y].red - map[x, y].red)
        }
    }

    private companion object {
        const val ROOT = "root"
        const val PANEL = "panel"
        const val OTHER = "other"
        const val API_NO_BLUR = 30
        const val SDK_S = 31
        const val TINT = 0.4f
        const val FLOOR_TINT = 0.7f
        const val BACKGROUND_REACH = 150
        const val PANEL_REACH = 80
        const val SHARP = 0.5f
        const val SMOOTH = 0.1f
        val BOX = 100.dp
    }
}
