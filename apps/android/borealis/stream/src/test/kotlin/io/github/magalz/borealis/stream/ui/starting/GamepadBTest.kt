package io.github.magalz.borealis.stream.ui.starting

import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The gamepad B rule of the stream screens: the first key-down acts once, and the key-down and its
 * key-up stay in the screen, so neither reaches the next screen or the PC.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class GamepadBTest {
    @get:Rule val composeRule = createComposeRule()

    private val focus = FocusRequester()
    private val reached = mutableListOf<String>()
    private var fired = 0
    private var listening by mutableStateOf(true)
    private lateinit var view: View

    @Before
    fun show() {
        composeRule.setContent {
            view = LocalView.current
            Box(if (listening) Modifier.onGamepadB { fired++ } else Modifier) {
                Box(
                    Modifier.size(10.dp).focusRequester(focus).focusable().onKeyEvent {
                        reached += it.type.toString()
                        false
                    }
                )
            }
        }
        composeRule.runOnIdle { focus.requestFocus() }
    }

    @Test
    fun `a key-down fires once`() {
        send(KeyEvent.ACTION_DOWN)

        assertThat(fired).isEqualTo(1)
    }

    @Test
    fun `holding the button does not fire again`() {
        send(KeyEvent.ACTION_DOWN)
        send(KeyEvent.ACTION_DOWN, repeat = 1)
        send(KeyEvent.ACTION_DOWN, repeat = 2)

        assertThat(fired).isEqualTo(1)
        assertThat(reached).isEmpty()
    }

    @Test
    fun `the key-down is consumed`() {
        send(KeyEvent.ACTION_DOWN)

        assertThat(reached).isEmpty()
    }

    @Test
    fun `the key-up that follows is consumed`() {
        send(KeyEvent.ACTION_DOWN)
        send(KeyEvent.ACTION_UP)

        assertThat(reached).isEmpty()
        assertThat(fired).isEqualTo(1)
    }

    @Test
    fun `a key-up without a key-down the screen saw does nothing and is not consumed`() {
        // B went down before this screen was there: the window saw the key-down, the screen did
        // not.
        listening = false
        composeRule.waitForIdle()
        send(KeyEvent.ACTION_DOWN)
        listening = true
        composeRule.waitForIdle()

        send(KeyEvent.ACTION_UP)

        assertThat(fired).isEqualTo(0)
        assertThat(reached).containsExactly("KeyDown", "KeyUp").inOrder()
    }

    @Test
    fun `another button is left alone`() {
        send(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_BUTTON_A)

        assertThat(fired).isEqualTo(0)
        assertThat(reached).containsExactly("KeyDown")
    }

    private fun send(action: Int, repeat: Int = 0, code: Int = KeyEvent.KEYCODE_BUTTON_B) {
        composeRule.runOnUiThread { view.dispatchKeyEvent(KeyEvent(0L, 0L, action, code, repeat)) }
        composeRule.waitForIdle()
    }
}
