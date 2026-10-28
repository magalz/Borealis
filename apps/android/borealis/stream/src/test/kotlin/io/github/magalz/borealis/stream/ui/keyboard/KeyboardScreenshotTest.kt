package io.github.magalz.borealis.stream.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.ui.StreamKeyboard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the keyboard over a stand-in for the stream picture, named
 * `keyboard_<device>_<state>.png` in `src/test/screenshots`. The TV ones are taken in keyboard
 * input mode, as a D-pad user sees them.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class KeyboardScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun keyboardPhoneDocked() {
        composeRule.setContent { Screen(docked = true, FormFactor.Phone) }

        shoot("keyboard_phone_docked")
    }

    @Test
    @Config(qualifiers = TV)
    fun keyboardTvFocused() {
        composeRule.setContentInKeyboardMode { Screen(docked = false, FormFactor.Tv) }

        shoot("keyboard_tv_focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun keyboardTvCtrlLatched() {
        composeRule.setContentInKeyboardMode { Screen(docked = false, FormFactor.Tv) }
        composeRule.onNodeWithTag("key:Ctrl").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        shoot("keyboard_tv_ctrl-latched")
    }

    @Test
    @Config(qualifiers = TV)
    fun keyboardTvFnLayer() {
        composeRule.setContentInKeyboardMode { Screen(docked = false, FormFactor.Tv) }
        composeRule.onNodeWithTag("key:Fn 123").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        shoot("keyboard_tv_fn-layer")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun keyboardPhoneShiftLatched() {
        composeRule.setContent { Screen(docked = true, FormFactor.Phone) }
        composeRule.onNodeWithTag("key:Shift").performClick()

        shoot("keyboard_phone_shift-latched")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun keyboardPhoneFnLayer() {
        composeRule.setContent { Screen(docked = true, FormFactor.Phone) }
        composeRule.onNodeWithTag("key:Fn 123").performClick()

        shoot("keyboard_phone_fn-layer")
    }

    @Composable
    private fun Screen(docked: Boolean, formFactor: FormFactor) {
        BorealisTheme(formFactor) {
            // A stand-in for the picture of the stream, so the keyboard sits over something.
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF2B3A55), Color(0xFF0E1013)))
                    )
            ) {
                StreamKeyboard(docked, RecordingIntents())
            }
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
