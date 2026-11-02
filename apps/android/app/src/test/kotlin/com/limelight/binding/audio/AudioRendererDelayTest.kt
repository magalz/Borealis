package com.limelight.binding.audio

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test

/**
 * Characterizes the audio delay that the overlay shows: the time between handing frames to the
 * track and the track presenting them, from the frames written and the timestamp of the track
 * (`AudioTrack.getTimestamp`, scripted through [AudioPlatform.timestamp]). The clock is given to
 * the function, so that the figures are exact.
 */
class AudioRendererDelayTest : AudioRendererTestBase() {
    private fun ready(channels: Int = 2): AndroidAudioRenderer =
        renderer().also { assertThat(it.setup(config(channels), SAMPLE_RATE, FRAME)).isEqualTo(0) }

    /** Hands [frames] frames of [channels] channels to the track, as the native layer does. */
    private fun AndroidAudioRenderer.write(frames: Int, channels: Int = 2) {
        ShadowRecordingBridge.answers["getPendingAudioDuration"] = listOf(0)
        playDecodedAudio(ShortArray(frames * channels))
    }

    private fun played(frames: Long, atNanos: Long = NOW) {
        AudioPlatform.timestamp = AudioPlatform.Timestamp(frames, atNanos)
    }

    @Test
    fun `there is no delay before the track was set up`() {
        assertThat(renderer().audioDelayMs(NOW)).isNull()
    }

    @Test
    fun `there is no delay until the track has a timestamp`() {
        val renderer = ready()
        renderer.write(240)

        assertThat(renderer.audioDelayMs(NOW)).isNull()
    }

    @Test
    fun `the delay is the time of the frames written that the track has not played yet`() {
        val renderer = ready()
        // 2,400 frames are 50 ms at 48 kHz; 1,200 of them are played, which leaves 25 ms.
        repeat(10) { renderer.write(240) }
        played(1_200)

        assertThat(renderer.audioDelayMs(NOW)).isEqualTo(25)
    }

    @Test
    fun `the time since the timestamp was taken has played on`() {
        val renderer = ready()
        repeat(10) { renderer.write(240) }
        played(1_200, atNanos = NOW - 10 * NANOS_PER_MS)

        assertThat(renderer.audioDelayMs(NOW)).isEqualTo(15)
    }

    @Test
    fun `a track that played everything it was given has no delay`() {
        val renderer = ready()
        repeat(4) { renderer.write(240) }
        played(960)

        assertThat(renderer.audioDelayMs(NOW)).isEqualTo(0)
    }

    @Test
    fun `a timestamp that is older than the delay does not give a negative one`() {
        val renderer = ready()
        repeat(4) { renderer.write(240) }
        played(960, atNanos = NOW - 50 * NANOS_PER_MS)

        assertThat(renderer.audioDelayMs(NOW)).isEqualTo(0)
    }

    @Test
    fun `the frames are counted for all the channels of a packet`() {
        val renderer = ready(channels = 6)
        // 240 frames of 6 channels are 1,440 samples and 5 ms.
        renderer.write(240, channels = 6)
        played(0)

        assertThat(renderer.audioDelayMs(NOW)).isEqualTo(5)
    }

    @Test
    fun `the frames of a packet that was dropped are not counted`() {
        val renderer = ready()
        ShadowRecordingBridge.answers["getPendingAudioDuration"] = listOf(40)
        renderer.playDecodedAudio(ShortArray(480))
        played(0)

        assertThat(renderer.audioDelayMs(NOW)).isEqualTo(0)
    }

    @Test
    fun `a new setup starts counting again`() {
        val renderer = ready()
        repeat(10) { renderer.write(240) }
        assertThat(renderer.setUpStereo()).isEqualTo(0)
        renderer.write(240)
        played(0)

        assertThat(renderer.audioDelayMs(NOW)).isEqualTo(5)
    }

    @Test
    fun `there is no delay once the track is gone`() {
        val renderer = ready()
        renderer.write(240)
        played(0)
        renderer.cleanup()

        assertThat(renderer.audioDelayMs(NOW)).isNull()
    }

    @Test
    fun `without a clock the delay reads the clock of the system`() {
        val renderer = ready()
        repeat(10) { renderer.write(240) }
        played(1_200, atNanos = System.nanoTime())

        // 25 ms less the time that the test takes between the timestamp and the question.
        val delay = renderer.audioDelayMs()
        assertThat(delay).isNotNull()
        assertThat(delay).isAtLeast(0)
        assertThat(delay).isAtMost(25)
    }

    private companion object {
        const val NOW = 5_000_000_000L
        const val NANOS_PER_MS = 1_000_000L
    }
}
