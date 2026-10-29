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
 * The copies of the video that the menu glass is made of (ADR 0061, decision 1): a copy a quarter
 * of the video, at most one in flight, into bitmaps that are never read while a copy writes them,
 * and a line in the log each second.
 */
@RunWith(AndroidJUnit4::class)
class VideoGlassFramesTest {
    @get:Rule val log = RecordingLogSink()
    private val source = FakeVideoSource()
    private var nanos = 0L
    private var origin = IntOffset.Zero
    private val frames = VideoGlassFrames(source, { origin }, { nanos })

    private fun shown() = frames.frame?.image?.asAndroidBitmap()

    private fun copyOnce() {
        frames.tick()
        source.finish()
    }

    // What a copy is

    @Test
    fun `a tick asks for a copy of the video a quarter of its size`() {
        frames.tick()

        assertThat(source.requests.map { it.width to it.height }).containsExactly(320 to 180)
    }

    @Test
    fun `there is no frame until a copy has finished, and then the frame is that copy`() {
        frames.tick()
        assertThat(frames.frame).isNull()

        source.finish()

        assertThat(shown()).isSameInstanceAs(source.requests.single())
    }

    @Test
    fun `the frame is where the video is in the chrome, at the scale of the copy`() {
        source.bounds = VideoBounds(left = 100, top = 30, width = 1280, height = 720)
        origin = IntOffset(40, 10)

        copyOnce()

        val frame = frames.frame!!
        assertThat(frame.left).isEqualTo(60f)
        assertThat(frame.top).isEqualTo(20f)
        assertThat(frame.scaleX).isEqualTo(4f)
        assertThat(frame.scaleY).isEqualTo(4f)
    }

    @Test
    fun `a video of another size is copied into a bitmap of its own size`() {
        copyOnce()
        source.bounds = VideoBounds(0, 0, 1920, 1080)

        frames.tick()

        assertThat(source.requests.last().let { it.width to it.height }).isEqualTo(480 to 270)
    }

    // At most one in flight

    @Test
    fun `a tick while a copy is pending asks for nothing`() {
        frames.tick()
        frames.tick()
        frames.tick()

        assertThat(source.requests).hasSize(1)
        assertThat(source.mostInFlight).isEqualTo(1)
    }

    @Test
    fun `the next copy can be asked for once the one before has ended, well or badly`() {
        frames.tick()
        source.finish(ok = false)
        frames.tick()
        source.finish(ok = true)
        frames.tick()

        assertThat(source.requests).hasSize(3)
        assertThat(source.mostInFlight).isEqualTo(1)
    }

    // Never a bitmap that is being written, or that was never filled

    @Test
    fun `the bitmap that a copy writes is never the one on show`() {
        repeat(10) {
            copyOnce()
            val onShow = shown()
            frames.tick()

            assertThat(source.beingWritten).doesNotContain(onShow)
            source.finish()
        }
    }

    @Test
    fun `what is on show is always a bitmap that a copy has finished filling`() {
        repeat(10) {
            frames.tick()
            source.finish()
            assertThat(shown()).isIn(source.filled)
        }
    }

    @Test
    fun `a copy that fails leaves the frame that was on show`() {
        copyOnce()
        val before = frames.frame

        frames.tick()
        source.finish(ok = false)

        assertThat(frames.frame).isSameInstanceAs(before)
    }

    @Test
    fun `the bitmaps are reused, three of them at most`() {
        repeat(20) { copyOnce() }

        assertThat(source.requests.toSet().size).isAtMost(3)
        assertThat(source.requests.toSet().size).isAtLeast(2)
    }

    // Nothing to copy

    @Test
    fun `with no surface a tick asks for nothing and there is no frame`() {
        source.bounds = null

        frames.tick()

        assertThat(source.requests).isEmpty()
        assertThat(frames.frame).isNull()
    }

    @Test
    fun `a copy that ends after the close is not shown, and no more are asked for`() {
        frames.tick()

        frames.close()
        source.finish()
        frames.tick()

        assertThat(frames.frame).isNull()
        assertThat(source.requests).hasSize(1)
    }

    // The log

    private fun lines() = log.records.filter { it.tag == LogTag.VIDEO }

    private fun tickEvery(millis: Int, times: Int) {
        repeat(times) {
            frames.tick()
            nanos += millis * NANOS_PER_MILLI
        }
    }

    @Test
    fun `nothing is logged before a second has passed`() {
        tickEvery(millis = 33, times = 20)

        assertThat(lines()).isEmpty()
    }

    @Test
    fun `a debug line each second says the copies, the skips, the failures and the slowest copy`() {
        // Ticks 33 ms apart: the first copy takes 90 ms, so the next two ticks are skipped; the
        // second fails; the third takes 40 ms.
        frames.tick()
        nanos += 33 * NANOS_PER_MILLI
        frames.tick()
        nanos += 57 * NANOS_PER_MILLI
        source.finish()
        frames.tick()
        nanos += 10 * NANOS_PER_MILLI
        source.finish(ok = false)
        frames.tick()
        nanos += 40 * NANOS_PER_MILLI
        source.finish()
        nanos = ONE_SECOND

        frames.tick()

        val line = lines().single()
        assertThat(line.level).isEqualTo(LogLevel.DEBUG)
        assertThat(line.fields)
            .containsExactly(
                "copies" to "2",
                "skipped" to "1",
                "failed" to "1",
                "no_surface" to "0",
                "slowest_ms" to "90",
            )
            .inOrder()
    }

    @Test
    fun `the next second starts its counts from zero`() {
        copyOnce()
        nanos = ONE_SECOND
        frames.tick()
        source.finish()
        nanos = 2 * ONE_SECOND

        frames.tick()

        assertThat(lines().map { it.fields.toMap()["copies"] }).containsExactly("1", "1").inOrder()
    }

    @Test
    fun `a second with no surface says so`() {
        source.bounds = null
        tickEvery(millis = 100, times = 11)

        assertThat(lines().single().fields.toMap()["no_surface"]).isEqualTo("10")
    }

    @Test
    fun `nothing is logged after the close`() {
        copyOnce()
        frames.close()
        nanos = ONE_SECOND

        frames.tick()

        assertThat(lines()).isEmpty()
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val ONE_SECOND = 1_000 * NANOS_PER_MILLI
    }
}
