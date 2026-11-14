package io.github.magalz.borealis.stream.game

import android.media.AudioDeviceInfo
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.PerfSnapshotSink
import io.github.magalz.borealis.stream.audioOutput
import io.github.magalz.borealis.stream.routeStreamAudioTo
import org.junit.Rule
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

private const val FALLBACK_LINE = "audio output has two channels, stereo asked of the host"

/**
 * The audio that the stream asks the host for: a 2-channel output gets stereo when the settings ask
 * for 5.1 or 7.1, because a track for more channels on it adds latency. The activity decides it
 * once, at the start.
 */
internal class GameAudioOutputTest : GameTestBase() {
    @get:Rule val log = RecordingLog()

    private fun output(type: Int, vararg channels: Int) {
        rig.application.routeStreamAudioTo(audioOutput(type, *channels))
    }

    private fun fallbackLines() = log.records.filter { it.message == FALLBACK_LINE }

    @Test
    fun `a 2-channel output and 7_1 asked, the stream asks the host for stereo and says so`() {
        rig.preference("list_audio_config", "71")
        output(AudioDeviceInfo.TYPE_HDMI, 2)

        rig.create()

        assertThat(rig.streamConfiguration.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_STEREO)
        assertThat(rig.game.stereoFallback)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_71_SURROUND)
        val line = fallbackLines().single()
        assertThat(line.level).isEqualTo(LogLevel.INFO)
        assertThat(line.fields).containsExactly("asked" to "7.1", "used" to "stereo")
    }

    @Test
    fun `a 2-channel output and 5_1 asked, the stream asks the host for stereo and says so`() {
        rig.preference("list_audio_config", "51")
        output(AudioDeviceInfo.TYPE_HDMI, 2)

        rig.create()

        assertThat(rig.streamConfiguration.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_STEREO)
        assertThat(rig.game.stereoFallback)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND)
        val line = fallbackLines().single()
        assertThat(line.fields).containsExactly("asked" to "5.1", "used" to "stereo")
    }

    @Test
    fun `an output of 8 channels keeps the 7_1 that the settings ask for`() {
        rig.preference("list_audio_config", "71")
        output(AudioDeviceInfo.TYPE_HDMI, 2, 6, 8)

        rig.create()

        assertThat(rig.streamConfiguration.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_71_SURROUND)
        assertThat(rig.game.stereoFallback).isNull()
        assertThat(fallbackLines()).isEmpty()
    }

    @Test
    fun `an output of 6 channels keeps the 5_1 that the settings ask for`() {
        rig.preference("list_audio_config", "51")
        output(AudioDeviceInfo.TYPE_HDMI, 2, 6)

        rig.create()

        assertThat(rig.streamConfiguration.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND)
        assertThat(rig.game.stereoFallback).isNull()
    }

    @Test
    fun `an output that does not say its channels keeps what the settings ask for`() {
        rig.preference("list_audio_config", "71")
        output(AudioDeviceInfo.TYPE_HDMI)

        rig.create()

        assertThat(rig.streamConfiguration.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_71_SURROUND)
        assertThat(rig.game.stereoFallback).isNull()
        assertThat(fallbackLines()).isEmpty()
    }

    @Test
    fun `stereo asked on a 2-channel output is no fallback`() {
        rig.preference("list_audio_config", "2")
        output(AudioDeviceInfo.TYPE_HDMI, 2)

        rig.create()

        assertThat(rig.streamConfiguration.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_STEREO)
        assertThat(rig.game.stereoFallback).isNull()
        assertThat(fallbackLines()).isEmpty()
    }

    @Test
    fun `a stream that starts on a Bluetooth output remembers it`() {
        output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)

        rig.create()

        assertThat(rig.game.startedOnBluetooth).isTrue()
    }

    @Test
    fun `a stream that starts on another output does not remember Bluetooth`() {
        output(AudioDeviceInfo.TYPE_HDMI, 8)

        rig.create()

        assertThat(rig.game.startedOnBluetooth).isFalse()
    }

    @Test
    fun `the fallback to stereo is no reason to forget the Bluetooth output`() {
        rig.preference("list_audio_config", "51")
        output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)

        rig.create()

        assertThat(rig.game.startedOnBluetooth).isTrue()
        assertThat(rig.game.stereoFallback)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND)
    }

    @Test
    fun `the snapshots of a stream that started on Bluetooth say so before the audio is announced`() {
        output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)
        rig.create()

        ReflectionHelpers.getField<PerfSnapshotSink>(rig.renderer, "perfSink")
            .onPerf(PerfSnapshot(59.9f, 60f, 59.8f, 0.5f, 3, 1, null, 4.5f, 20f))

        assertThat(rig.session.perf.value?.audioBluetooth).isTrue()
    }

    private fun audioEffectsOfRenderer(): Boolean =
        ReflectionHelpers.getField(rig.field<Any>("audioRenderer"), "enableAudioFx")

    @Test
    fun `the audio renderer gets the audio effects setting`() {
        rig.preference("checkbox_enable_audiofx", true)

        rig.create()

        assertThat(audioEffectsOfRenderer()).isTrue()
    }

    @Test
    fun `the audio effects are off in the audio renderer by default`() {
        rig.create()

        assertThat(audioEffectsOfRenderer()).isFalse()
    }
}
