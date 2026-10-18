package io.github.magalz.borealis.stream.ui.keyboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.setContentInKeyboardMode
import io.github.magalz.borealis.stream.ui.StreamKeyboard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Where the D-pad takes the focus on a TV, key by key, through both layers. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class KeyboardNavigationTest {
    @get:Rule val composeRule = createComposeRule()
    private val intents = RecordingIntents()

    private fun show() {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(FormFactor.Tv) { StreamKeyboard(docked = false, intents) }
        }
    }

    private fun press(key: Key) = composeRule.onRoot().performKeyInput { pressKey(key) }

    /** The tag of the key that has the focus, without its `key:` or `chip:` prefix. */
    private fun focused(): String =
        composeRule
            .onNode(isFocused())
            .fetchSemanticsNode()
            .config[SemanticsProperties.TestTag]
            .substringAfter(':')

    @Test
    fun `the D-pad walks down the letter layer to the bottom row and back up`() {
        show()
        assertThat(focused()).isEqualTo("Q")

        val down = mutableListOf<String>()
        repeat(3) {
            press(Key.DirectionDown)
            down += focused()
        }
        val up = mutableListOf<String>()
        repeat(3) {
            press(Key.DirectionUp)
            up += focused()
        }

        assertThat(down).containsExactly("A", "Shift", "Win").inOrder()
        assertThat(up).containsExactly("Shift", "A", "Q").inOrder()
    }

    @Test
    fun `the D-pad stops at the bottom row`() {
        show()

        repeat(4) { press(Key.DirectionDown) }

        assertThat(focused()).isEqualTo("Win")
    }

    @Test
    fun `the D-pad crosses the bottom row`() {
        show()
        composeRule.onNodeWithTag("key:Ctrl").requestFocus()

        val visited = mutableListOf(focused())
        repeat(5) {
            press(Key.DirectionRight)
            visited += focused()
        }

        assertThat(visited)
            .containsExactly("Ctrl", "Win", "Alt", "Space", "Tab", "Fn 123")
            .inOrder()
    }

    @Test
    fun `the D-pad walks up the Fn layer from Fn 123 to the digits and back down`() {
        show()
        composeRule.onNodeWithTag("key:Fn 123").requestFocus()
        press(Key.DirectionCenter)
        assertThat(focused()).isEqualTo("Fn 123")
        composeRule.onNodeWithTag("key:F1").assertExists()

        val up = mutableListOf<String>()
        repeat(3) {
            press(Key.DirectionUp)
            up += focused()
        }
        val down = mutableListOf<String>()
        repeat(3) {
            press(Key.DirectionDown)
            down += focused()
        }

        assertThat(up).containsExactly("Del", "F10", "0").inOrder()
        assertThat(down).containsExactly("F10", "Del", "Fn 123").inOrder()
    }

    @Test
    fun `the D-pad moves along the arrows of the Fn layer`() {
        show()
        composeRule.onNodeWithTag("key:Fn 123").requestFocus()
        press(Key.DirectionCenter)
        composeRule.onNodeWithTag("key:←").requestFocus()

        val visited = mutableListOf(focused())
        repeat(8) {
            press(Key.DirectionRight)
            visited += focused()
        }

        assertThat(visited)
            .containsExactly("←", "↑", "↓", "→", "Home", "End", "PgUp", "PgDn", "Del")
            .inOrder()
    }
}
