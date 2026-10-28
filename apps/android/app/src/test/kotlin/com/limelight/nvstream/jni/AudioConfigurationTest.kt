package com.limelight.nvstream.jni

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge.AudioConfiguration
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * `MoonBridge.AudioConfiguration`: a channel count and a channel mask that travel to the native
 * library as one packed int (`MAKE_AUDIO_CONFIGURATION()` of Limelight.h) and come back from it the
 * same way. Plain JUnit: the nested class does not initialize `MoonBridge`.
 */
class AudioConfigurationTest {
    /**
     * `new AudioConfiguration(int)`, the constructor that decodes the packed int; `MoonBridge`
     * calls it.
     */
    private fun unpack(packed: Int): AudioConfiguration {
        val constructor =
            AudioConfiguration::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        try {
            return constructor.newInstance(packed)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    @Test
    fun `the fields hold what the constructor was given`() {
        val configuration = AudioConfiguration(6, 0x3F)

        assertThat(configuration.channelCount).isEqualTo(6)
        assertThat(configuration.channelMask).isEqualTo(0x3F)
    }

    @Test
    fun `toInt packs the mask, the count and the magic byte`() {
        assertThat(AudioConfiguration(2, 0x3).toInt()).isEqualTo(0x000302CA)
        assertThat(AudioConfiguration(6, 0x3F).toInt()).isEqualTo(0x003F06CA)
        assertThat(AudioConfiguration(8, 0x63F).toInt()).isEqualTo(0x063F08CA)
    }

    @Test
    fun `a mask with the top bit set packs into a negative int`() {
        assertThat(AudioConfiguration(8, 0xFFFF).toInt()).isEqualTo(0xFFFF08CA.toInt())
    }

    @Test
    fun `an int that toInt made unpacks to the same count and mask`() {
        for ((count, mask) in listOf(2 to 0x3, 6 to 0x3F, 8 to 0x63F, 8 to 0xFFFF, 255 to 0x8001)) {
            val unpacked = unpack(AudioConfiguration(count, mask).toInt())

            assertThat(unpacked.channelCount).isEqualTo(count)
            assertThat(unpacked.channelMask).isEqualTo(mask)
        }
    }

    @Test
    fun `unpacking reads the count from bits 8 to 15 and the mask from bits 16 to 31`() {
        val unpacked = unpack(0xFFFF7ECA.toInt())

        assertThat(unpacked.channelCount).isEqualTo(0x7E)
        assertThat(unpacked.channelMask).isEqualTo(0xFFFF)
    }

    @Test
    fun `an int without the magic byte is refused`() {
        val refused = assertThrows(IllegalArgumentException::class.java) { unpack(0x000302C9) }

        assertThat(refused)
            .hasMessageThat()
            .isEqualTo("Audio configuration has invalid magic byte!")
    }

    @Test
    fun `an old hardcoded configuration value is refused`() {
        // AUDIO_CONFIGURATION_STEREO of the moonlight-common-c versions before
        // MAKE_AUDIO_CONFIGURATION
        assertThrows(IllegalArgumentException::class.java) { unpack(0) }
        assertThrows(IllegalArgumentException::class.java) { unpack(1) }
    }

    @Test
    fun `the surround audio info is the mask in the high half and the count in the low half`() {
        assertThat(AudioConfiguration(2, 0x3).surroundAudioInfo).isEqualTo(0x00030002)
        assertThat(AudioConfiguration(6, 0x3F).surroundAudioInfo).isEqualTo(0x003F0006)
        assertThat(AudioConfiguration(8, 0x63F).surroundAudioInfo).isEqualTo(0x063F0008)
        assertThat(AudioConfiguration(8, 0xFFFF).surroundAudioInfo).isEqualTo(0xFFFF0008.toInt())
    }

    @Test
    fun `configurations with the same count and mask are equal and hash alike`() {
        val first = AudioConfiguration(6, 0x3F)
        val second = AudioConfiguration(6, 0x3F)

        assertThat(first).isEqualTo(second)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
    }

    @Test
    fun `the hash code is the packed int`() {
        assertThat(AudioConfiguration(6, 0x3F).hashCode()).isEqualTo(0x003F06CA)
    }

    @Test
    fun `configurations that differ in count or mask are not equal`() {
        assertThat(AudioConfiguration(6, 0x3F)).isNotEqualTo(AudioConfiguration(8, 0x3F))
        assertThat(AudioConfiguration(6, 0x3F)).isNotEqualTo(AudioConfiguration(6, 0x63F))
    }

    @Test
    fun `equality is decided by the packed int, not by the two fields`() {
        // A count of 0x100 and a mask of 1 pack to the same int: 0x000100CA
        val wide = AudioConfiguration(0x100, 0)
        val masked = AudioConfiguration(0, 1)

        assertThat(wide).isEqualTo(masked)
        assertThat(wide.hashCode()).isEqualTo(masked.hashCode())
    }

    // The test is about equals(null) itself; a Truth assertion would not call it
    @Suppress("EqualsNullCall")
    @Test
    fun `a configuration is not equal to null or to another type`() {
        val configuration = AudioConfiguration(2, 0x3)

        assertThat(configuration.equals(null)).isFalse()
        assertThat(configuration.equals(configuration.toInt())).isFalse()
        assertThat(configuration.equals("2")).isFalse()
    }
}
