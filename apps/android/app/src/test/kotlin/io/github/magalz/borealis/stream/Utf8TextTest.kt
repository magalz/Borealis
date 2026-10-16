package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Utf8TextTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `accented letters take two bytes each`() {
        assertThat(Utf8Text.bytes("ção")).isEqualTo(bytes(0x63, 0xC3, 0xA7, 0xC3, 0xA3, 0x6F))
    }

    @Test
    fun `an emoji is one four-byte sequence, not two surrogates`() {
        assertThat(Utf8Text.bytes("🎮")).isEqualTo(bytes(0xF0, 0x9F, 0x8E, 0xAE))
    }

    @Test
    fun `mixed text keeps every character`() {
        assertThat(Utf8Text.bytes("ção 🎮"))
            .isEqualTo(bytes(0x63, 0xC3, 0xA7, 0xC3, 0xA3, 0x6F, 0x20, 0xF0, 0x9F, 0x8E, 0xAE))
    }

    @Test
    fun `empty text has no bytes`() {
        assertThat(Utf8Text.bytes("")).isEmpty()
    }
}
