package com.limelight.binding.audio

import android.media.AudioAttributes
import android.os.Parcel
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

/** One call that the code under test made into the audio platform, in the order it made it. */
internal sealed interface AudioCall {
    /** `AudioTrack.getNativeOutputSampleRate`: the hardware rate of [streamType]. */
    data class OutputRate(val streamType: Int) : AudioCall

    /** `AudioTrack.getMinBufferSize`, as the native query sees it. */
    data class MinBuffer(val sampleRate: Int, val channelCount: Int, val encoding: Int) : AudioCall

    /** The creation of the track number [track] (1 for the first), as `native_setup` sees it. */
    data class Setup(
        val track: Int,
        val sampleRate: Int,
        val channelMask: Int,
        val encoding: Int,
        val bufferSize: Int,
        val mode: Int,
        val usage: Int,
        val lowLatency: Boolean,
    ) : AudioCall

    /** The samples that one write handed to the track [track]. */
    data class Write(val track: Int, val samples: List<Short>) : AudioCall

    data class Start(val track: Int) : AudioCall

    data class Pause(val track: Int) : AudioCall

    data class Flush(val track: Int) : AudioCall

    data class Stop(val track: Int) : AudioCall

    data class Release(val track: Int) : AudioCall
}

/**
 * Stands in for the audio hardware that a unit test does not have. Robolectric's own `AudioTrack`
 * shadow keeps none of what a renderer decides (the buffer size, the performance mode, the
 * samples) and answers neither the hardware rate nor the samples of a write, so this one replaces
 * it. The real `AudioTrack` and `AudioTrack.Builder` still run on top of it, with their own checks.
 *
 * State lives in [AudioPlatform], which every test of one Robolectric sandbox shares: use the
 * [AudioPlatform] rule, which resets it before each test.
 *
 * Use it with `@Config(shadows = [ShadowRecordingAudioTrack::class, ...])`.
 */
@Implements(android.media.AudioTrack::class)
@Suppress("FunctionNaming", "unused", "LongParameterList", "UNUSED_PARAMETER")
internal class ShadowRecordingAudioTrack {
    private var track = 0

    /**
     * What `AudioTrack` passes to its native constructor. This 15-argument signature is the one of
     * SDK 35, which `robolectric.properties` pins and which is the only level checked: another SDK
     * level has its own signature and needs its own shadow method.
     */
    @Implementation
    fun native_setup(
        weakThis: Any?,
        attributes: Any?,
        sampleRate: IntArray?,
        channelMask: Int,
        channelIndexMask: Int,
        encoding: Int,
        bufferSize: Int,
        mode: Int,
        sessionId: IntArray?,
        attributionSource: Parcel?,
        nativeAudioTrack: Long,
        offload: Boolean,
        encapsulationMode: Int,
        tunerConfiguration: Any?,
        opPackageName: String?,
    ): Int {
        track = AudioPlatform.nextTrack()
        val audioAttributes = attributes as AudioAttributes
        val flags = ReflectionHelpers.callInstanceMethod<Int>(audioAttributes, "getAllFlags")
        AudioPlatform.record(
            AudioCall.Setup(
                track = track,
                sampleRate = checkNotNull(sampleRate)[0],
                channelMask = channelMask,
                encoding = encoding,
                bufferSize = bufferSize,
                mode = mode,
                usage = audioAttributes.usage,
                lowLatency = flags and FLAG_LOW_LATENCY != 0,
            )
        )
        val result = AudioPlatform.onSetup(track)
        if (result == 0) checkNotNull(sessionId)[0] = AudioPlatform.sessionOf(track)
        return result
    }

    @Implementation
    fun native_start() {
        AudioPlatform.record(AudioCall.Start(track))
        AudioPlatform.onStart(track)
    }

    @Implementation
    fun native_pause() {
        AudioPlatform.record(AudioCall.Pause(track))
    }

    @Implementation
    fun native_flush() {
        AudioPlatform.record(AudioCall.Flush(track))
    }

    @Implementation
    fun native_stop() {
        AudioPlatform.record(AudioCall.Stop(track))
    }

    @Implementation
    fun native_release() {
        AudioPlatform.record(AudioCall.Release(track))
        AudioPlatform.onRelease(track)
    }

    @Implementation
    fun native_write_short(
        data: ShortArray?,
        offsetInShorts: Int,
        sizeInShorts: Int,
        format: Int,
        blocking: Boolean,
    ): Int {
        val samples = checkNotNull(data).slice(offsetInShorts until offsetInShorts + sizeInShorts)
        AudioPlatform.record(AudioCall.Write(track, samples))
        return sizeInShorts
    }

    companion object {
        private const val FLAG_LOW_LATENCY = 0x100

        @Implementation
        @JvmStatic
        fun native_get_output_sample_rate(streamType: Int): Int {
            AudioPlatform.record(AudioCall.OutputRate(streamType))
            return AudioPlatform.nativeOutputRate
        }

        @Implementation
        @JvmStatic
        fun native_get_min_buff_size(
            sampleRate: Int,
            channelCount: Int,
            encoding: Int,
        ): Int {
            AudioPlatform.record(AudioCall.MinBuffer(sampleRate, channelCount, encoding))
            return AudioPlatform.minBufferSize
        }
    }
}

/**
 * What a test scripts and reads of [ShadowRecordingAudioTrack]: the calls it recorded, the answers
 * of the hardware, and what the next track does when it is created, started and released.
 *
 * A fresh scenario starts with a 48 kHz output, a minimum buffer of 1 byte and tracks that are
 * created, started and released without a failure.
 */
internal object AudioPlatform {
    private const val FIRST_SESSION = 4_000
    private const val SETUP_ERROR = -1

    /** Tracks 1 to 10: more than any scenario creates. */
    val everyTrack: Set<Int> = (1..10).toSet()

    val calls = CopyOnWriteArrayList<AudioCall>()

    /** The answer of `getNativeOutputSampleRate`. */
    @Volatile var nativeOutputRate = 48_000

    /** The answer of `getMinBufferSize`, as the native query returns it. */
    @Volatile var minBufferSize = 1

    /** The result of the native constructor of the track [track]: 0 is success. */
    @Volatile var onSetup: (track: Int) -> Int = { 0 }

    /** What starting the track [track] does; throwing makes `play` fail. */
    @Volatile var onStart: (track: Int) -> Unit = {}

    /** What releasing the track [track] does; throwing makes `release` fail. */
    @Volatile var onRelease: (track: Int) -> Unit = {}

    private var tracks = 0

    /** The audio session of the track [track]: the number that `getAudioSessionId` answers. */
    fun sessionOf(track: Int): Int = FIRST_SESSION + track

    fun record(call: AudioCall) {
        calls += call
    }

    fun nextTrack(): Int = ++tracks

    /** The setups, in order. */
    val setups: List<AudioCall.Setup>
        get() = calls.filterIsInstance<AudioCall.Setup>()

    /** Makes the native constructor of each track in [tracks] fail, as when a device refuses it. */
    fun failSetupOf(tracks: Set<Int>) {
        onSetup = { if (it in tracks) SETUP_ERROR else 0 }
    }

    /** Makes `play` throw an [IllegalStateException] for each track in [tracks]. */
    fun failStartOf(tracks: Set<Int>) {
        onStart = { check(it !in tracks) { "cannot start track $it" } }
    }

    /** Makes `release` throw an [IllegalStateException] for each track in [tracks]. */
    fun failReleaseOf(tracks: Set<Int>) {
        onRelease = { check(it !in tracks) { "cannot release track $it" } }
    }

    fun reset() {
        calls.clear()
        tracks = 0
        nativeOutputRate = 48_000
        minBufferSize = 1
        onSetup = { 0 }
        onStart = {}
        onRelease = {}
    }
}
