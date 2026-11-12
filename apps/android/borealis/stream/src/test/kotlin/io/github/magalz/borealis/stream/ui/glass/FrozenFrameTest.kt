package io.github.magalz.borealis.stream.ui.glass

import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.stream.testing.RecordingLogSink
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

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

    /** What the frame asked the main thread to run later, with the delay in milliseconds. */
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private val frozen =
        FrozenFrame(
            source,
            { origin },
            { nanos },
            { delay, action -> scheduled += delay to action },
        )

    private fun lines() = log.records.filter { it.tag == LogTag.VIDEO }

    /** The time cap of the copy runs out, as the main thread would run it a second later. */
    private fun capRunsOut() {
        assertThat(scheduled).hasSize(1)
        scheduled.single().second()
    }

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
    fun `the frame follows the video when it moves or is resized after the copy`() {
        frozen.request()
        source.finish()

        // A rotation, a split-screen resize or the end of picture-in-picture.
        source.bounds = VideoBounds(left = 300, top = 50, width = 640, height = 360)
        origin = IntOffset(40, 10)

        val frame = frozen.frame!!
        assertThat(frame.left).isEqualTo(260f)
        assertThat(frame.top).isEqualTo(40f)
        assertThat(frame.scaleX).isEqualTo(2f)
        assertThat(frame.scaleY).isEqualTo(2f)
    }

    @Test
    fun `the frame keeps its last place while the video has no bounds`() {
        source.bounds = VideoBounds(left = 100, top = 30, width = 1280, height = 720)
        frozen.request()
        source.finish()

        // The surface is gone for a moment, as when picture-in-picture ends.
        source.bounds = null
        origin = IntOffset(40, 10)

        val frame = frozen.frame!!
        assertThat(frame.left).isEqualTo(60f)
        assertThat(frame.top).isEqualTo(20f)
        assertThat(frame.scaleX).isEqualTo(4f)
        assertThat(frozen.coversBackground).isTrue()
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

    // The time cap of the copy

    @Test
    fun `a request gives the copy a second to end`() {
        frozen.request()

        assertThat(scheduled.map { it.first }).containsExactly(1_000L)
    }

    @Test
    fun `a copy that does not end in time is no longer pending, and there is no frame`() {
        frozen.request()

        capRunsOut()

        assertThat(frozen.pending).isFalse()
        assertThat(frozen.frame).isNull()
        assertThat(frozen.coversBackground).isFalse()
    }

    @Test
    fun `a copy that ends after the cap is ignored`() {
        frozen.request()
        capRunsOut()

        source.finish()

        assertThat(frozen.frame).isNull()
        assertThat(frozen.pending).isFalse()
        assertThat(frozen.coversBackground).isFalse()
    }

    @Test
    fun `a copy that ends before the cap works as before, and the cap then changes nothing`() {
        frozen.request()
        source.finish()

        capRunsOut()

        assertThat(frozen.frame).isNotNull()
        assertThat(frozen.pending).isFalse()
        assertThat(frozen.coversBackground).isTrue()
    }

    @Test
    fun `a copy that failed before the cap is not reported again by the cap`() {
        frozen.request()
        source.finish(ok = false)

        capRunsOut()

        assertThat(lines().filter { it.message == "Frozen frame failed" }).hasSize(1)
    }

    @Test
    fun `a cap that runs out after release changes nothing`() {
        frozen.request()
        frozen.release()

        capRunsOut()

        assertThat(frozen.frame).isNull()
        assertThat(frozen.pending).isFalse()
        assertThat(lines().filter { it.message == "Frozen frame failed" }).isEmpty()
    }

    @Test
    fun `by default the cap runs on the main thread a second after the request`() {
        val real = FrozenFrame(source, { origin }, { nanos })
        // The activity asks from the connection thread.
        Thread { real.request() }.apply { start() }.join()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(999))
        assertThat(real.pending).isTrue()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2))
        assertThat(real.pending).isFalse()
        assertThat(real.frame).isNull()
    }

    @Test
    fun `a video with nothing to copy has no cap to wait for`() {
        source.bounds = null

        frozen.request()

        assertThat(scheduled).isEmpty()
    }

    @Test
    fun `a copy that did not end in time says so`() {
        frozen.request()

        capRunsOut()

        val line = lines().single { it.message == "Frozen frame failed" }
        assertThat(line.level).isEqualTo(LogLevel.WARN)
        assertThat(line.fields).containsExactly("reason" to "timeout")
    }

    // What covers the background

    @Test
    fun `nothing covers the background until a copy is asked for`() {
        assertThat(frozen.coversBackground).isFalse()
    }

    @Test
    fun `a pending copy covers the background, with its black, and so does the frame`() {
        frozen.request()
        assertThat(frozen.coversBackground).isTrue()

        source.finish()

        assertThat(frozen.frame).isNotNull()
        assertThat(frozen.coversBackground).isTrue()
    }

    @Test
    fun `a copy that failed, or that had nothing to copy, covers nothing`() {
        frozen.request()
        source.finish(ok = false)
        assertThat(frozen.coversBackground).isFalse()

        val nothing = FrozenFrame(FakeVideoSource(bounds = null), { IntOffset.Zero }, { nanos })
        nothing.request()

        assertThat(nothing.coversBackground).isFalse()
    }

    @Test
    fun `release uncovers the background`() {
        frozen.request()
        source.finish()

        frozen.release()

        assertThat(frozen.coversBackground).isFalse()
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

    @Test
    fun `a copy that worked but ends with the video gone keeps no frame and says so`() {
        frozen.request()

        // The surface is gone when the copy ends, as when picture-in-picture ends under it.
        source.bounds = null
        source.finish()

        assertThat(frozen.frame).isNull()
        assertThat(lines().map { it.message })
            .containsExactly("Frozen frame requested", "Frozen frame failed")
        val line = lines().single { it.message == "Frozen frame failed" }
        assertThat(line.level).isEqualTo(LogLevel.WARN)
        assertThat(line.fields).containsExactly("reason" to "no_surface")
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
