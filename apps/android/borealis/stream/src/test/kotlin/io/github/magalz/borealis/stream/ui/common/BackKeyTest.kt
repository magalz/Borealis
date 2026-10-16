package io.github.magalz.borealis.stream.ui.common

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
import io.github.magalz.borealis.stream.testing.PHONE
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Back rule of the stream panels: the key-down stays in the panel, so Compose does not take it
 * for a focus exit, and the panel acts when the key comes up, as the system does for Back.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class BackKeyTest {
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
            Box(if (listening) Modifier.onBackKey { fired++ } else Modifier) {
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
    fun `the key-down is consumed and does nothing yet`() {
        send(KeyEvent.ACTION_DOWN)

        assertThat(fired).isEqualTo(0)
        assertThat(reached).isEmpty()
    }

    @Test
    fun `the key-up acts once and is consumed`() {
        send(KeyEvent.ACTION_DOWN)
        send(KeyEvent.ACTION_UP)

        assertThat(fired).isEqualTo(1)
        assertThat(reached).isEmpty()
    }

    @Test
    fun `holding Back acts once, on the key-up`() {
        send(KeyEvent.ACTION_DOWN)
        send(KeyEvent.ACTION_DOWN, repeat = 1)
        send(KeyEvent.ACTION_DOWN, repeat = 2)
        send(KeyEvent.ACTION_UP)

        assertThat(fired).isEqualTo(1)
        assertThat(reached).isEmpty()
    }

    @Test
    fun `a key-up without a key-down the panel saw does nothing and is not consumed`() {
        // Back opened the panel: the window saw the key-down, the panel was not there yet.
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
    fun `another key is left alone`() {
        send(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_BUTTON_B)
        send(KeyEvent.ACTION_UP, code = KeyEvent.KEYCODE_BUTTON_B)

        assertThat(fired).isEqualTo(0)
        assertThat(reached).containsExactly("KeyDown", "KeyUp").inOrder()
    }

    private fun send(action: Int, repeat: Int = 0, code: Int = KeyEvent.KEYCODE_BACK) {
        composeRule.runOnUiThread { view.dispatchKeyEvent(KeyEvent(0L, 0L, action, code, repeat)) }
        composeRule.waitForIdle()
    }
}
