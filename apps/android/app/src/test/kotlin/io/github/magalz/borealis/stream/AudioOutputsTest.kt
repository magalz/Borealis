package io.github.magalz.borealis.stream

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * What the device says about the output that the audio of a stream goes to: the platform routes the
 * audio of a stream on Android 13 and later, and below it the order of the types of output decides.
 */
@RunWith(RobolectricTestRunner::class)
class AudioOutputsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val outputs = AudioOutputs(context)
    private val audio = shadowOf(context.getSystemService(AudioManager::class.java))

    private val bluetoothTypes =
        mapOf(
            "A2DP" to AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            "SCO" to AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            "hearing aid" to AudioDeviceInfo.TYPE_HEARING_AID,
            "BLE headset" to AudioDeviceInfo.TYPE_BLE_HEADSET,
            "BLE speaker" to AudioDeviceInfo.TYPE_BLE_SPEAKER,
            "BLE broadcast" to AudioDeviceInfo.TYPE_BLE_BROADCAST,
        )

    // The output that the platform routes to

    @Test
    fun `the output that the platform routes the stream to gives its channel count`() {
        context.routeStreamAudioTo(audioOutput(AudioDeviceInfo.TYPE_HDMI, 2))

        assertThat(outputs.current()).isEqualTo(AudioOutput(channelCount = 2, bluetooth = false))
    }

    @Test
    fun `the most channels that the output takes is its channel count`() {
        context.routeStreamAudioTo(audioOutput(AudioDeviceInfo.TYPE_HDMI, 2, 6, 8))

        assertThat(outputs.current().channelCount).isEqualTo(8)
    }

    @Test
    fun `an output that does not say its channels has no channel count`() {
        context.routeStreamAudioTo(audioOutput(AudioDeviceInfo.TYPE_HDMI))

        assertThat(outputs.current()).isEqualTo(AudioOutput(channelCount = null, bluetooth = false))
    }

    @Test
    fun `no output at all has no channel count and is not Bluetooth`() {
        assertThat(outputs.current()).isEqualTo(AudioOutput(channelCount = null, bluetooth = false))
    }

    @Test
    fun `the first output that the platform routes to is the one`() {
        context.routeStreamAudioTo(
            audioOutput(AudioDeviceInfo.TYPE_HDMI, 8),
            audioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2),
        )

        assertThat(outputs.current().channelCount).isEqualTo(8)
    }

    // Bluetooth

    @Test
    fun `a Bluetooth output says it is Bluetooth`() {
        for ((name, type) in bluetoothTypes) {
            context.routeStreamAudioTo(audioOutput(type, 2))

            val output = outputs.current()

            assertWithMessage(name).that(output).isEqualTo(AudioOutput(2, bluetooth = true))
        }
    }

    @Test
    fun `an output that is not Bluetooth says it is not`() {
        val types =
            listOf(
                AudioDeviceInfo.TYPE_HDMI,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            )
        for (type in types) {
            context.routeStreamAudioTo(audioOutput(type, 2))

            assertWithMessage("type $type").that(outputs.current().bluetooth).isFalse()
        }
    }

    // Below Android 13, where nothing says what the platform routes to

    @Config(sdk = [31])
    @Test
    fun `below Android 13 a connected Bluetooth output is the one`() {
        audio.setOutputDevices(
            listOf(
                audioOutput(AudioDeviceInfo.TYPE_HDMI, 8),
                audioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2),
            )
        )

        assertThat(outputs.current()).isEqualTo(AudioOutput(channelCount = 2, bluetooth = true))
    }

    @Config(sdk = [31])
    @Test
    fun `below Android 13 the output is the first of HDMI, USB, wired and built-in`() {
        // Each output takes a different number of channels, to tell which one was chosen.
        val inOrder =
            listOf(
                audioOutput(AudioDeviceInfo.TYPE_HDMI, 8),
                audioOutput(AudioDeviceInfo.TYPE_USB_DEVICE, 6),
                audioOutput(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, 2),
                audioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 1),
            )

        // The best output goes away one by one; the list that the device gives is the other way.
        val channels =
            inOrder.indices.map { gone ->
                audio.setOutputDevices(inOrder.drop(gone).reversed())
                outputs.current().channelCount
            }

        assertThat(channels).containsExactly(8, 6, 2, 1).inOrder()
    }

    @Config(sdk = [31])
    @Test
    fun `below Android 13 no output has no channel count`() {
        assertThat(outputs.current()).isEqualTo(AudioOutput(channelCount = null, bluetooth = false))
    }

    // A change of the outputs

    @Test
    fun `a device that comes or goes tells the listener the output of the moment`() {
        context.routeStreamAudioTo(audioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2))
        val seen = CopyOnWriteArrayList<AudioOutput>()
        outputs.onChange { seen += it }
        ShadowLooper.idleMainLooper()

        val bluetooth = audioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2)
        context.routeStreamAudioTo(bluetooth)
        audio.addOutputDevice(bluetooth, true)
        ShadowLooper.idleMainLooper()

        assertThat(seen.lastOrNull()).isEqualTo(AudioOutput(channelCount = 2, bluetooth = true))
    }

    @Test
    fun `a listener that was closed hears nothing more`() {
        val seen = CopyOnWriteArrayList<AudioOutput>()
        outputs.onChange { seen += it }
        ShadowLooper.idleMainLooper()
        val before = seen.size

        outputs.close()
        audio.addOutputDevice(audioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 2), true)
        ShadowLooper.idleMainLooper()

        assertThat(seen).hasSize(before)
    }
}
