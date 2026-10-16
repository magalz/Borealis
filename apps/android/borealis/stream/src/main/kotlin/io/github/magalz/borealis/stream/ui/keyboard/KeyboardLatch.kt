package io.github.magalz.borealis.stream.ui.keyboard

import io.github.magalz.borealis.stream.keys.KeyChord
import io.github.magalz.borealis.stream.keys.KeyModifier

/**
 * The modifiers of the on-screen keyboard that wait for the next key. Tapping a modifier toggles
 * it; typing a key sends it with every latched modifier and clears them. Immutable, so the keyboard
 * can hold it in Compose state.
 */
internal data class KeyboardLatch(val latched: Set<KeyModifier> = emptySet()) {
    fun toggle(modifier: KeyModifier): KeyboardLatch =
        KeyboardLatch(if (modifier in latched) latched - modifier else latched + modifier)

    /** The chord to send for [vk] with the latched modifiers, and the latch that is left. */
    fun type(vk: Int): Typed = Typed(KeyChord(vk, latched), cleared())

    /** No modifier latched: what a shortcut chip leaves, since it sends its own chord. */
    fun cleared(): KeyboardLatch = KeyboardLatch()

    internal data class Typed(val chord: KeyChord, val latch: KeyboardLatch)
}
