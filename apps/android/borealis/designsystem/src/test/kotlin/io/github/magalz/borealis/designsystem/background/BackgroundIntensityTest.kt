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
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.BackgroundIntensity
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Background intensity of the look (ADR 0058, decision 8): at 75, 50 and 25 percent the page
 * colour lies over the animated background at the veil of the level, 0.25, 0.5 and 0.75, and at 100
 * percent nothing does. With high contrast the stronger of the veil and its dim wins. Frames are
 * 720 x 1280 pixels.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BackgroundIntensityTest {
    @get:Rule val composeRule = createComposeRule()

    private val players = FakeBackgroundPlayers()
    private val lifecycle = TestLifecycle()

    private val veiled = BackgroundIntensity.entries.filter { it != BackgroundIntensity.Full }

    private var look by mutableStateOf(BorealisLook())
    private var frozen by mutableStateOf(false)

    // The veil

    @Test
    fun `a drawn background shows the light that the veil leaves over the page`() {
        noAnimations()
        start(BackgroundStyle.SoftGlow)
        val full = composeRule.frame(BG).brightest()
        assertThat(full).isGreaterThan(0.3f)

        veiled.forEach { level ->
            choose(level)

            val frame = composeRule.frame(BG)
            // The light above the page is what the page colour over it leaves: 1 less the veil.
            assertWithMessage("$level")
                .that(frame.brightest())
                .isWithin(TOLERANCE)
                .of(full * (1f - level.veil))
            // The veil is the page colour, so nothing goes darker than the page.
            assertWithMessage("$level darkest").that(frame.darkest()).isGreaterThan(-NONE)
        }
    }

    @Test
    fun `the veil lies over the still of a clip`() {
        noAnimations()
        start(BackgroundStyle.LightMist)
        val full = composeRule.frame(BG).brightest()
        assertThat(full).isGreaterThan(0.1f)

        veiled.forEach { level ->
            choose(level)

            val frame = composeRule.frame(BG)
            assertWithMessage("$level")
                .that(frame.brightest())
                .isWithin(TOLERANCE)
                .of(full * (1f - level.veil))
            assertWithMessage("$level darkest").that(frame.darkest()).isGreaterThan(-NONE)
        }
    }

    @Test
    fun `at 100 percent no veil lies over the background, whatever it was before`() {
        noAnimations()
        start(BackgroundStyle.SoftGlow)
        val before = composeRule.frame(BG)

        choose(BackgroundIntensity.Quarter)
        assertThat(composeRule.frame(BG).brightest()).isLessThan(before.brightest() * 0.5f)
        choose(BackgroundIntensity.Full)

        assertWithMessage("the frame at 100 percent")
            .that(composeRule.frame(BG).buffer.contentEquals(before.buffer))
            .isTrue()
    }

    @Test
    fun `the plain page is the page at every intensity`() {
        start(BackgroundStyle.Off)

        BackgroundIntensity.entries.forEach { level ->
            choose(level)

            val frame = composeRule.frame(BG)
            assertWithMessage("$level").that(frame.brightest()).isLessThan(NONE)
            assertWithMessage("$level").that(frame.darkest()).isGreaterThan(-NONE)
            assertWithMessage("$level").that(frame[0, 0]).isEqualTo(BorealisTokens.Color.Page)
        }
    }

    // High contrast

    @Test
    fun `with high contrast the frame is the same at every intensity`() {
        noAnimations()
        start(BackgroundStyle.SoftGlow, BorealisLook(highContrast = true))
        val alone = composeRule.frame(BG)

        veiled.forEach { level ->
            choose(level, highContrast = true)

            assertWithMessage("$level with high contrast")
                .that(composeRule.frame(BG).buffer.contentEquals(alone.buffer))
                .isTrue()
        }
    }

    // The still frame

    @Test
    fun `the still of Reduce motion has the veil`() =
        assertStillVeiled(BorealisLook(reduceMotion = ReduceMotion.On))

    @Test
    fun `the still of the battery saver has the veil`() {
        setPowerSave(true)
        assertStillVeiled(BorealisLook())
    }

    @Test
    fun `the still of a window held for a dialog has the veil`() {
        frozen = true
        assertStillVeiled(BorealisLook())
    }

    // The player

    @Test
    fun `changing the intensity does not make the video player again`() {
        start(BackgroundStyle.FlowingLight)
        val first = players.live.single()

        choose(BackgroundIntensity.Half)
        choose(BackgroundIntensity.Full)

        assertThat(players.created).hasSize(1)
        assertThat(players.live.single()).isSameInstanceAs(first)
        assertThat(first.released).isFalse()
    }

    // Helpers

    /** Shows [BackgroundStyle.SoftGlow] still, at each level of the intensity, under [still]. */
    private fun assertStillVeiled(still: BorealisLook) {
        start(BackgroundStyle.SoftGlow, still)
        val full = composeRule.frame(BG).brightest()
        assertThat(full).isGreaterThan(0.3f)

        veiled.forEach { level ->
            look = still.copy(backgroundIntensity = level)
            composeRule.waitForIdle()

            assertWithMessage("$level")
                .that(composeRule.frame(BG).brightest())
                .isWithin(TOLERANCE)
                .of(full * (1f - level.veil))
        }
    }

    private fun choose(level: BackgroundIntensity, highContrast: Boolean = false) {
        look = BorealisLook(backgroundIntensity = level, highContrast = highContrast)
        composeRule.waitForIdle()
    }

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
                BorealisBackground(
                    style,
                    Modifier.fillMaxSize().testTag(BG),
                    frozen = frozen,
                )
            }
        }
    }

    /** The most light above the page, as the sum of red, green and blue, of the whole frame. */
    private fun PixelMap.brightest(): Float =
        (0 until height step STEP).maxOf { y ->
            (0 until width step STEP).maxOf { x -> this[x, y].lift() }
        }

    /** The least light above the page of the whole frame: below zero where it is darker than it. */
    private fun PixelMap.darkest(): Float =
        (0 until height step STEP).minOf { y ->
            (0 until width step STEP).minOf { x -> this[x, y].lift() }
        }

    private fun Color.lift() = red + green + blue - PAGE_SUM

    private companion object {
        const val BG = "background"
        const val STEP = 8
        const val TOLERANCE = 0.02f

        // The sum of red, green and blue above the page colour: negative below it.
        const val NONE = 0.01f
        val PAGE_SUM = BorealisTokens.Color.Page.let { it.red + it.green + it.blue }
    }
}
