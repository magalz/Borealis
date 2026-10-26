package com.limelight.binding.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import com.limelight.LimeLog
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.jni.MoonBridge
import java.util.Locale
import kotlin.math.max

/** `AudioFormat.CHANNEL_OUT_7POINT1_SURROUND`, spelled out as in the inherited Java. */
private const val CHANNEL_OUT_7POINT1_SURROUND = 0x000018fc

/** Small and large buffer, each in low latency and in standard mode. */
private const val ATTEMPTS = 4

private const val PENDING_AUDIO_LIMIT_MS = 40

/** What `setup` answers when no attempt created a track. */
private const val NO_AUDIO_TRACK = -2

class AndroidAudioRenderer(private val context: Context, private val enableAudioFx: Boolean) :
    AudioRenderer {

    private var track: AudioTrack? = null

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

        // The default locale, as `String.format(String, Object...)` used it in the inherited Java.
        val hexConfig =
            String.format(Locale.getDefault(Locale.Category.FORMAT), "0x%X", channelConfig)
        LimeLog.info("Audio channel config: $hexConfig")

        val bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * 2

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
                try {
                    val kept = track
                    if (kept != null) {
                        kept.release()
                        track = null
                    }
                } catch (ignored: Exception) {}
            }
        }

        if (track == null) {
            // Couldn't create any audio track for playback
            return NO_AUDIO_TRACK
        }

        return 0
    }

    override fun playDecodedAudio(audioData: ShortArray?) {
        // Only queue up to 40 ms of pending audio data in addition to what AudioTrack is buffering
        // for us.
        if (MoonBridge.getPendingAudioDuration() < PENDING_AUDIO_LIMIT_MS) {
            // This will block until the write is completed. That can cause a backlog
            // of pending audio data, so we do the above check to be able to bound
            // latency at 40 ms in that situation.
            val samples = audioData ?: throw NullPointerException("No audio data to play")
            requireTrack().write(samples, 0, samples.size)
        } else {
            LimeLog.info("Too much pending audio data: ${MoonBridge.getPendingAudioDuration()} ms")
        }
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
        val current = requireTrack()

        // Immediately drop all pending data
        current.pause()
        current.flush()

        current.release()
    }
}
