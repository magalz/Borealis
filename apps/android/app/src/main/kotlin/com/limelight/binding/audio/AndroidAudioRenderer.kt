package com.limelight.binding.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import com.limelight.LimeLog
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.jni.MoonBridge
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/** Small and large buffer, each in low latency and in standard mode. */
private const val ATTEMPTS = 4

// BOREALIS: a test stream dropped 263 packets at 40 ms and 14 at 100 ms (Google TV Streamer)
private const val PENDING_AUDIO_LIMIT_MS = 100

/** What `setup` answers when no attempt created a track. */
private const val NO_AUDIO_TRACK = -2

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MS = 1_000_000L

private fun channelMask(channelCount: Int): Int {
    val mask =
        when (channelCount) {
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            4 -> AudioFormat.CHANNEL_OUT_QUAD
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            else -> AudioFormat.CHANNEL_INVALID
        }
    return mask
}

// The channel count first: only the first check that fails logs its line.
private fun isValidStream(channelConfig: Int, samplesPerFrame: Int): Boolean {
    if (channelConfig == AudioFormat.CHANNEL_INVALID) {
        LimeLog.severe("Decoder returned unhandled channel count")
        return false
    }

    // BOREALIS: a packet of no samples has no buffer size; the second attempt divided by it
    if (samplesPerFrame <= 0) {
        LimeLog.severe("Decoder returned no samples per frame")
        return false
    }

    return true
}

// Skip low latency options if hardware sample rate doesn't match the content, or when using audio
// effects, since low latency mode precludes the use of the audio effect pipeline (as of Android
// 13). The hardware rate is asked on every attempt.
private fun skipsAttempt(lowLatency: Boolean, sampleRate: Int, enableAudioFx: Boolean): Boolean {
    val rateMismatch = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) != sampleRate
    return lowLatency && (rateMismatch || enableAudioFx)
}

private fun bufferSizeFor(
    attempt: Int,
    channelConfig: Int,
    sampleRate: Int,
    bytesPerFrame: Int,
): Int {
    val bufferSize =
        when (attempt) {
            0,
            2 -> bytesPerFrame * 2
            1,
            3 -> {
                // Try the larger buffer size
                val minimum =
                    max(
                        AudioTrack.getMinBufferSize(
                            sampleRate,
                            channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT,
                        ),
                        bytesPerFrame * 2,
                    )

                // Round to next frame
                ((minimum + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame
            }
            else -> error("Unreachable")
        }
    return bufferSize
}

class AndroidAudioRenderer(private val context: Context, private val enableAudioFx: Boolean) :
    AudioRenderer {

    // The native audio threads read it while setup and cleanup write it.
    @Volatile private var track: AudioTrack? = null

    // Set by cleanup, cleared when setup keeps a track: a repeated cleanup must not fail, while a
    // cleanup without any track still does.
    @Volatile private var cleanedUp = false

    // BOREALIS: the frames handed to the current track, to tell the audio delay (stream.perf). The
    // native audio thread adds to it and the thread that builds the overlay snapshot reads it.
    private val framesWritten = AtomicLong()

    // The inherited Java used the track without a check, so a call before a successful setup failed
    // with a NullPointerException at that point. Keep the exception.
    private fun requireTrack(): AudioTrack =
        track ?: throw NullPointerException("The audio track has not been created by setup")

    private fun createAudioTrack(
        channelConfig: Int,
        sampleRate: Int,
        bufferSize: Int,
        lowLatency: Boolean,
    ): AudioTrack {
        val attributesBuilder = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        val format =
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build()

        val trackBuilder =
            AudioTrack.Builder()
                .setAudioFormat(format)
                .setAudioAttributes(attributesBuilder.build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)

        // Use PERFORMANCE_MODE_LOW_LATENCY on O and later
        if (lowLatency) {
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        return trackBuilder.build()
    }

    // BOREALIS: a second setup releases the track of the first, which nothing would release
    private fun releasePrevious() {
        val previous = track
        track = null
        framesWritten.set(0)
        try {
            previous?.release()
        } catch (ignored: Exception) {}
    }

    // True when a track plays, which ends the attempts.
    // The printed stack trace and the generic catch stay as they were (Sprint 18c backlog).
    @Suppress("PrintStackTrace", "TooGenericExceptionCaught")
    private fun attemptTrack(
        attempt: Int,
        channelConfig: Int,
        sampleRate: Int,
        bytesPerFrame: Int,
    ): Boolean {
        // We will try:
        // 1) Small buffer, low latency mode
        // 2) Large buffer, low latency mode
        // 3) Small buffer, standard mode
        // 4) Large buffer, standard mode
        val lowLatency =
            when (attempt) {
                0,
                1 -> true
                2,
                3 -> false
                else -> error("Unreachable")
            }

        // BOREALIS: the skips come before the buffer size, so a skipped attempt does not ask the
        // platform for its minimum buffer
        if (skipsAttempt(lowLatency, sampleRate, enableAudioFx)) return false

        val bufferSize = bufferSizeFor(attempt, channelConfig, sampleRate, bytesPerFrame)
        return try {
            val created = createAudioTrack(channelConfig, sampleRate, bufferSize, lowLatency)
            track = created
            created.play()

            // Successfully created working AudioTrack. We're done here.
            LimeLog.info("Audio track configuration: $bufferSize $lowLatency")
            true
        } catch (e: Exception) {
            // Try to release the AudioTrack if we got far enough
            e.printStackTrace()
            // BOREALIS: the track is dropped whatever the release does, so a track that never
            // played is not kept
            val kept = track
            track = null
            try {
                kept?.release()
            } catch (ignored: Exception) {}
            false
        }
    }

    override fun setup(
        audioConfiguration: MoonBridge.AudioConfiguration,
        sampleRate: Int,
        samplesPerFrame: Int,
    ): Int {
        val channelConfig = channelMask(audioConfiguration.channelCount)
        if (!isValidStream(channelConfig, samplesPerFrame)) return -1

        // The default locale, as `String.format(String, Object...)` used it in the inherited Java.
        val hexConfig =
            String.format(Locale.getDefault(Locale.Category.FORMAT), "0x%X", channelConfig)
        LimeLog.info("Audio channel config: $hexConfig")

        val bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * 2

        releasePrevious()

        // We're not supposed to request less than the minimum
        // buffer size for our buffer, but it appears that we can
        // do this on many devices and it lowers audio latency.
        // We'll try the small buffer size first and if it fails,
        // use the recommended larger buffer size.
        for (attempt in 0 until ATTEMPTS) {
            if (attemptTrack(attempt, channelConfig, sampleRate, bytesPerFrame)) break
        }

        if (track == null) {
            // Couldn't create any audio track for playback
            return NO_AUDIO_TRACK
        }

        cleanedUp = false
        return 0
    }

    override fun playDecodedAudio(audioData: ShortArray?) {
        // Only queue up to 100 ms of pending audio data in addition to what AudioTrack is buffering
        // for us.
        // BOREALIS: read once, so the log shows the value that the check saw
        val pendingAudioMs = MoonBridge.getPendingAudioDuration()
        if (pendingAudioMs < PENDING_AUDIO_LIMIT_MS) {
            // This will block until the write is completed. That can cause a backlog
            // of pending audio data, so we do the above check to be able to bound
            // latency at 100 ms in that situation.
            val samples = audioData ?: throw NullPointerException("No audio data to play")
            val current = requireTrack()
            val written = current.write(samples, 0, samples.size)
            // BOREALIS: a negative count is an error, and nothing was written (stream.perf)
            if (written > 0) {
                framesWritten.addAndGet((written / current.channelCount).toLong())
            }
        } else {
            LimeLog.info("Too much pending audio data: $pendingAudioMs ms")
        }
    }

    /**
     * The audio delay in milliseconds, for the performance overlay: how long a frame handed to the
     * track waits before the track presents it. Null until the track has a timestamp, when there is
     * no track, and when no written frame still waits (an idle track has nothing to measure). A
     * delay above zero and below a millisecond reads 0. It asks the platform for the timestamp, so
     * the overlay calls it once for each snapshot and not for each packet.
     */
    fun audioDelayMs(): Int? = audioDelayMs(System.nanoTime())

    // Synthetic for Java, so that the public shape of the class stays the contract and the above.
    @JvmSynthetic
    internal fun audioDelayMs(nowNanos: Long): Int? {
        val current = track ?: return null
        val timestamp = AudioTimestamp()
        if (!current.getTimestamp(timestamp)) return null

        // The timestamp says that the track presented frame `framePosition` at `nanoTime`. The
        // frames written after it still wait, less the time since the timestamp was taken, which
        // the track has played on. When that is not above zero, nothing waits and there is no delay
        // to measure.
        val waitingFrames = framesWritten.get() - timestamp.framePosition
        val delayNanos =
            waitingFrames * NANOS_PER_SECOND / current.sampleRate - (nowNanos - timestamp.nanoTime)
        return if (delayNanos <= 0) null
        else (delayNanos / NANOS_PER_MS).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun start() {
        if (enableAudioFx) {
            // Open an audio effect control session to allow equalizers to apply audio effects
            val i = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, requireTrack().audioSessionId)
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_GAME)
            context.sendBroadcast(i)
        }
    }

    override fun stop() {
        if (enableAudioFx) {
            // Close our audio effect control session when we're stopping
            val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, requireTrack().audioSessionId)
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            context.sendBroadcast(i)
        }
    }

    override fun cleanup() {
        // BOREALIS: the first cleanup drops the track, so a second one does nothing. Before any
        // setup that kept a track, cleanup still fails as in the inherited Java.
        if (cleanedUp) return
        val current = requireTrack()
        track = null
        cleanedUp = true

        // BOREALIS: the track is released even when it cannot be paused or flushed
        try {
            // Immediately drop all pending data
            current.pause()
            current.flush()
        } finally {
            current.release()
        }
    }
}
