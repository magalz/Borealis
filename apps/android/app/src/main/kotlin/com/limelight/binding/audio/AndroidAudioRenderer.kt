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

/** `AudioFormat.CHANNEL_OUT_7POINT1_SURROUND`, spelled out as in the inherited Java. */
private const val CHANNEL_OUT_7POINT1_SURROUND = 0x000018fc

/** Small and large buffer, each in low latency and in standard mode. */
private const val ATTEMPTS = 4

// BOREALIS: 100 ms and not 40: on a Google TV Streamer, a test stream dropped 263 packets at 40 ms
// and 14 at 100 ms
private const val PENDING_AUDIO_LIMIT_MS = 100

/** What `setup` answers when no attempt created a track. */
private const val NO_AUDIO_TRACK = -2

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MS = 1_000_000L

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

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress(
        "CyclomaticComplexMethod",
        "LongMethod",
        "LoopWithTooManyJumpStatements",
        "NestedBlockDepth",
        "PrintStackTrace",
        "ReturnCount",
        "TooGenericExceptionCaught",
    )
    override fun setup(
        audioConfiguration: MoonBridge.AudioConfiguration,
        sampleRate: Int,
        samplesPerFrame: Int,
    ): Int {
        val channelConfig =
            when (audioConfiguration.channelCount) {
                2 -> AudioFormat.CHANNEL_OUT_STEREO
                4 -> AudioFormat.CHANNEL_OUT_QUAD
                6 -> AudioFormat.CHANNEL_OUT_5POINT1
                // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND isn't available until Android 6.0,
                // yet the CHANNEL_OUT_SIDE_LEFT and CHANNEL_OUT_SIDE_RIGHT constants were added
                // in 5.0, so just hardcode the constant so we can work on Lollipop.
                8 -> CHANNEL_OUT_7POINT1_SURROUND
                else -> {
                    LimeLog.severe("Decoder returned unhandled channel count")
                    return -1
                }
            }

        // BOREALIS: a packet of no samples has no buffer size; the second attempt divided by it
        if (samplesPerFrame <= 0) {
            LimeLog.severe("Decoder returned no samples per frame")
            return -1
        }

        // The default locale, as `String.format(String, Object...)` used it in the inherited Java.
        val hexConfig =
            String.format(Locale.getDefault(Locale.Category.FORMAT), "0x%X", channelConfig)
        LimeLog.info("Audio channel config: $hexConfig")

        val bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * 2

        // BOREALIS: a second setup releases the track of the first, which nothing would release
        val previous = track
        track = null
        framesWritten.set(0)
        try {
            previous?.release()
        } catch (ignored: Exception) {}

        // We're not supposed to request less than the minimum
        // buffer size for our buffer, but it appears that we can
        // do this on many devices and it lowers audio latency.
        // We'll try the small buffer size first and if it fails,
        // use the recommended larger buffer size.

        for (i in 0 until ATTEMPTS) {
            // We will try:
            // 1) Small buffer, low latency mode
            // 2) Large buffer, low latency mode
            // 3) Small buffer, standard mode
            // 4) Large buffer, standard mode

            val lowLatency =
                when (i) {
                    0,
                    1 -> true
                    2,
                    3 -> false
                    else -> error("Unreachable")
                }

            // BOREALIS: the skips come before the buffer size, so a skipped attempt does not ask
            // the platform for its minimum buffer

            // Skip low latency options if hardware sample rate doesn't match the content
            if (
                AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) != sampleRate &&
                    lowLatency
            ) {
                continue
            }

            // Skip low latency options when using audio effects, since low latency mode
            // precludes the use of the audio effect pipeline (as of Android 13).
            if (enableAudioFx && lowLatency) {
                continue
            }

            val bufferSize =
                when (i) {
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

            try {
                val created = createAudioTrack(channelConfig, sampleRate, bufferSize, lowLatency)
                track = created
                created.play()

                // Successfully created working AudioTrack. We're done here.
                LimeLog.info("Audio track configuration: $bufferSize $lowLatency")
                break
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
            }
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
     * track waits before the track presents it. Null until the track has a timestamp, and when
     * there is no track. It asks the platform for the timestamp, so the overlay calls it once for
     * each snapshot and not for each packet.
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
        // the track has played on. Never below zero.
        val waitingFrames = framesWritten.get() - timestamp.framePosition
        val delayNanos =
            waitingFrames * NANOS_PER_SECOND / current.sampleRate - (nowNanos - timestamp.nanoTime)
        return (delayNanos / NANOS_PER_MS).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
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
