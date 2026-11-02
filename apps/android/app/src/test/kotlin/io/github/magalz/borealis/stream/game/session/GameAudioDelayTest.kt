package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import com.limelight.binding.audio.AndroidAudioRenderer
import com.limelight.binding.audio.AudioPlatform
import com.limelight.binding.audio.ShadowRecordingAudioTrack
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.session
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

private const val SAMPLE_RATE = 48_000
private const val FRAME = 240

/**
 * The audio delay of the performance overlay: the video renderer builds the snapshot, and the audio
 * renderer knows the delay. Both are made by the activity, which links them: a snapshot of a
 * running stream carries what the audio renderer of that stream says.
 */
@Config(shadows = [ShadowRecordingBridge::class, ShadowRecordingAudioTrack::class])
internal class GameAudioDelayTest : GameTestBase() {
    @Before fun freshAudioPlatform() = AudioPlatform.reset()

    @After fun forgetAudioPlatform() = AudioPlatform.reset()

    private val audioRenderer: AndroidAudioRenderer
        get() = rig.field("audioRenderer")

    /**
     * The video renderer builds the snapshot of the window that ended, as it does once a second.
     */
    private fun pushSnapshot() {
        ReflectionHelpers.callInstanceMethod<Any?>(rig.renderer, "pushPerfSnapshot")
    }

    /** The audio renderer plays what the host sends: four packets, all of them presented. */
    private fun playedEverything() {
        assertThat(audioRenderer.setup(MoonBridge.AUDIO_CONFIGURATION_STEREO, SAMPLE_RATE, FRAME))
            .isEqualTo(0)
        ShadowRecordingBridge.answers["getPendingAudioDuration"] = listOf(0)
        repeat(4) { audioRenderer.playDecodedAudio(ShortArray(FRAME * 2)) }
        AudioPlatform.timestamp = AudioPlatform.Timestamp(4L * FRAME, System.nanoTime())
    }

    @Test
    fun `a snapshot of a running stream carries the delay of the audio renderer of the stream`() {
        rig.streaming()
        playedEverything()

        pushSnapshot()

        assertThat(rig.session.perf.value?.audioDelayMs).isEqualTo(0)
    }

    @Test
    fun `a snapshot has no audio delay while the audio renderer has none`() {
        rig.streaming()

        pushSnapshot()

        assertThat(rig.session.perf.value).isNotNull()
        assertThat(rig.session.perf.value?.audioDelayMs).isNull()
    }
}
