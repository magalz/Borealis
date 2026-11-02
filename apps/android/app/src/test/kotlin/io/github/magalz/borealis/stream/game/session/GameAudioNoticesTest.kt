package io.github.magalz.borealis.stream.game.session

import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.StreamNotice
import io.github.magalz.borealis.stream.SurroundLayout
import io.github.magalz.borealis.stream.audioOutput
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.RecordingSurfaceHolder
import io.github.magalz.borealis.stream.game.session
import io.github.magalz.borealis.stream.routeStreamAudioTo
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

/**
 * What the stream tells the viewer about its sound: that it is stereo where more channels were
 * asked for, and that it goes out over Bluetooth, at the start and again when the output changes to
 * Bluetooth while the stream runs. The notices are given once the connection is up, and the card
 * that shows them waits for the stream on screen.
 */
internal class GameAudioNoticesTest : GameTestBase() {
    private val audio = shadowOf(rig.application.getSystemService(AudioManager::class.java))

    private fun output(type: Int, vararg channels: Int) {
        rig.application.routeStreamAudioTo(audioOutput(type, *channels))
    }

    /** The notices that the session has now, each one shown and gone, in order. */
    private fun notices(): List<StreamNotice> {
        val given = mutableListOf<StreamNotice>()
        while (true) {
            val next = rig.session.notice.value ?: return given
            given += next
            rig.session.noticeShown(next)
        }
    }

    /** A device of [type] comes: the output of the stream is it from now on. */
    private fun connect(type: Int) = plug(audioOutput(type, 2))

    private fun plug(device: AudioDeviceInfo) {
        rig.application.routeStreamAudioTo(device)
        audio.addOutputDevice(device, true)
        ShadowLooper.idleMainLooper()
    }

    /** [device] goes, and the stream goes to the output [goesTo]. */
    private fun unplug(device: AudioDeviceInfo, goesTo: AudioDeviceInfo) {
        rig.application.routeStreamAudioTo(goesTo)
        audio.removeOutputDevice(device, true)
        ShadowLooper.idleMainLooper()
    }

    private fun connected() = rig.game.connectionStarted()

    @Test
    fun `a stream that fell back to stereo tells the viewer once the connection is up, not before`() {
        rig.preference("list_audio_config", "71")
        output(AudioDeviceInfo.TYPE_HDMI, 2)
        rig.create()
        assertThat(notices()).isEmpty()

        connected()

        assertThat(notices())
            .containsExactly(StreamNotice.StereoFallback(SurroundLayout.SURROUND_7_1))
    }

    @Test
    fun `a fallback from 5_1 says 5_1`() {
        rig.preference("list_audio_config", "51")
        output(AudioDeviceInfo.TYPE_HDMI, 2)
        rig.create()

        connected()

        assertThat(notices())
            .containsExactly(StreamNotice.StereoFallback(SurroundLayout.SURROUND_5_1))
    }

    @Test
    fun `a stream that starts on Bluetooth tells the viewer once the connection is up, not before`() {
        output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)
        rig.create()
        assertThat(notices()).isEmpty()

        connected()
        ShadowLooper.idleMainLooper()

        assertThat(notices()).containsExactly(StreamNotice.BluetoothOutput)
    }

    @Test
    fun `a stream with neither tells the viewer nothing`() {
        output(AudioDeviceInfo.TYPE_HDMI, 8)
        rig.create()

        connected()
        ShadowLooper.idleMainLooper()

        assertThat(notices()).isEmpty()
    }

    @Test
    fun `a fallback to stereo on Bluetooth tells the viewer both, the fallback first`() {
        rig.preference("list_audio_config", "51")
        output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)
        rig.create()

        connected()
        ShadowLooper.idleMainLooper()

        assertThat(notices())
            .containsExactly(
                StreamNotice.StereoFallback(SurroundLayout.SURROUND_5_1),
                StreamNotice.BluetoothOutput,
            )
            .inOrder()
    }

    @Test
    fun `an output that changes to Bluetooth while the stream runs tells the viewer`() {
        output(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)
        rig.create()
        connected()
        ShadowLooper.idleMainLooper()
        assertThat(notices()).isEmpty()

        connect(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)

        assertThat(notices()).containsExactly(StreamNotice.BluetoothOutput)
    }

    @Test
    fun `an output that changes while it is Bluetooth already tells the viewer nothing more`() {
        output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)
        rig.create()
        connected()
        ShadowLooper.idleMainLooper()
        assertThat(notices()).containsExactly(StreamNotice.BluetoothOutput)

        connect(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)

        assertThat(notices()).isEmpty()
    }

    @Test
    fun `an output that comes back to Bluetooth after another one tells the viewer again`() {
        val speaker = audioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)
        val bluetooth = audioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)
        rig.application.routeStreamAudioTo(speaker)
        rig.create()
        connected()
        ShadowLooper.idleMainLooper()
        plug(bluetooth)
        assertThat(notices()).containsExactly(StreamNotice.BluetoothOutput)

        unplug(bluetooth, goesTo = speaker)
        assertThat(notices()).isEmpty()
        plug(bluetooth)

        assertThat(notices()).containsExactly(StreamNotice.BluetoothOutput)
    }

    @Test
    fun `an output that changes to something else tells the viewer nothing`() {
        output(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)
        rig.create()
        connected()
        ShadowLooper.idleMainLooper()

        connect(AudioDeviceInfo.TYPE_HDMI)

        assertThat(notices()).isEmpty()
    }

    @Test
    fun `an output that changes to Bluetooth after the stream stopped tells nobody`() {
        output(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)
        rig.streaming()
        ShadowLooper.idleMainLooper()
        rig.stop()

        connect(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)

        assertThat(notices()).isEmpty()
    }

    @Test
    fun `a connection that was stopped before it started gives no notice`() {
        output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)
        rig.create()
        rig.game.surfaceCreated(RecordingSurfaceHolder())
        rig.markConnectionAttempted()
        rig.game.surfaceDestroyed(RecordingSurfaceHolder())
        rig.awaitStop()

        connected()
        ShadowLooper.idleMainLooper()

        assertThat(notices()).isEmpty()
    }
}
