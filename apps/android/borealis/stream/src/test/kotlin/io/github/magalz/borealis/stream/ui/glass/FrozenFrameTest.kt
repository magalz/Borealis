package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.stream.testing.RecordingLogSink
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one copy of the stream view that is taken when the stream fails (the owner's decision of
 * 2026-10-29): a quarter of the video like the glass, asked for once, from whichever thread reports
 * the failure, and let go of when the activity ends. A failed copy is no frame.
 */
@RunWith(AndroidJUnit4::class)
class FrozenFrameTest {
    @get:Rule val log = RecordingLogSink()
    private val source = FakeVideoSource()
    private var origin = IntOffset.Zero
    private var nanos = 0L
    private val frozen = FrozenFrame(source, { origin }, { nanos })

    private fun lines() = log.records.filter { it.tag == LogTag.VIDEO }

    // What a request is

    @Test
    fun `a request asks for one copy of the video, a quarter of its size`() {
        frozen.request()

        assertThat(source.requests.map { it.width to it.height }).containsExactly(320 to 180)
    }

    @Test
    fun `there is no frame until the copy has finished, and then the frame is that copy`() {
        frozen.request()
        assertThat(frozen.frame).isNull()

        source.finish()

        assertThat(frozen.frame?.image?.asAndroidBitmap())
            .isSameInstanceAs(source.requests.single())
    }

    @Test
    fun `the frame is where the video is in the chrome, at the scale of the copy`() {
        source.bounds = VideoBounds(left = 100, top = 30, width = 1280, height = 720)
        origin = IntOffset(40, 10)

        frozen.request()
        source.finish()

        val frame = frozen.frame!!
        assertThat(frame.left).isEqualTo(60f)
        assertThat(frame.top).isEqualTo(20f)
        assertThat(frame.scaleX).isEqualTo(4f)
        assertThat(frame.scaleY).isEqualTo(4f)
    }

    @Test
    fun `a request is made once, whoever asks and however often`() {
        frozen.request()
        frozen.request()
        source.finish()
        frozen.request()

        assertThat(source.requests).hasSize(1)
        assertThat(frozen.requested).isTrue()
    }

    @Test
    fun `a request made on another thread asks at once, and the frame comes when the copy ends`() {
        Thread { frozen.request() }.apply { start() }.join()

        assertThat(source.requests).hasSize(1)
        assertThat(frozen.requested).isTrue()
        assertThat(frozen.frame).isNull()

        source.finish()

        assertThat(frozen.frame).isNotNull()
    }

    @Test
    fun `a request made on another thread reads the size of the video, not where it is`() {
        Thread { frozen.request() }.apply { start() }.join()

        // Where the video is on the screen is a question for the main thread, asked when the copy
        // ends.
        assertThat(source.boundsThreads).isEmpty()

        source.finish()

        assertThat(source.boundsThreads).containsExactly(Thread.currentThread())
    }

    @Test
    fun `the copy is pending from the request until it has ended, whichever way`() {
        assertThat(frozen.pending).isFalse()

        frozen.request()
        assertThat(frozen.pending).isTrue()
        assertThat(frozen.frame).isNull()

        source.finish()
        assertThat(frozen.pending).isFalse()
        assertThat(frozen.frame).isNotNull()
    }

    @Test
    fun `a copy that fails is no longer pending, and there is no frame`() {
        frozen.request()

        source.finish(ok = false)

        assertThat(frozen.pending).isFalse()
        assertThat(frozen.frame).isNull()
    }

    @Test
    fun `a video with nothing to copy is never pending`() {
        source.bounds = null

        frozen.request()

        assertThat(frozen.pending).isFalse()
    }

    @Test
    fun `release ends a pending copy, and the copy that ends after it is dropped`() {
        frozen.request()

        frozen.release()
        assertThat(frozen.pending).isFalse()
        source.finish()

        assertThat(frozen.pending).isFalse()
        assertThat(frozen.frame).isNull()
    }

    @Test
    fun `nothing has been asked for until a request is made`() {
        assertThat(frozen.requested).isFalse()
        assertThat(source.requests).isEmpty()
    }

    // No frame

    @Test
    fun `a video with nothing to copy asks for nothing and leaves no frame`() {
        source.bounds = null

        frozen.request()

        assertThat(source.requests).isEmpty()
        assertThat(frozen.frame).isNull()
    }

    @Test
    fun `a copy that fails leaves no frame`() {
        frozen.request()

        source.finish(ok = false)

        assertThat(frozen.frame).isNull()
    }

    @Test
    fun `a failed copy is not asked for again`() {
        frozen.request()
        source.finish(ok = false)

        frozen.request()

        assertThat(source.requests).hasSize(1)
    }

    // Letting go

    @Test
    fun `release lets go of the frame`() {
        frozen.request()
        source.finish()
        assertThat(frozen.frame).isNotNull()

        frozen.release()

        assertThat(frozen.frame).isNull()
    }

    @Test
    fun `a copy that ends after release is dropped`() {
        frozen.request()

        frozen.release()
        source.finish()

        assertThat(frozen.frame).isNull()
    }

    @Test
    fun `nothing is asked for after release`() {
        frozen.release()

        frozen.request()

        assertThat(source.requests).isEmpty()
    }

    // The log

    @Test
    fun `a request says so, with the size of the copy and without any address`() {
        frozen.request()

        val line = lines().single { it.message == "Frozen frame requested" }
        assertThat(line.level).isEqualTo(LogLevel.INFO)
        assertThat(line.fields).containsExactly("width" to "320", "height" to "180")
    }

    @Test
    fun `a copy that worked says how long it took`() {
        frozen.request()
        nanos += 12 * NANOS_PER_MILLI

        source.finish()

        val line = lines().single { it.message == "Frozen frame copied" }
        assertThat(line.level).isEqualTo(LogLevel.INFO)
        assertThat(line.fields).containsExactly("ms" to "12")
    }

    @Test
    fun `a copy that failed says so`() {
        frozen.request()

        source.finish(ok = false)

        val line = lines().single { it.message == "Frozen frame failed" }
        assertThat(line.level).isEqualTo(LogLevel.WARN)
        assertThat(line.fields).containsExactly("reason" to "copy")
    }

    @Test
    fun `a video with nothing to copy says so`() {
        source.bounds = null

        frozen.request()

        val line = lines().single { it.message == "Frozen frame failed" }
        assertThat(line.fields).containsExactly("reason" to "no_surface")
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
