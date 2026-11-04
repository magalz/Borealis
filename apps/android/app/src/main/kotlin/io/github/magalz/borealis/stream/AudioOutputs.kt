package io.github.magalz.borealis.stream

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Where the audio of the stream goes: [channelCount] is the most channels that the output takes,
 * null when the device does not say; [bluetooth] is true for an output that adds a lot of latency.
 */
data class AudioOutput(val channelCount: Int?, val bluetooth: Boolean)

/**
 * What the device says about the output that the audio of the stream goes to. From Android 13 the
 * platform says which output it routes the audio of a game to. Below it nothing does, and the
 * output is the connected Bluetooth one if there is one, else the first of HDMI, USB, wired and the
 * built-in speaker (the order of [PREFERENCE]).
 */
class AudioOutputs(private val manager: AudioManager) {
    constructor(context: Context) : this(context.getSystemService(AudioManager::class.java))

    private var callback: AudioDeviceCallback? = null

    fun current(): AudioOutput {
        val device = routed()
        return AudioOutput(
            channelCount = device?.channelCounts?.maxOrNull(),
            bluetooth = device != null && device.type in BLUETOOTH,
        )
    }

    /**
     * Calls [listener] on the main thread with the output of the moment, each time a device comes
     * or goes, until [close]; the platform also calls it once at the start. It replaces the
     * listener that an earlier call set.
     */
    fun onChange(listener: (AudioOutput) -> Unit) {
        close()
        val registered =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) =
                    listener(current())

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) =
                    listener(current())
            }
        manager.registerAudioDeviceCallback(registered, Handler(Looper.getMainLooper()))
        callback = registered
    }

    fun close() {
        callback?.let(manager::unregisterAudioDeviceCallback)
        callback = null
    }

    private fun routed(): AudioDeviceInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getAudioDevicesForAttributes(STREAM_AUDIO).firstOrNull()
        } else {
            val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            outputs.firstOrNull { it.type in BLUETOOTH } ?: outputs.minByOrNull { rank(it.type) }
        }

    private fun rank(type: Int): Int =
        PREFERENCE.indexOf(type).let { if (it < 0) PREFERENCE.size else it }

    // The constants of the newer types are inlined ints that are only compared with the type that
    // the device reports; none goes to the platform, so an older device never fails on them.
    @SuppressLint("InlinedApi")
    private companion object {
        /** The attributes of the audio track of a stream, which the platform routes by. */
        val STREAM_AUDIO: AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()

        /** A2DP, BLE audio, a hearing aid and SCO: they all go through the Bluetooth stack. */
        val BLUETOOTH =
            setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_HEARING_AID,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLE_BROADCAST,
            )

        /**
         * The outputs below Android 13, best first: HDMI, USB, wired, then the built-in speaker.
         */
        val PREFERENCE =
            listOf(
                AudioDeviceInfo.TYPE_HDMI,
                AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_HDMI_EARC,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            )
    }
}
