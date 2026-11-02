package io.github.magalz.borealis.stream

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import com.google.common.collect.ImmutableList
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.util.ReflectionHelpers

/** The attributes of the audio of a stream, which the platform routes by. */
private val GAME_AUDIO: AudioAttributes =
    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()

private fun channelMask(channels: Int): Int =
    when (channels) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        2 -> AudioFormat.CHANNEL_OUT_STEREO
        6 -> AudioFormat.CHANNEL_OUT_5POINT1
        8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
        else -> error("no channel mask for $channels channels in this helper")
    }

/**
 * An output device of [type], an `AudioDeviceInfo.TYPE_*` value, that takes each of [channels]
 * channels. No [channels] is a device that does not say. The builder of Robolectric leaves the
 * channel masks of the port unset, and the device reads them to tell its channel counts.
 */
internal fun audioOutput(type: Int, vararg channels: Int): AudioDeviceInfo {
    val device = AudioDeviceInfoBuilder.newBuilder().setType(type).build()
    val port = ReflectionHelpers.getField<Any>(device, "mPort")
    ReflectionHelpers.setField(port, "mChannelMasks", channels.map(::channelMask).toIntArray())
    ReflectionHelpers.setField(port, "mChannelIndexMasks", IntArray(0))
    return device
}

/** The platform routes the audio of a stream to [devices], on Android 13 and later. */
internal fun Context.routeStreamAudioTo(vararg devices: AudioDeviceInfo) {
    shadowOf(getSystemService(AudioManager::class.java))
        .setAudioDevicesForAttributes(GAME_AUDIO, ImmutableList.copyOf(devices))
}
