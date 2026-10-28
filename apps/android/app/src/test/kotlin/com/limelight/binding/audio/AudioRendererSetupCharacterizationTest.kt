package com.limelight.binding.audio

import com.google.common.truth.Truth.assertThat
import java.util.logging.Level
import org.junit.Test

/**
 * Characterizes `AndroidAudioRenderer.setup`: the channel masks, the buffer sizes, the order of the
 * four attempts to create a track and when each is skipped, and what the call answers. The audio
 * platform is [ShadowRecordingAudioTrack], so a test reads the calls that the renderer made into
 * it.
 *
 * The expected calls are written out in full: the order matters. The size of a packet for stereo at
 * 240 samples per channel is 960 bytes, so the small buffer is 1920.
 */
class AudioRendererSetupCharacterizationTest : AudioRendererTestBase() {
    private fun assertMask(channels: Int, mask: Int, logged: String) {
        val result = renderer().setup(config(channels), SAMPLE_RATE, FRAME)

        assertThat(result).isEqualTo(0)
        assertThat(AudioPlatform.setups.map { it.channelMask }).containsExactly(mask)
        assertThat(log.entries).contains(Level.INFO to "Audio channel config: $logged")
    }

    @Test fun `2 channels ask for the stereo mask`() = assertMask(2, 0xC, "0xC")

    @Test fun `4 channels ask for the quad mask`() = assertMask(4, 0xCC, "0xCC")

    @Test fun `6 channels ask for the 5 point 1 mask`() = assertMask(6, 0xFC, "0xFC")

    @Test fun `8 channels ask for the 7 point 1 surround mask`() = assertMask(8, 0x18FC, "0x18FC")

    @Test
    fun `any other channel count fails with -1 and severe log before the platform is asked`() {
        val counts = listOf(0, 1, 3, 5, 7, 9, 10, -1)

        val results = counts.map { renderer().setup(config(it), SAMPLE_RATE, FRAME) }

        assertThat(results).containsExactlyElementsIn(List(counts.size) { -1 })
        assertThat(AudioPlatform.calls).isEmpty()
        assertThat(log.entries)
            .containsExactlyElementsIn(
                List(counts.size) { Level.SEVERE to "Decoder returned unhandled channel count" }
            )
    }

    @Test
    fun `the first attempt is a small low latency buffer of two packets and it is enough`() {
        val result = renderer().setUpStereo()

        assertThat(result).isEqualTo(0)
        assertThat(AudioPlatform.calls)
            .containsExactly(
                outputRate(),
                trackSetup(track = 1, bufferSize = 1_920, lowLatency = true),
                AudioCall.Start(1),
            )
            .inOrder()
        assertThat(log.entries)
            .containsExactly(
                Level.INFO to "Audio channel config: 0xC",
                Level.INFO to "Audio track configuration: 1920 true",
            )
            .inOrder()
    }

    @Test
    fun `a packet is the channels times the samples of a channel times two bytes`() {
        val renderer = renderer()

        renderer.setup(config(6), SAMPLE_RATE, 480)

        // 6 * 480 * 2 = 5760 bytes in a packet, so two packets are 11520
        assertThat(AudioPlatform.setups.map { it.bufferSize }).containsExactly(11_520)
    }

    @Test
    fun `the track is a game stream of 16 bit samples at the rate of the stream`() {
        AudioPlatform.nativeOutputRate = 44_100

        renderer().setup(config(2), 44_100, FRAME)

        val setup = AudioPlatform.setups.single()
        assertThat(setup.sampleRate).isEqualTo(44_100)
        assertThat(setup.encoding).isEqualTo(2)
        assertThat(setup.mode).isEqualTo(1)
        assertThat(setup.usage).isEqualTo(14)
        assertThat(setup.lowLatency).isTrue()
    }

    @Test
    fun `the attempts go small low latency, large low latency, small standard, large standard`() {
        AudioPlatform.minBufferSize = 5_000
        AudioPlatform.failSetupOf(AudioPlatform.everyTrack)

        val result = renderer().setUpStereo()

        assertThat(result).isEqualTo(-2)
        assertThat(AudioPlatform.calls)
            .containsExactly(
                outputRate(),
                trackSetup(track = 1, bufferSize = 1_920, lowLatency = true),
                minBuffer(),
                outputRate(),
                trackSetup(track = 2, bufferSize = 5_760, lowLatency = true),
                outputRate(),
                trackSetup(track = 3, bufferSize = 1_920, lowLatency = false),
                minBuffer(),
                outputRate(),
                trackSetup(track = 4, bufferSize = 5_760, lowLatency = false),
            )
            .inOrder()
    }

    @Test
    fun `the attempts stop at the first track that is created and played`() {
        AudioPlatform.minBufferSize = 5_000
        AudioPlatform.failSetupOf(setOf(1, 2))

        val result = renderer().setUpStereo()

        assertThat(result).isEqualTo(0)
        assertThat(AudioPlatform.setups.map { it.bufferSize to it.lowLatency })
            .containsExactly(1_920 to true, 5_760 to true, 1_920 to false)
            .inOrder()
        assertThat(AudioPlatform.calls.last()).isEqualTo(AudioCall.Start(3))
        assertThat(log.entries.last())
            .isEqualTo(Level.INFO to "Audio track configuration: 1920 false")
    }

    @Test
    fun `the large buffer is the minimum of the platform rounded up to a whole packet`() {
        // 960 bytes in a packet: 5000 needs six packets, 4800 is exactly five, 4801 needs six
        val sizes =
            listOf(5_000, 4_800, 4_801, 1_921, 1_920).map { minimum ->
                AudioPlatform.reset()
                AudioPlatform.minBufferSize = minimum
                AudioPlatform.failSetupOf(setOf(1))
                renderer().setUpStereo()
                AudioPlatform.setups[1].bufferSize
            }

        assertThat(sizes).containsExactly(5_760, 4_800, 5_760, 2_880, 1_920).inOrder()
    }

    @Test
    fun `the large buffer is at least two packets when the minimum of the platform is smaller`() {
        AudioPlatform.minBufferSize = 100
        AudioPlatform.failSetupOf(setOf(1))

        renderer().setUpStereo()

        assertThat(AudioPlatform.setups.map { it.bufferSize }).containsExactly(1_920, 1_920)
    }

    @Test
    fun `the large buffer is two packets when the platform fails the query`() {
        AudioPlatform.minBufferSize = -1
        AudioPlatform.failSetupOf(setOf(1))

        renderer().setUpStereo()

        assertThat(AudioPlatform.setups.map { it.bufferSize }).containsExactly(1_920, 1_920)
    }

    private fun assertLargeBuffer(channels: Int, minimum: Int, expected: Int) {
        AudioPlatform.minBufferSize = minimum
        AudioPlatform.failSetupOf(setOf(1))

        renderer().setup(config(channels), SAMPLE_RATE, FRAME)

        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.MinBuffer>())
            .containsExactly(minBuffer(channelCount = channels))
        assertThat(AudioPlatform.setups[1].bufferSize).isEqualTo(expected)
    }

    // 4 channels: a packet is 4 * 240 * 2 = 1920 bytes. 9000 needs five of them.
    @Test
    fun `the minimum buffer of 4 channels is rounded to a packet of 1920`() =
        assertLargeBuffer(channels = 4, minimum = 9_000, expected = 9_600)

    // 6 channels: a packet is 2880 bytes. 40000 needs fourteen of them.
    @Test
    fun `the minimum buffer of 6 channels is rounded to a packet of 2880`() =
        assertLargeBuffer(channels = 6, minimum = 40_000, expected = 40_320)

    // 8 channels: a packet is 3840 bytes. 50000 needs fourteen of them.
    @Test
    fun `the minimum buffer of 8 channels is rounded to a packet of 3840`() =
        assertLargeBuffer(channels = 8, minimum = 50_000, expected = 53_760)

    @Test
    fun `a hardware rate that is not the rate of the stream skips both low latency attempts`() {
        AudioPlatform.nativeOutputRate = 44_100

        val result = renderer().setUpStereo()

        assertThat(result).isEqualTo(0)
        assertThat(AudioPlatform.calls)
            .containsExactly(
                outputRate(),
                minBuffer(),
                outputRate(),
                outputRate(),
                trackSetup(track = 1, bufferSize = 1_920, lowLatency = false),
                AudioCall.Start(1),
            )
            .inOrder()
        assertThat(log.entries.last())
            .isEqualTo(Level.INFO to "Audio track configuration: 1920 false")
    }

    @Test
    fun `audio effects skip both low latency attempts when the rates match`() {
        val result = renderer(audioFx = true).setUpStereo()

        assertThat(result).isEqualTo(0)
        assertThat(AudioPlatform.calls)
            .containsExactly(
                outputRate(),
                minBuffer(),
                outputRate(),
                outputRate(),
                trackSetup(track = 1, bufferSize = 1_920, lowLatency = false),
                AudioCall.Start(1),
            )
            .inOrder()
    }

    @Test
    fun `audio effects with a rate mismatch also use the standard attempts only`() {
        AudioPlatform.nativeOutputRate = 96_000

        renderer(audioFx = true).setUpStereo()

        assertThat(AudioPlatform.setups.map { it.lowLatency }).containsExactly(false)
    }

    @Test
    fun `the standard attempts are tried in the order small then large when low latency is skipped`() {
        AudioPlatform.nativeOutputRate = 44_100
        AudioPlatform.minBufferSize = 5_000
        AudioPlatform.failSetupOf(setOf(1))

        val result = renderer().setUpStereo()

        assertThat(result).isEqualTo(0)
        assertThat(AudioPlatform.setups.map { it.bufferSize to it.lowLatency })
            .containsExactly(1_920 to false, 5_760 to false)
            .inOrder()
    }

    @Test
    fun `setup answers -2 when no attempt created a track`() {
        AudioPlatform.failSetupOf(AudioPlatform.everyTrack)

        assertThat(renderer().setUpStereo()).isEqualTo(-2)
        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.Start>()).isEmpty()
        assertThat(log.entries.map { it.second })
            .doesNotContain("Audio track configuration: 1920 true")
    }

    @Test
    fun `setup answers -2 when the standard attempts fail after the low latency ones were skipped`() {
        AudioPlatform.nativeOutputRate = 44_100
        AudioPlatform.failSetupOf(AudioPlatform.everyTrack)

        assertThat(renderer().setUpStereo()).isEqualTo(-2)
        assertThat(AudioPlatform.setups.map { it.lowLatency }).containsExactly(false, false)
    }

    @Test
    fun `a creation that fails is printed and the next attempt goes on`() {
        AudioPlatform.failSetupOf(setOf(1))

        val result = renderer().setUpStereo()

        assertThat(result).isEqualTo(0)
        assertThat(printed.text).contains("java.lang.UnsupportedOperationException")
        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.Release>()).isEmpty()
    }

    @Test
    fun `a track that cannot be played is released and the next attempt goes on`() {
        AudioPlatform.failStartOf(setOf(1))

        val result = renderer().setUpStereo()

        assertThat(result).isEqualTo(0)
        assertThat(printed.text).contains("java.lang.IllegalStateException: cannot start track 1")
        assertThat(AudioPlatform.calls.filter { it is AudioCall.Start || it is AudioCall.Release })
            .containsExactly(AudioCall.Start(1), AudioCall.Release(1), AudioCall.Start(2))
            .inOrder()
        assertThat(log.entries.last().second).startsWith("Audio track configuration:")
    }

    @Test
    fun `when no track can be played every track is released and setup answers -2`() {
        AudioPlatform.failStartOf(AudioPlatform.everyTrack)

        val result = renderer().setUpStereo()

        assertThat(result).isEqualTo(-2)
        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.Release>())
            .containsExactly(
                AudioCall.Release(1),
                AudioCall.Release(2),
                AudioCall.Release(3),
                AudioCall.Release(4),
            )
            .inOrder()
    }

    @Test
    fun `a track that cannot be released stays, and setup answers 0 without a track that played`() {
        AudioPlatform.failStartOf(AudioPlatform.everyTrack)
        AudioPlatform.failReleaseOf(AudioPlatform.everyTrack)

        val result = renderer().setUpStereo()

        // Every attempt created a track, none played and none could be released: the last one stays
        assertThat(result).isEqualTo(0)
        assertThat(AudioPlatform.setups).hasSize(4)
        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.Release>()).hasSize(4)
    }

    @Test
    fun `a creation that fails releases the track that an earlier attempt could not release`() {
        AudioPlatform.failStartOf(setOf(1))
        AudioPlatform.failReleaseOf(setOf(1))
        AudioPlatform.onSetup = { if (it == 2) -1 else 0 }

        val result = renderer().setUpStereo()

        assertThat(result).isEqualTo(0)
        assertThat(AudioPlatform.calls.filter { it is AudioCall.Release })
            .containsExactly(AudioCall.Release(1), AudioCall.Release(1))
            .inOrder()
    }

    @Test
    fun `a failure that is not an exception is not caught and releases nothing`() {
        AudioPlatform.onStart = { throw AssertionError("not an exception") }

        val failure = runCatching { renderer().setUpStereo() }.exceptionOrNull()

        assertThat(failure).isInstanceOf(AssertionError::class.java)
        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.Release>()).isEmpty()
    }

    @Test
    fun `a second setup creates another track and leaves the first one alone`() {
        val renderer = renderer()

        renderer.setUpStereo()
        renderer.setUpStereo()

        assertThat(AudioPlatform.setups.map { it.track }).containsExactly(1, 2).inOrder()
        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.Release>()).isEmpty()
    }

    @Test
    fun `a packet of no samples makes the second attempt divide by zero`() {
        // The first attempt asks for a buffer of 0 bytes and the platform refuses it. The second
        // rounds to a whole packet of 0 bytes.
        val failure = runCatching { renderer().setup(config(2), SAMPLE_RATE, 0) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ArithmeticException::class.java)
    }
}
