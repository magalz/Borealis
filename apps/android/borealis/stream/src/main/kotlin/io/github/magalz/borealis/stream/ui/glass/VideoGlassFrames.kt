package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.core.graphics.createBitmap
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import kotlin.math.max

/** The copy of the video is this many times smaller than the video, on each side. */
internal const val COPY_DOWNSCALE = 4

/** Three bitmaps: the one on show, the one a copy writes, and one that was on show before. */
private const val POOL_SIZE = 3
private const val NONE = -1

private const val NANOS_PER_MILLI = 1_000_000L
private const val REPORT_PERIOD_NANOS = 1_000 * NANOS_PER_MILLI

/** A copy of the video, and where it goes in the chrome: its place in pixels and its scale. */
internal class VideoFrame(
    val image: ImageBitmap,
    val left: Float,
    val top: Float,
    val scaleX: Float,
    val scaleY: Float,
)

/**
 * The copies of the video that the menu glass draws (ADR 0061, decision 1). [tick] asks for a copy
 * of [source], unless one is still in flight, into a bitmap a quarter of the video; the last copy
 * that worked is [frame]. The caller ticks 30 times a second, on the main thread, while the glass
 * is shown, and [close]s this when it is not.
 *
 * A bitmap that a copy is writing is never drawn, and one that was never filled is never drawn: the
 * frame is a bitmap only after its copy has ended well, and the next copy goes to another one of a
 * small pool, so a bitmap that is on show is not written until a newer frame is on show. A failed
 * copy leaves the frame as it was. A copy that ends after [close] is dropped.
 *
 * [origin] is where the chrome is on the screen, so that a frame is placed in the chrome's own
 * pixels. [clock] reads nanoseconds. Each second a debug line says what the second did.
 */
internal class VideoGlassFrames(
    private val source: VideoSource,
    private val origin: () -> IntOffset,
    private val clock: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    /** The last copy that worked, or null until one has. */
    var frame: VideoFrame? by mutableStateOf(null)
        private set

    private class Slot(val bitmap: Bitmap) {
        val image = bitmap.asImageBitmap()
    }

    private val slots = arrayOfNulls<Slot>(POOL_SIZE)
    private var shown = NONE
    private var cursor = NONE
    private var pending = false
    private var closed = false
    private val seconds = Counts()

    /**
     * One tick of the clock: asks for a copy, unless one is pending or there is nothing to copy.
     */
    fun tick() {
        if (closed) return
        val now = clock()
        seconds.reportIfDue(now)
        if (pending) {
            seconds.skipped++
            return
        }
        val bounds = source.bounds()
        if (bounds == null) {
            seconds.noSurface++
            return
        }
        request(bounds, now)
    }

    /** The glass is gone: no more copies are asked for, and one that is in flight is dropped. */
    fun close() {
        closed = true
    }

    private fun request(bounds: VideoBounds, startedAt: Long) {
        val width = (bounds.width / COPY_DOWNSCALE).coerceAtLeast(1)
        val height = (bounds.height / COPY_DOWNSCALE).coerceAtLeast(1)
        val index = nextTarget()
        val slot =
            slots[index]?.takeIf { it.bitmap.width == width && it.bitmap.height == height }
                ?: Slot(createBitmap(width, height)).also {
                    slots[index] = it
                }
        pending = true
        source.copy(slot.bitmap) { ok -> finish(ok, index, slot, bounds, startedAt) }
    }

    /** The slot after the last one used, but never the one on show. */
    private fun nextTarget(): Int {
        cursor = (cursor + 1) % POOL_SIZE
        if (cursor == shown) cursor = (cursor + 1) % POOL_SIZE
        return cursor
    }

    private fun finish(ok: Boolean, index: Int, slot: Slot, bounds: VideoBounds, startedAt: Long) {
        pending = false
        if (closed) return
        seconds.slowestNanos = max(seconds.slowestNanos, clock() - startedAt)
        if (!ok) {
            seconds.failed++
            return
        }
        seconds.copies++
        shown = index
        val at = origin()
        frame =
            VideoFrame(
                slot.image,
                left = (bounds.left - at.x).toFloat(),
                top = (bounds.top - at.y).toFloat(),
                scaleX = bounds.width.toFloat() / slot.bitmap.width,
                scaleY = bounds.height.toFloat() / slot.bitmap.height,
            )
    }

    /** What the ticks and the copies of the second so far did, and the line that says it. */
    private class Counts {
        var copies = 0
        var skipped = 0
        var failed = 0
        var noSurface = 0
        var slowestNanos = 0L
        private var since: Long? = null

        fun reportIfDue(now: Long) {
            val start = since ?: now.also { since = it }
            if (now - start < REPORT_PERIOD_NANOS) return
            BorealisLog.d(
                LogTag.VIDEO,
                "Menu glass copies",
                "copies" to copies,
                "skipped" to skipped,
                "failed" to failed,
                "no_surface" to noSurface,
                "slowest_ms" to slowestNanos / NANOS_PER_MILLI,
            )
            copies = 0
            skipped = 0
            failed = 0
            noSurface = 0
            slowestNanos = 0
            since = now
        }
    }
}
