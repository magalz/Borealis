package io.github.magalz.borealis.library.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The frame of the Library draws the background once, behind the rail and the screen. The tests
 * take the still frame of a system without animations, so the pixels are the same on every run.
 */
@RunWith(AndroidJUnit4::class)
class LibraryBackgroundTest {
    @get:Rule val composeRule = createComposeRule()

    private val screen: @Composable () -> Unit = { Box(Modifier.fillMaxSize()) }
    private val actions = RailActions({}, {}, {})

    private fun show(
        formFactor: FormFactor,
        style: BackgroundStyle,
        look: BorealisLook = BorealisLook(),
    ) {
        composeRule.setContent {
            LibraryScaffold(
                formFactor,
                RailSelection.Hosts,
                actions,
                backgroundStyle = style,
                look = look,
                content = screen,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV frame draws the style behind the screen, and the rail is glass over it`() {
        noAnimations()
        show(FormFactor.Tv, BackgroundStyle.AuroraCurtains)

        val shot = frame()
        // The screen is right of the 220dp rail: from pixel 440 at xhdpi.
        assertThat(shot.maxLift(RAIL_END_PX, shot.width, 0f, 0.5f)).isIn(LIT)
        assertThat(shot.maxLift(RAIL_END_PX, shot.width, 0.75f, 1f)).isLessThan(NONE)
        // The rail is a tint of the surface colour over the blurred background, not the chrome
        // colour that it is where the glass is solid.
        val rail = shot[RAIL_PROBE_PX, shot.height / 2]
        val chrome = BorealisTokens.Color.Chrome
        val distance =
            abs(rail.red - chrome.red) +
                abs(rail.green - chrome.green) +
                abs(rail.blue - chrome.blue)
        assertThat(distance).isGreaterThan(GLASS_NOT_CHROME)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV frame with the transparency Off leaves the rail its own colour`() {
        noAnimations()
        show(
            FormFactor.Tv,
            BackgroundStyle.AuroraCurtains,
            BorealisLook(transparency = Transparency.Off),
        )

        val shot = frame()
        assertThat(shot.maxLift(RAIL_END_PX, shot.width, 0f, 0.5f)).isIn(LIT)
        assertThat(shot[RAIL_PROBE_PX, shot.height / 2]).isEqualTo(BorealisTokens.Color.Chrome)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone frame draws the style across the whole width`() {
        noAnimations()
        show(FormFactor.Phone, BackgroundStyle.SoftGlow)

        val shot = frame()
        assertThat(shot.maxLift(0, shot.width / 4, 0f, 0.5f)).isIn(LIT)
        assertThat(shot.maxLift(shot.width * 3 / 4, shot.width, 0f, 0.5f)).isIn(LIT)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a frame with no style given draws the style that ships, the one of the settings default`() {
        noAnimations()
        composeRule.setContent {
            LibraryScaffold(FormFactor.Tv, RailSelection.Hosts, actions, content = screen)
        }
        composeRule.waitForIdle()

        val shot = frame()
        assertThat(BackgroundStyle.Default).isEqualTo(BackgroundStyle.AuroraCurtains)
        assertThat(shot.maxLift(RAIL_END_PX, shot.width, 0f, 0.5f)).isIn(LIT)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a frame given the plain page draws the plain page colour`() {
        show(FormFactor.Tv, BackgroundStyle.Off)

        val shot = frame()
        assertThat(shot.maxLift(RAIL_END_PX, shot.width, 0f, 1f)).isLessThan(NONE)
        assertThat(shot.minLift(RAIL_END_PX, shot.width, 0f, 1f)).isGreaterThan(-NONE)
    }

    private fun frame(): PixelMap = composeRule.onRoot().captureToImage().toPixelMap()

    private companion object {
        const val RAIL_END_PX = 440
        const val RAIL_PROBE_PX = 20
        const val SAMPLE_STEP = 8
        const val NONE = 0.01f

        // The glass rail is about 0.1 from the chrome colour, the sum of red, green and blue.
        const val GLASS_NOT_CHROME = 0.05f

        // A band or a glow lights the page by 0.3 and more (the sum of red, green and blue); a
        // frame where nothing is drawn is white, far above 1.2.
        val LIT = Range.open(0.3f, 1.2f)

        val PAGE_SUM = BorealisTokens.Color.Page.let { it.red + it.green + it.blue }

        fun PixelMap.lifts(fromX: Int, toX: Int, from: Float, to: Float): List<Float> =
            ((height * from).toInt() until (height * to).toInt() step SAMPLE_STEP).flatMap { y ->
                (fromX until toX step SAMPLE_STEP).map { x ->
                    val pixel = this[x, y]
                    pixel.red + pixel.green + pixel.blue - PAGE_SUM
                }
            }

        fun PixelMap.maxLift(fromX: Int, toX: Int, from: Float, to: Float) =
            lifts(fromX, toX, from, to).max()

        fun PixelMap.minLift(fromX: Int, toX: Int, from: Float, to: Float) =
            lifts(fromX, toX, from, to).min()
    }
}
