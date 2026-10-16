package io.github.magalz.borealis.stream.keys

import androidx.annotation.StringRes
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.keys.KeyModifier.ALT
import io.github.magalz.borealis.stream.keys.KeyModifier.CTRL
import io.github.magalz.borealis.stream.keys.KeyModifier.SHIFT
import io.github.magalz.borealis.stream.keys.KeyModifier.WIN

/**
 * A modifier key: [vk] is its Windows virtual-key code, as `KeyboardTranslator` sends it, and
 * [flag] is its bit in `KeyboardPacket.MODIFIER_*`.
 */
enum class KeyModifier(val vk: Int, val flag: Int) {
    SHIFT(0xA0, 0x01),
    CTRL(0xA2, 0x02),
    ALT(0xA4, 0x04),
    WIN(0x5B, 0x08),
}

/** A key with the modifiers held while it is pressed. [vk] is a Windows virtual-key code. */
data class KeyChord(val vk: Int, val modifiers: Set<KeyModifier> = emptySet())

/** A [chord] with the [label] a button shows for it. */
data class NamedChord(@StringRes val label: Int, val chord: KeyChord)

/** The chords the menu and the keyboard offer. */
object Shortcuts {
    private const val VK_TAB = 0x09
    private const val VK_ESCAPE = 0x1B
    private const val VK_B = 0x42
    private const val VK_D = 0x44
    private const val VK_G = 0x47
    private const val VK_LWIN = 0x5B
    private const val VK_F4 = 0x73
    private const val VK_F11 = 0x7A

    private val altTab = NamedChord(R.string.key_alt_tab, KeyChord(VK_TAB, setOf(ALT)))
    private val winTab = NamedChord(R.string.key_win_tab, KeyChord(VK_TAB, setOf(WIN)))
    private val win = NamedChord(R.string.key_win, KeyChord(VK_LWIN))
    private val ctrlShiftEsc =
        NamedChord(R.string.key_ctrl_shift_esc, KeyChord(VK_ESCAPE, setOf(CTRL, SHIFT)))
    private val altF4 = NamedChord(R.string.key_alt_f4, KeyChord(VK_F4, setOf(ALT)))
    private val winD = NamedChord(R.string.key_win_d, KeyChord(VK_D, setOf(WIN)))
    private val winG = NamedChord(R.string.key_win_g, KeyChord(VK_G, setOf(WIN)))
    private val esc = NamedChord(R.string.key_esc, KeyChord(VK_ESCAPE))
    private val winAltB = NamedChord(R.string.key_win_alt_b, KeyChord(VK_B, setOf(WIN, ALT)))
    private val f11 = NamedChord(R.string.key_f11, KeyChord(VK_F11))

    /** The chips of the menu's Send keys row. */
    val sendKeys: List<NamedChord> =
        listOf(altTab, winTab, win, ctrlShiftEsc, altF4, winD, winG, esc, winAltB)

    /** The shortcut row of the in-stream keyboard. */
    val keyboardRow: List<NamedChord> =
        listOf(altTab, winTab, win, ctrlShiftEsc, altF4, winD, esc, f11)

    /**
     * The shortcut row of the docked keyboard (a phone): Esc first, so the scroll never hides it.
     */
    val keyboardRowPhone: List<NamedChord> =
        listOf(esc, altTab, winTab, win, ctrlShiftEsc, altF4, winD, f11)
}
