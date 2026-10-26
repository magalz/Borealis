package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.PointerSpeed
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.aSessionInfo
import io.github.magalz.borealis.stream.testing.setChromeInKeyboardMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the streaming layer, named `<screen>_<device>_<state>`. The stream is a gradient,
 * so the scrim of the panel and the translucent overlay can be seen over a picture.
 */
@RunWith(AndroidJUnit4::class)
class StreamingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val info = aSessionInfo.copy(hdr = true)

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun menuPhone() {
        composeRule.setContent {
            Stream(FormFactor.Phone) { Layer(aStreaming(Panel.MENU, info = info)) }
        }

        shoot("menu_phone")
    }

    @Test
    @Config(qualifiers = TV)
    fun menuTvFocused() {
        composeRule.setChromeInKeyboardMode(FormFactor.Tv) {
            Stream { Layer(aStreaming(Panel.MENU, touchMode = null, gamepadMouse = false)) }
        }

        shoot("menu_tv_focused")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun menuHandheld() {
        composeRule.setChromeInKeyboardMode(FormFactor.Handheld) {
            Stream { Layer(aStreaming(Panel.MENU, OverlayMode.COMPACT, null, true, info)) }
        }

        shoot("menu_handheld")
    }

    @Test
    @Config(qualifiers = TV)
    fun menuTvGamepadMouseOn() {
        composeRule.setChromeInKeyboardMode(FormFactor.Tv) {
            Stream {
                Layer(
                    aStreaming(Panel.MENU, touchMode = null, gamepadMouse = true)
                        .copy(pointerSpeed = PointerSpeed.FAST)
                )
            }
        }

        shoot("menu_tv_gamepad-mouse-on")
    }

    @Test
    @Config(qualifiers = TV)
    fun menuTvSendKeysExpanded() {
        composeRule.setChromeInKeyboardMode(FormFactor.Tv) {
            Stream { Layer(aStreaming(Panel.MENU, touchMode = null, gamepadMouse = false)) }
        }
        composeRule.onNodeWithText("Send keys").performClick()

        shoot("menu_tv_sendkeys-expanded")
    }

    @Test
    @Config(qualifiers = TV)
    fun menuTvDisconnectQuitFocused() {
        composeRule.setChromeInKeyboardMode(FormFactor.Tv) {
            Stream { Layer(aStreaming(Panel.MENU, touchMode = null, gamepadMouse = false)) }
        }
        composeRule.onNodeWithText("Quit Desktop").performScrollTo().requestFocus()
        // Scroll to the very end, so the padding under the last row and its outline show.
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }

        shoot("menu_tv_disconnect-quit_focused")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun menuPhoneQuitConfirm() {
        composeRule.setContent { Stream(FormFactor.Phone) { Layer(aStreaming(Panel.MENU)) } }
        composeRule.onNodeWithText("Quit Desktop").performScrollTo().performClick()

        shoot("menu_phone_quit-confirm")
    }

    /** The same confirmation for a game: it closes the game and loses its unsaved progress. */
    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun menuPhoneQuitConfirmGame() {
        composeRule.setContent {
            Stream(FormFactor.Phone) { Layer(aStreaming(Panel.MENU), appName = "Hades II") }
        }
        composeRule.onNodeWithText("Quit Hades II").performScrollTo().performClick()

        shoot("menu_phone_quit-confirm-game")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun overlayPhoneCompact() {
        composeRule.setContent {
            Stream(FormFactor.Phone) {
                Layer(aStreaming(overlay = OverlayMode.COMPACT, info = info))
            }
        }

        shoot("overlay_phone_compact")
    }

    @Test
    @Config(qualifiers = TV)
    fun overlayTvDetailed() {
        composeRule.setContent {
            Stream(FormFactor.Tv) {
                Layer(aStreaming(overlay = OverlayMode.DETAILED, touchMode = null, info = info))
            }
        }

        shoot("overlay_tv_detailed")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun streamPhoneHandleHint() {
        composeRule.setContent {
            Stream(FormFactor.Phone) { Layer(aStreaming().copy(showMenuHint = true)) }
        }

        shoot("stream_phone_handle-hint")
    }

    @Composable
    private fun Layer(phase: StreamPhase.Streaming, appName: String = "Desktop") =
        StreamingLayer(appName, "Gaming PC", phase, aPerf(), RecordingIntents())

    /** The picture of the stream is a gradient; [content] is the chrome over it. */
    @Composable
    private fun Stream(formFactor: FormFactor, content: @Composable () -> Unit) {
        BorealisTheme(formFactor) { Stream(content) }
    }

    @Composable
    private fun Stream(content: @Composable () -> Unit) {
        val colors = LocalBorealisColors.current
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.accentStrong, colors.page)))
        ) {
            content()
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
