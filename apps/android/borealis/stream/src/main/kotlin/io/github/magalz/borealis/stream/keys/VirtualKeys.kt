package io.github.magalz.borealis.stream.keys

import androidx.annotation.StringRes

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
    /** The chips of the menu's Send keys row. */
    val sendKeys: List<NamedChord> = emptyList()

    /** The shortcut row of the in-stream keyboard. */
    val keyboardRow: List<NamedChord> = emptyList()
}
