package io.github.magalz.borealis.stream.host

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeldKeysTest {
    private val held = HeldKeys()

    private fun releaseAll(): List<Int> = buildList { held.releaseAll { add(it) } }

    @Test
    fun `releases the keys that are down in reverse order`() {
        held.down(0xA2)
        held.down(0xA0)
        held.down(0x1B)

        assertThat(releaseAll()).containsExactly(0x1B, 0xA0, 0xA2).inOrder()
    }

    @Test
    fun `a key that went up is not released`() {
        held.down(0xA2)
        held.down(0x1B)
        held.up(0x1B)

        assertThat(releaseAll()).containsExactly(0xA2)
    }

    @Test
    fun `a key pressed twice is released once, at its last press`() {
        held.down(0x41)
        held.down(0x42)
        held.down(0x41)

        assertThat(releaseAll()).containsExactly(0x41, 0x42).inOrder()
    }

    @Test
    fun `releasing clears the keys`() {
        held.down(0x41)
        releaseAll()

        assertThat(releaseAll()).isEmpty()
    }

    @Test
    fun `nothing is released when no key is down`() {
        assertThat(releaseAll()).isEmpty()
    }
}
