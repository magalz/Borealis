package com.limelight.binding.audio

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.testing.LimeLogCapture
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the code under test printed on the error stream, which is where `printStackTrace` writes. It
 * also keeps the stack traces of the failures that a test causes on purpose out of the test log.
 */
class ErrorStreamRecorder : ExternalResource() {
    private val buffer = ByteArrayOutputStream()
    private var previous: PrintStream? = null

    val text: String
        get() = buffer.toString(Charsets.UTF_8)

    override fun before() {
        previous = System.err
        System.setErr(PrintStream(buffer, true, Charsets.UTF_8))
    }

    override fun after() {
        System.setErr(previous)
    }
}

/**
 * The Robolectric setup that every test of `AndroidAudioRenderer` shares: the audio platform and
 * the native bridge are replaced by shadows (see [ShadowRecordingAudioTrack] and
 * [ShadowRecordingBridge]), and the application is the context that the renderer gets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class, ShadowRecordingAudioTrack::class])
// Abstract so that Gradle does not run the base as a test of its own.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AudioRendererTestBase {
    @get:Rule val log = LimeLogCapture()

    @get:Rule val printed = ErrorStreamRecorder()

    protected val context: Application = ApplicationProvider.getApplicationContext()

    @Before fun freshPlatform() = resetShadows()

    @After fun forgetPlatform() = resetShadows()

    private fun resetShadows() {
        AudioPlatform.reset()
        ShadowRecordingBridge.reset()
    }

    protected fun renderer(audioFx: Boolean = false) = AndroidAudioRenderer(context, audioFx)

    /** The configuration that the native layer reports for [channels] channels. */
    protected fun config(channels: Int) =
        MoonBridge.AudioConfiguration(channels, CHANNEL_MASKS[channels] ?: 0)

    /**
     * Sets [this] up as the stream does for stereo audio: 48 kHz in packets of five milliseconds.
     */
    protected fun AndroidAudioRenderer.setUpStereo(): Int = setup(config(2), SAMPLE_RATE, FRAME)
}

internal const val SAMPLE_RATE = 48_000

/** The samples of one channel in one packet: five milliseconds at 48 kHz. */
internal const val FRAME = 240

private val CHANNEL_MASKS = mapOf(2 to 0x3, 4 to 0x33, 6 to 0x3F, 8 to 0x63F)

// What the platform is asked for, as the calls that AudioPlatform records them.

internal fun outputRate() = AudioCall.OutputRate(STREAM_MUSIC)

internal fun minBuffer(channelCount: Int = 2) =
    AudioCall.MinBuffer(sampleRate = 48_000, channelCount = channelCount, encoding = PCM_16BIT)

internal fun trackSetup(
    track: Int,
    bufferSize: Int,
    lowLatency: Boolean,
    channelMask: Int = STEREO,
) =
    AudioCall.Setup(
        track = track,
        sampleRate = 48_000,
        channelMask = channelMask,
        encoding = PCM_16BIT,
        bufferSize = bufferSize,
        mode = MODE_STREAM,
        usage = USAGE_GAME,
        lowLatency = lowLatency,
    )

private const val STREAM_MUSIC = 3
private const val PCM_16BIT = 2
private const val MODE_STREAM = 1
private const val USAGE_GAME = 14
internal const val STEREO = 0xC
