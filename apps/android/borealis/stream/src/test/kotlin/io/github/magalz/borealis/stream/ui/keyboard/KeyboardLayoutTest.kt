package io.github.magalz.borealis.stream.ui.keyboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.KeyStyle
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.keys.KeyChord
import io.github.magalz.borealis.stream.keys.KeyModifier
import io.github.magalz.borealis.stream.keys.Shortcuts
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.ui.StreamKeyboard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class KeyboardLayoutTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Whether the keyboard on show is docked, which a test can flip while it is composed. */
    private var docked by mutableStateOf(true)

    private fun show(formFactor: FormFactor) {
        composeRule.setContent {
            BorealisTheme(formFactor) { StreamKeyboard(docked, RecordingIntents()) }
        }
    }

    private fun dock(value: Boolean) {
        composeRule.runOnIdle { docked = value }
        composeRule.waitForIdle()
    }

    /** The captions of the shortcut chips on show, from the left. */
    private fun shortcutRow() =
        Shortcuts.keyboardRow
            .map { context.getString(it.label) }
            .sortedBy {
                composeRule.onNodeWithTag("chip:$it").fetchSemanticsNode().positionInRoot.x
            }

    private fun KeySpec.caption() = label.resolve(context)

    private fun List<List<KeySpec>>.rowCaptions() = map { row -> row.map { it.caption() } }

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
            put("⌫", 8)
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
        assertThat(KeyboardLayout.letterRows.rowCaptions())
            .containsExactly(
                listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
                listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "⌫"),
                listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", "Enter"),
            )
            .inOrder()
    }

    @Test
    fun `the bottom row is Ctrl, Win, Alt, Space, Tab and Fn 123`() {
        assertThat(KeyboardLayout.bottomRow.captions())
            .containsExactly("Ctrl", "Win", "Alt", "Space", "Tab", "Fn 123")
            .inOrder()
    }

    @Test
    fun `the Fn layer has the digits, F1 to F10, and the arrows with the navigation keys`() {
        assertThat(KeyboardLayout.functionRows.rowCaptions())
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
        assertThat(styles["⌫"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Enter"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Shift"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Ctrl"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Tab"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Fn 123"]).isEqualTo(KeyStyle.Wide)
        assertThat(styles["Space"]).isEqualTo(KeyStyle.Space)
        assertThat(styles["PgDn"]).isEqualTo(KeyStyle.Wide)
    }

    @Test
    fun `the modifier keys send the left-hand virtual-key codes of KeyboardTranslator`() {
        val modifiers =
            (KeyboardLayout.letterRows.flatten() + KeyboardLayout.bottomRow)
                .filterIsInstance<ModifierKey>()
                .associate { it.caption() to it.modifier.vk }

        assertThat(modifiers).containsExactly("Shift", 0xA0, "Ctrl", 0xA2, "Alt", 0xA4, "Win", 0x5B)
    }

    @Test
    fun `the Esc chip of the shortcut row sends the Escape code`() {
        val esc = Shortcuts.keyboardRow.single { context.getString(it.label) == "Esc" }

        assertThat(esc.chord).isEqualTo(KeyChord(27))
    }

    // The row follows the window, not the docking: a pad makes a keyboard cover the stream, and a
    // phone in split screen with a pad is as narrow as one without.
    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone-width window the shortcut row starts with Esc, docked or covering`() {
        val phoneRow = Shortcuts.keyboardRowPhone.map { context.getString(it.label) }
        show(FormFactor.Phone)

        assertThat(shortcutRow()).containsExactlyElementsIn(phoneRow).inOrder()
        dock(false)
        assertThat(shortcutRow()).containsExactlyElementsIn(phoneRow).inOrder()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a wide window the shortcut row keeps the order of the TV, docked or covering`() {
        val tvRow = Shortcuts.keyboardRow.map { context.getString(it.label) }
        show(FormFactor.Handheld)

        assertThat(shortcutRow()).containsExactlyElementsIn(tvRow).inOrder()
        dock(false)
        assertThat(shortcutRow()).containsExactlyElementsIn(tvRow).inOrder()
    }
}
