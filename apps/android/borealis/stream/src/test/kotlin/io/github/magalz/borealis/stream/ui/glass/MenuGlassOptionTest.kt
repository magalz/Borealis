package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.stream.MenuGlass
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.aDefaults
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import io.github.magalz.borealis.stream.ui.StreamChromeRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The menu glass option of Settings (ADR 0061, decision 1), read once when the stream starts and
 * kept in the visuals of the session: Blur draws the in-game menu over the live copy of the video,
 * and Solid draws the surface it had and asks for no copy. The chrome root is what passes it on.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class MenuGlassOptionTest {
    @get:Rule val composeRule = createComposeRule()

    private val surface = BorealisColors().surface
    private val source = FakeVideoSource(frameColor = AndroidColor.RED, completing = true)

    /** The chrome of a stream that started with [menuGlass], with its menu open, over [source]. */
    private fun show(menuGlass: MenuGlass): StreamSession {
        val session =
            aSession(defaults = aDefaults.copy(visuals = StreamVisuals(menuGlass = menuGlass)))
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        session.openMenu()
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoSource provides source) {
                StreamChromeRoot(session, {}, BorealisLook())
            }
        }
        composeRule.mainClock.advanceTimeBy(RUN_MS)
        composeRule.waitForIdle()
        return session
    }

    private fun panelRed() = composeRule.onNode(hasScrollAction()).leftPixel().red

    @Test
    fun `Blur draws the menu over the video and copies it`() {
        show(MenuGlass.BLUR)

        assertThat(source.requests).isNotEmpty()
        assertThat(panelRed()).isGreaterThan(surface.red + SHOWS_THROUGH)
    }

    @Test
    fun `Solid draws the surface and asks for no copy`() {
        show(MenuGlass.SOLID)

        assertThat(source.requests).isEmpty()
        assertThat(panelRed()).isWithin(TOLERANCE).of(surface.red)
    }

    private companion object {
        const val RUN_MS = 500L
        const val SHOWS_THROUGH = 0.05f
        const val TOLERANCE = 0.012f
    }
}
