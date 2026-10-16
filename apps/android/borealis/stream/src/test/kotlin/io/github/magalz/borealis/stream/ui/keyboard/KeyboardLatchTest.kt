package io.github.magalz.borealis.stream.ui.keyboard

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.keys.KeyChord
import io.github.magalz.borealis.stream.keys.KeyModifier.ALT
import io.github.magalz.borealis.stream.keys.KeyModifier.CTRL
import io.github.magalz.borealis.stream.keys.KeyModifier.SHIFT
import org.junit.Test

class KeyboardLatchTest {
    private val vkA = 0x41

    @Test
    fun `nothing is latched at first and a key is sent as it is`() {
        val latch = KeyboardLatch()

        assertThat(latch.latched).isEmpty()
        assertThat(latch.type(vkA).chord).isEqualTo(KeyChord(vkA))
    }

    @Test
    fun `a latched modifier goes with the next key and is then cleared`() {
        val typed = KeyboardLatch().toggle(CTRL).type(vkA)

        assertThat(typed.chord).isEqualTo(KeyChord(vkA, setOf(CTRL)))
        assertThat(typed.latch.latched).isEmpty()
    }

    @Test
    fun `two latches combine`() {
        val typed = KeyboardLatch().toggle(CTRL).toggle(ALT).type(vkA)

        assertThat(typed.chord).isEqualTo(KeyChord(vkA, setOf(CTRL, ALT)))
        assertThat(typed.latch.latched).isEmpty()
    }

    @Test
    fun `tapping a latched modifier again unlatches it`() {
        val latch = KeyboardLatch().toggle(SHIFT).toggle(CTRL).toggle(SHIFT)

        assertThat(latch.latched).containsExactly(CTRL)
        assertThat(latch.type(vkA).chord).isEqualTo(KeyChord(vkA, setOf(CTRL)))
    }

    @Test
    fun `toggling twice leaves nothing latched`() {
        assertThat(KeyboardLatch().toggle(ALT).toggle(ALT).latched).isEmpty()
    }

    @Test
    fun `a latch stays until a key is typed`() {
        val latch = KeyboardLatch().toggle(CTRL)

        assertThat(latch.latched).containsExactly(CTRL)
    }

    @Test
    fun `a shortcut chip clears the latches`() {
        val latch = KeyboardLatch().toggle(CTRL).toggle(SHIFT).cleared()

        assertThat(latch.latched).isEmpty()
        assertThat(latch.type(vkA).chord).isEqualTo(KeyChord(vkA))
    }
}
