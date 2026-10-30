package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.rootFrame
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Quit popup over the game (owner, 2026-10-30): the game stays visible around it, because its
 * scrim is the page colour at 35 percent over what is behind it (the side panel of the menu has the
 * same scrim), and its panel is the firm element, the surface at 85 percent over the blurred video
 * where it is glass, whatever tint the viewer chose for the panel of the menu. Where the rules make
 * the panel solid the scrim is the same. The pixels are read over a white frame, the lightest one:
 * the margin left of the side panel for the scrim, and inside the edge of the popup for its panel.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class PopupOverGameTest {
    @get:Rule val composeRule = createComposeRule()

    private val surface = BorealisColors().surface

    /** The menu over a white video, in [look], with the glass option [videoGlass]. */
    private fun menu(look: BorealisLook = BorealisLook(), videoGlass: Boolean = true) =
        GlassFixture(composeRule).also {
            it.look = look
            it.videoGlass = videoGlass
            it.behind = { Box(Modifier.fillMaxSize().background(Color.White)) }
            it.show()
            it.run(100)
        }

    /** A pixel of the margin: left of the side panel and of the popup, where only scrims are. */
    private fun margin(): Color = composeRule.rootFrame()[MARGIN_PX, MARGIN_PX]

    /** The popup opens over [glass] and its scrim is the page of [look] at 35 percent. */
    private fun assertScrim(glass: GlassFixture, look: BorealisLook = BorealisLook()) {
        val before = margin()

        glass.openQuitPopup()

        assertNear(margin(), borealisColors(look).page.copy(alpha = SCRIM).compositeOver(before))
    }

    @Test
    fun `the scrim of the popup is the page at 35 percent over the game and the glass`() {
        assertScrim(menu())
    }

    @Test
    fun `the scrim is the same where Transparency is Off and the panel is solid`() {
        val look = BorealisLook(transparency = Transparency.Off)

        assertScrim(menu(look), look)
    }

    @Test
    fun `the scrim is the same where the menu glass option is Solid`() {
        assertScrim(menu(videoGlass = false))
    }

    @Test
    fun `the scrim is the same with high contrast, in the page colour of that look`() {
        val look = BorealisLook(highContrast = true)

        assertScrim(menu(look), look)
    }

    @Test
    fun `the panel is 85 percent of the surface over a white frame, whatever tint the viewer chose`() {
        val glass = menu(BorealisLook(glassTintPercent = 20))

        glass.openQuitPopup()

        assertNear(
            glass.popup().rightPixel(),
            surface.copy(alpha = POPUP_TINT).compositeOver(Color.White),
        )
    }

    @Test
    fun `over a white frame every text of the popup keeps 4_5 to 1 on its panel`() {
        val glass = menu()
        glass.openQuitPopup()
        val tokens = borealisColors(BorealisLook())
        val panel = wcagLuminance(glass.popup().rightPixel())

        for ((name, ink) in
            mapOf(
                "ink" to tokens.ink,
                "ink-2" to tokens.ink2,
                "accent-soft" to tokens.accentSoft,
                "danger-ink" to tokens.dangerInk,
            )) {
            assertWithMessage("$name on the panel over a white frame")
                .that(contrastOver(ink, panel))
                .isAtLeast(AA_RATIO)
        }
    }

    private fun assertNear(pixel: Color, expected: Color) {
        assertThat(pixel.red).isWithin(TOLERANCE).of(expected.red)
        assertThat(pixel.green).isWithin(TOLERANCE).of(expected.green)
        assertThat(pixel.blue).isWithin(TOLERANCE).of(expected.blue)
    }

    private companion object {
        const val SCRIM = 0.35f
        const val POPUP_TINT = 0.85f
        const val TOLERANCE = 0.012f
        const val MARGIN_PX = 10
    }
}
