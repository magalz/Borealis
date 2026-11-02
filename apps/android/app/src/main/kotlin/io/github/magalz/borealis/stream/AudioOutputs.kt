package io.github.magalz.borealis.stream

import android.content.Context
import android.media.AudioManager

/**
 * Where the audio of the stream goes: [channelCount] is the most channels that the output takes,
 * null when the device does not say; [bluetooth] is true for an output that adds a lot of latency.
 */
data class AudioOutput(val channelCount: Int?, val bluetooth: Boolean)

/** What the device says about the output that the audio of the stream goes to. */
class AudioOutputs(private val manager: AudioManager) {
    constructor(context: Context) : this(context.getSystemService(AudioManager::class.java))

    fun current(): AudioOutput = AudioOutput(null, false)

    fun onChange(listener: (AudioOutput) -> Unit) = Unit

    fun close() = Unit
}
