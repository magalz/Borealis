package io.github.magalz.borealis.stream.ui.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.KeyStyle
import io.github.magalz.borealis.stream.keys.KeyModifier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardLayoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun KeySpec.caption() = label.resolve(context)

    private fun List<List<KeySpec>>.captions() = map { row -> row.map { it.caption() } }

    private fun List<KeySpec>.captions() = map { it.caption() }

    /** The Windows virtual-key codes `KeyboardTranslator` sends for the key, written out. */
    private val vkOf =
        buildMap<String, Int> {
            for ((index, letter) in ('A'..'Z').withIndex()) put(letter.toString(), 65 + index)
            for ((index, digit) in "1234567890".withIndex()) put(
                digit.toString(),
                48 + (index + 1) % 10,
            )
            for (number in 1..10) put("F$number", 112 + number - 1)
            put("Backspace", 8)
            put("Enter", 0x0D)
            put("Tab", 9)
            put("Space", 32)
            put("Del", 0x2E)
            put("Home", 36)
            put("End", 35)
            put("PgUp", 33)
            put("PgDn", 34)
            put("←", 37)
            put("↑", 38)
            put("→", 39)
            put("↓", 40)
        }

    private val everyKey
        get() =
            (KeyboardLayout.letterRows +
                    KeyboardLayout.functionRows +
                    listOf(KeyboardLayout.bottomRow))
                .flatten()

    @Test
    fun `the letter rows are Q to P, A to L with Backspace, and Shift, Z to M with Enter`() {
        assertThat(KeyboardLayout.letterRows.captions())
            .containsExactly(
                listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
                listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Backspace"),
                listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", "Enter"),
            )
            .inOrder()
    }

    @Test
    fun `the bottom row is Ctrl, Win, Alt, Space, Tab and Fn`() {
        assertThat(KeyboardLayout.bottomRow.captions())
            .containsExactly("Ctrl", "Win", "Alt", "Space", "Tab", "Fn")
            .inOrder()
    }

    @Test
    fun `the Fn layer has the digits, F1 to F10, and the arrows with the navigation keys`() {
        assertThat(KeyboardLayout.functionRows.captions())
            .containsExactly(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10"),
                listOf("←", "↑", "↓", "→", "Home", "End", "PgUp", "PgDn", "Del"),
            )
            .inOrder()
    }

    @Test
    fun `every key sends the virtual-key code KeyboardTranslator uses`() {
        val sent = everyKey.filterIsInstance<CharKey>()

        assertThat(sent).isNotEmpty()
        for (key in sent) {
            assertThat(key.vk).isEqualTo(vkOf[key.caption()])
        }
        assertThat(sent.map { it.caption() }).containsAtLeastElementsIn(vkOf.keys)
    }

    @Test
    fun `the modifiers are the four that latch and the Fn key is the layer switch`() {
        val modifiers = KeyboardLayout.letterRows.flatten() + KeyboardLayout.bottomRow

        assertThat(modifiers.filterIsInstance<ModifierKey>().map { it.modifier })
            .containsExactly(KeyModifier.SHIFT, KeyModifier.CTRL, KeyModifier.WIN, KeyModifier.ALT)
        assertThat(KeyboardLayout.bottomRow.last()).isEqualTo(FnKey)
    }

    @Test
    fun `wide and space keys have their style and letters are normal`() {
        val styles = everyKey.associate { it.caption() to it.style }

        assertThat(styles["Q"]).isEqualTo(KeyStyle.Normal)
        assertThat(styles["Backspace"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Enter"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Shift"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Ctrl"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Tab"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Fn"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Space"]).isEqualTo(KeyStyle.Space)
        assertThat(styles["PgDn"]).isEqualTo(KeyStyle.Wide)
    }
}
