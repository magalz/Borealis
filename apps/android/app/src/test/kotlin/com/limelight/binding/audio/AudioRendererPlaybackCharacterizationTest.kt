package com.limelight.binding.audio

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import java.util.logging.Level
import org.junit.Assert.assertThrows
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * Characterizes what `AndroidAudioRenderer` does once its track exists: it writes the samples that
 * the native layer decodes unless too much audio is waiting, it opens and closes the audio effect
 * session of the track, and it drops the track. The native layer answers how much audio is waiting
 * through [ShadowAudioMoonBridge].
 *
 * `start`, `stop` and `cleanup` use the track without checking that `setup` made one: a call before
 * a successful `setup` fails with a [NullPointerException] at that point.
 */
class AudioRendererPlaybackCharacterizationTest : AudioRendererTestBase() {
    private val samples = shortArrayOf(1, -2, 3, Short.MAX_VALUE, Short.MIN_VALUE)

    private fun ready(audioFx: Boolean = false): AndroidAudioRenderer =
        renderer(audioFx).also { assertThat(it.setUpStereo()).isEqualTo(0) }

    private fun writes() = AudioPlatform.calls.filterIsInstance<AudioCall.Write>()

    private val broadcasts: List<Intent>
        get() = shadowOf(context).broadcastIntents

    // playDecodedAudio

    @Test
    fun `the samples are written whole to the track when nothing is pending`() {
        val renderer = ready()
        ShadowAudioMoonBridge.pendingDurations = listOf(0)

        renderer.playDecodedAudio(samples)

        assertThat(writes()).containsExactly(AudioCall.Write(1, samples.toList()))
        assertThat(ShadowAudioMoonBridge.asked).isEqualTo(1)
        assertThat(log.entries.map { it.second }.filter { it.startsWith("Too much") }).isEmpty()
    }

    @Test
    fun `39 milliseconds of pending audio still lets the samples through`() {
        val renderer = ready()
        ShadowAudioMoonBridge.pendingDurations = listOf(39)

        renderer.playDecodedAudio(samples)

        assertThat(writes()).hasSize(1)
    }

    @Test
    fun `a negative pending duration lets the samples through`() {
        val renderer = ready()
        ShadowAudioMoonBridge.pendingDurations = listOf(-5)

        renderer.playDecodedAudio(samples)

        assertThat(writes()).hasSize(1)
    }

    @Test
    fun `40 milliseconds of pending audio drops the samples and logs the duration read again`() {
        val renderer = ready()
        // The check reads 40; the log reads the duration a second time and shows what it found
        ShadowAudioMoonBridge.pendingDurations = listOf(40, 77)

        renderer.playDecodedAudio(samples)

        assertThat(writes()).isEmpty()
        assertThat(ShadowAudioMoonBridge.asked).isEqualTo(2)
        assertThat(log.entries.last())
            .isEqualTo(Level.INFO to "Too much pending audio data: 77 ms")
    }

    @Test
    fun `successive packets each ask for the pending duration again`() {
        val renderer = ready()
        ShadowAudioMoonBridge.pendingDurations = listOf(0, 100, 100, 10)

        repeat(3) { renderer.playDecodedAudio(samples) }

        // The second packet is dropped (two reads); the first and the third are written
        assertThat(writes()).hasSize(2)
        assertThat(ShadowAudioMoonBridge.asked).isEqualTo(4)
    }

    @Test
    fun `packets go to the track of the last setup`() {
        val renderer = ready()
        renderer.setUpStereo()

        renderer.playDecodedAudio(samples)

        assertThat(writes().map { it.track }).containsExactly(2)
    }

    @Test
    fun `no samples with little pending audio fails when the pending audio is read`() {
        val renderer = ready()
        ShadowAudioMoonBridge.pendingDurations = listOf(0)

        assertThrows(NullPointerException::class.java) { renderer.playDecodedAudio(null) }
        assertThat(writes()).isEmpty()
    }

    @Test
    fun `no samples with much pending audio is only logged`() {
        val renderer = ready()
        ShadowAudioMoonBridge.pendingDurations = listOf(60)

        renderer.playDecodedAudio(null)

        assertThat(log.entries.last())
            .isEqualTo(Level.INFO to "Too much pending audio data: 60 ms")
    }

    @Test
    fun `samples before setup fail when little audio is pending`() {
        ShadowAudioMoonBridge.pendingDurations = listOf(0)

        assertThrows(NullPointerException::class.java) { renderer().playDecodedAudio(samples) }
    }

    @Test
    fun `samples before setup are only logged when much audio is pending`() {
        ShadowAudioMoonBridge.pendingDurations = listOf(50)

        renderer().playDecodedAudio(samples)

        assertThat(log.entries.last())
            .isEqualTo(Level.INFO to "Too much pending audio data: 50 ms")
    }

    // start and stop

    @Test
    fun `without audio effects start and stop send nothing, even before setup`() {
        val before = renderer()
        before.start()
        before.stop()

        val after = ready()
        after.start()
        after.stop()

        assertThat(broadcasts).isEmpty()
    }

    @Test
    fun `with audio effects start opens an audio effect session for the game`() {
        val renderer = ready(audioFx = true)

        renderer.start()

        val intent = broadcasts.single()
        assertThat(intent.action).isEqualTo("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION")
        assertThat(intent.getIntExtra("android.media.extra.AUDIO_SESSION", -1))
            .isEqualTo(AudioPlatform.sessionOf(1))
        assertThat(intent.getStringExtra("android.media.extra.PACKAGE_NAME"))
            .isEqualTo(context.packageName)
        assertThat(intent.getIntExtra("android.media.extra.CONTENT_TYPE", -1)).isEqualTo(2)
        assertThat(intent.extras!!.keySet())
            .containsExactly(
                "android.media.extra.AUDIO_SESSION",
                "android.media.extra.PACKAGE_NAME",
                "android.media.extra.CONTENT_TYPE",
            )
    }

    @Test
    fun `with audio effects stop closes the audio effect session`() {
        val renderer = ready(audioFx = true)

        renderer.stop()

        val intent = broadcasts.single()
        assertThat(intent.action)
            .isEqualTo("android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION")
        assertThat(intent.getIntExtra("android.media.extra.AUDIO_SESSION", -1))
            .isEqualTo(AudioPlatform.sessionOf(1))
        assertThat(intent.getStringExtra("android.media.extra.PACKAGE_NAME"))
            .isEqualTo(context.packageName)
        assertThat(intent.extras!!.keySet())
            .containsExactly("android.media.extra.AUDIO_SESSION", "android.media.extra.PACKAGE_NAME")
    }

    @Test
    fun `the audio effect session is the one of the track that was kept`() {
        // With audio effects the low latency attempts are skipped: track 1 is the small standard
        // buffer and fails, and the large standard buffer is track 2
        AudioPlatform.failSetupOf(setOf(1))
        val renderer = renderer(audioFx = true)
        assertThat(renderer.setUpStereo()).isEqualTo(0)

        renderer.start()
        renderer.stop()

        assertThat(broadcasts.map { it.getIntExtra("android.media.extra.AUDIO_SESSION", -1) })
            .containsExactly(AudioPlatform.sessionOf(2), AudioPlatform.sessionOf(2))
    }

    @Test
    fun `with audio effects start and stop fail before setup and send nothing`() {
        val renderer = renderer(audioFx = true)

        assertThrows(NullPointerException::class.java) { renderer.start() }
        assertThrows(NullPointerException::class.java) { renderer.stop() }
        assertThat(broadcasts).isEmpty()
    }

    // cleanup

    @Test
    fun `cleanup drops what is pending, then releases the track`() {
        val renderer = ready()

        renderer.cleanup()

        assertThat(
                AudioPlatform.calls.filter {
                    it is AudioCall.Pause || it is AudioCall.Flush || it is AudioCall.Release
                }
            )
            .containsExactly(AudioCall.Pause(1), AudioCall.Flush(1), AudioCall.Release(1))
            .inOrder()
    }

    @Test
    fun `a release that fails in cleanup is not hidden`() {
        val renderer = ready()
        AudioPlatform.failReleaseOf(setOf(1))

        assertThrows(IllegalStateException::class.java) { renderer.cleanup() }
        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.Pause>()).hasSize(1)
        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.Flush>()).hasSize(1)
    }

    @Test
    fun `cleanup sends no broadcast even with audio effects`() {
        val renderer = ready(audioFx = true)

        renderer.cleanup()

        assertThat(broadcasts).isEmpty()
    }

    @Test
    fun `cleanup before setup fails`() {
        assertThrows(NullPointerException::class.java) { renderer().cleanup() }
    }

    @Test
    fun `cleanup after a setup that made no track fails`() {
        AudioPlatform.failSetupOf(AudioPlatform.everyTrack)
        val renderer = renderer()
        assertThat(renderer.setUpStereo()).isEqualTo(-2)

        assertThrows(NullPointerException::class.java) { renderer.cleanup() }
    }

    @Test
    fun `cleanup keeps the released track, so a second cleanup is refused by the platform`() {
        val renderer = ready()
        renderer.cleanup()

        assertThrows(IllegalStateException::class.java) { renderer.cleanup() }
        assertThat(AudioPlatform.calls.filterIsInstance<AudioCall.Release>()).hasSize(1)
    }
}
