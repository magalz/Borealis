package io.github.magalz.borealis.designsystem.background

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * High contrast dims the animated background (ADR 0057, decision 11): the page colour is laid over
 * it at [BorealisTokens.HighContrast.Dim], so that text straight on the background keeps 7:1 over
 * the brightest pixel of every background (docs/ux/accessibility.md). Frames are 720 x 1280 pixels.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BackgroundDimTest {
    @get:Rule val composeRule = createComposeRule()

    private val players = FakeBackgroundPlayers()
    private val lifecycle = TestLifecycle()

    @Test
    fun `with high contrast a drawn background shows its light at the dim that is left`() {
        noAnimations()
        start(BackgroundStyle.SoftGlow)
        val normal = composeRule.frame(BG).brightest()

        look = BorealisLook(highContrast = true)
        composeRule.waitForIdle()
        val dimmed = composeRule.frame(BG).brightest()

        // The light above the page is what the page colour over it leaves: 1 less the dim.
        val kept = 1f - BorealisTokens.HighContrast.Dim
        assertThat(normal).isGreaterThan(0.3f)
        assertThat(dimmed).isWithin(0.02f).of(normal * kept)
    }

    @Test
    fun `without high contrast the background is not dimmed`() {
        noAnimations()
        start(BackgroundStyle.SoftGlow, BorealisLook(thickFocus = true))

        assertThat(composeRule.frame(BG).brightest()).isGreaterThan(0.3f)
    }

    @Test
    fun `the dim reaches the still of a video style`() {
        noAnimations()
        start(BackgroundStyle.NorthernLights)
        val normal = composeRule.frame(BG).brightest()

        look = BorealisLook(highContrast = true)
        composeRule.waitForIdle()
        val dimmed = composeRule.frame(BG).brightest()

        assertThat(normal).isGreaterThan(0.3f)
        assertThat(dimmed).isLessThan(normal * 0.25f)
    }

    @Test
    fun `the plain page is as it is with high contrast`() {
        start(BackgroundStyle.Off, BorealisLook(highContrast = true))

        val frame = composeRule.frame(BG)
        assertThat(frame.brightest()).isLessThan(0.01f)
        assertThat(frame[0, 0]).isEqualTo(BorealisTokens.Color.Page)
    }

    @Test
    fun `turning high contrast on and off does not make the video player again`() {
        start(BackgroundStyle.FlowingLight)
        val first = players.live.single()

        look = BorealisLook(highContrast = true)
        composeRule.waitForIdle()
        look = BorealisLook()
        composeRule.waitForIdle()

        assertThat(players.created).hasSize(1)
        assertThat(players.live.single()).isSameInstanceAs(first)
        assertThat(first.released).isFalse()
    }

    private var look by mutableStateOf(BorealisLook())

    private fun start(style: BackgroundStyle, first: BorealisLook = BorealisLook()) {
        look = first
        composeRule.setContent { Specimen(style) }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Specimen(style: BackgroundStyle) {
        CompositionLocalProvider(
            LocalBackgroundPlayerFactory provides players,
            LocalLifecycleOwner provides lifecycle,
        ) {
            BorealisTheme(look = look) {
                BorealisBackground(style, Modifier.fillMaxSize().testTag(BG))
            }
        }
    }

    /** The most light above the page, as the sum of red, green and blue, of the lit top half. */
    private fun PixelMap.brightest(): Float =
        (0 until height / 2 step STEP).maxOf { y ->
            (0 until width step STEP).maxOf { x -> this[x, y].lift() }
        }

    private fun Color.lift() = red + green + blue - PAGE_SUM

    private companion object {
        const val BG = "background"
        const val STEP = 8
        val PAGE_SUM = BorealisTokens.Color.Page.let { it.red + it.green + it.blue }
    }
}
