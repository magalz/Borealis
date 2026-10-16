package io.github.magalz.borealis.stream.keys

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.keys.KeyModifier.ALT
import io.github.magalz.borealis.stream.keys.KeyModifier.CTRL
import io.github.magalz.borealis.stream.keys.KeyModifier.SHIFT
import io.github.magalz.borealis.stream.keys.KeyModifier.WIN
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShortcutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun List<NamedChord>.labels() = map { context.getString(it.label) }

    private fun List<NamedChord>.chords() = map { it.chord }

    @Test
    fun `the Send keys row offers the nine chips of the menu`() {
        assertThat(Shortcuts.sendKeys.labels())
            .containsExactly(
                "Alt+Tab",
                "Win+Tab",
                "Win",
                "Ctrl+Shift+Esc",
                "Alt+F4",
                "Win+D",
                "Win+G",
                "Esc",
                "Win+Alt+B",
            )
            .inOrder()
    }

    @Test
    fun `the Send keys chords use Windows virtual-key codes`() {
        assertThat(Shortcuts.sendKeys.chords())
            .containsExactly(
                KeyChord(0x09, setOf(ALT)),
                KeyChord(0x09, setOf(WIN)),
                KeyChord(0x5B),
                KeyChord(0x1B, setOf(CTRL, SHIFT)),
                KeyChord(0x73, setOf(ALT)),
                KeyChord(0x44, setOf(WIN)),
                KeyChord(0x47, setOf(WIN)),
                KeyChord(0x1B),
                KeyChord(0x42, setOf(WIN, ALT)),
            )
            .inOrder()
    }

    @Test
    fun `the keyboard row offers the eight shortcuts`() {
        assertThat(Shortcuts.keyboardRow.labels())
            .containsExactly(
                "Alt+Tab",
                "Win+Tab",
                "Win",
                "Ctrl+Shift+Esc",
                "Alt+F4",
                "Win+D",
                "Esc",
                "F11",
            )
            .inOrder()
    }

    @Test
    fun `the keyboard row chords use Windows virtual-key codes`() {
        assertThat(Shortcuts.keyboardRow.chords())
            .containsExactly(
                KeyChord(0x09, setOf(ALT)),
                KeyChord(0x09, setOf(WIN)),
                KeyChord(0x5B),
                KeyChord(0x1B, setOf(CTRL, SHIFT)),
                KeyChord(0x73, setOf(ALT)),
                KeyChord(0x44, setOf(WIN)),
                KeyChord(0x1B),
                KeyChord(0x7A),
            )
            .inOrder()
    }

    @Test
    fun `the modifiers match the stream stack`() {
        // KeyboardTranslator's left Shift, Ctrl, Alt and Meta, and KeyboardPacket.MODIFIER_*.
        assertThat(KeyModifier.entries.map { Triple(it, it.vk, it.flag) })
            .containsExactly(
                Triple(SHIFT, 0xA0, 0x01),
                Triple(CTRL, 0xA2, 0x02),
                Triple(ALT, 0xA4, 0x04),
                Triple(WIN, 0x5B, 0x08),
            )
    }
}
