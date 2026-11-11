package io.github.magalz.borealis.stream.game.session

import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.audio.AndroidAudioRenderer
import com.limelight.binding.audio.AudioPlatform
import com.limelight.binding.audio.ShadowRecordingAudioTrack
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.audioOutput
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.session
import io.github.magalz.borealis.stream.routeStreamAudioTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

private const val SAMPLE_RATE = 48_000
private const val FRAME = 240

/**
 * The audio delay of the performance overlay: the video renderer builds the snapshot, and the audio
 * renderer knows the delay. Both are made by the activity, which links them: a snapshot of a
 * running stream carries what the audio renderer of that stream says. The activity also tells the
 * snapshot whether the audio goes out over Bluetooth, from the start and as the output changes.
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

    /**
     * The audio renderer gets what the host sends: 1.5 s of frames, none of them presented yet. The
     * snapshot reads the real clock, so the wait is long enough not to depend on how fast the test
     * runs.
     */
    private fun soundWaiting() {
        assertThat(audioRenderer.setup(MoonBridge.AUDIO_CONFIGURATION_STEREO, SAMPLE_RATE, FRAME))
            .isEqualTo(0)
        ShadowRecordingBridge.answers["getPendingAudioDuration"] = listOf(0)
        repeat(300) { audioRenderer.playDecodedAudio(ShortArray(FRAME * 2)) }
        AudioPlatform.timestamp = AudioPlatform.Timestamp(0, System.nanoTime())
    }

    @Test
    fun `a snapshot of a running stream carries the delay of the audio renderer of the stream`() {
        rig.streaming()
        soundWaiting()

        pushSnapshot()

        assertThat(rig.session.perf.value?.audioDelayMs).isIn(Range.closed(1_000, 1_500))
    }

    @Test
    fun `a snapshot has no audio delay while the audio renderer has none`() {
        rig.streaming()

        pushSnapshot()

        assertThat(rig.session.perf.value).isNotNull()
        assertThat(rig.session.perf.value?.audioDelayMs).isNull()
    }

    /** The platform routes the stream to [device], and the audio manager lists it as connected. */
    private fun plug(device: AudioDeviceInfo) {
        rig.application.routeStreamAudioTo(device)
        shadowOf(rig.application.getSystemService(AudioManager::class.java))
            .addOutputDevice(device, true)
        ShadowLooper.idleMainLooper()
    }

    /** [device] goes, and the platform routes the stream to [goesTo]. */
    private fun unplug(device: AudioDeviceInfo, goesTo: AudioDeviceInfo) {
        rig.application.routeStreamAudioTo(goesTo)
        shadowOf(rig.application.getSystemService(AudioManager::class.java))
            .removeOutputDevice(device, true)
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `a snapshot of a stream that started on Bluetooth says the audio goes over Bluetooth`() {
        rig.application.routeStreamAudioTo(audioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2))
        rig.streaming()

        pushSnapshot()

        assertThat(rig.session.perf.value?.audioBluetooth).isTrue()
    }

    @Test
    fun `a snapshot of a stream on another output does not`() {
        rig.application.routeStreamAudioTo(audioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2))
        rig.streaming()

        pushSnapshot()

        assertThat(rig.session.perf.value).isNotNull()
        assertThat(rig.session.perf.value?.audioBluetooth).isFalse()
    }

    @Test
    fun `a snapshot follows the output when it moves to Bluetooth and away again`() {
        val speaker = audioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)
        val bluetooth = audioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)
        rig.application.routeStreamAudioTo(speaker)
        rig.streaming()
        ShadowLooper.idleMainLooper()
        pushSnapshot()
        assertThat(rig.session.perf.value?.audioBluetooth).isFalse()

        plug(bluetooth)
        pushSnapshot()
        assertThat(rig.session.perf.value?.audioBluetooth).isTrue()

        unplug(bluetooth, goesTo = speaker)
        pushSnapshot()
        assertThat(rig.session.perf.value?.audioBluetooth).isFalse()
    }
}
