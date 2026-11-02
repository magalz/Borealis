package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.core.graphics.createBitmap
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.util.concurrent.atomic.AtomicBoolean

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * The last frame of a stream that failed, which the popup of the error shows frozen behind it (the
 * owner's decision of 2026-10-29). [request] copies [source] once, a quarter of the video like the
 * glass of the menu, and the copy that worked is [frame]; a failed copy is no frame, and the popup
 * sits on the background of the screens outside the game instead.
 *
 * The decoder is released a fraction of a second after the connection reports that the stream
 * ended, and a copy after that gets no data: the activity asks for the copy at once, from the
 * thread that reports the end, which is not the main thread. So [request] may be called from any
 * thread. It reads how big the video is, which asks nothing about the place of a view; the copy
 * ends on the main thread ([VideoSource.copy] says so, also for a copy that cannot be made), and
 * there the place of the video is read and the copy becomes the [frame].
 *
 * The place of the frame is not fixed at the copy: [frame] reads where the video and the chrome
 * ([origin]) are now, whenever it is read, and the popup reads it as it draws. A rotation, a
 * split-screen resize or the end of picture-in-picture moves the video after the failure, and the
 * frame goes with it. A video that has no bounds for a moment (its surface is being made again)
 * leaves the frame at the last place that it had.
 *
 * Between the request and the end of the copy it is [pending], and the popup keeps the background
 * off the screen meanwhile. That relies on the source ending every copy that [request] asked for,
 * well or badly: `PixelCopy` answers each request it accepts on its handler, and [PixelCopySource]
 * answers one that it refuses. A copy that never ended would leave the popup on the black of the
 * frame, under its scrim, where it stays readable.
 *
 * Only the first [request] copies. [release] lets go of the frame, once the activity is ending and
 * nothing draws it any more, and a copy that ends after it is dropped.
 */
internal class FrozenFrame(
    private val source: VideoSource,
    private val origin: () -> IntOffset,
    private val clock: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    /** A copy that worked: the picture, and where the video was when it was last seen. */
    private class Copy(val image: ImageBitmap, var bounds: VideoBounds)

    private var copy: Copy? by mutableStateOf(null)

    /**
     * The copy, placed where the video is now, or null until one has worked, and after [release].
     * Read it where it is drawn: each read asks [source] and [origin] again, on the main thread.
     */
    val frame: VideoFrame?
        get() = copy?.let(::placed)

    /** Whether there is a [frame], which asks nothing of the views. */
    val hasFrame: Boolean
        get() = copy != null

    /** Whether the copy was asked for and has neither ended with a frame nor failed yet. */
    var pending: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether the frame, or the black that it is copied over, stands behind the popup in place of
     * the background of the screens outside the game: the frame is there, or the copy is pending.
     * Then the popup is over the game. A failed copy, or none asked for, leaves the background.
     */
    val coversBackground: Boolean
        get() = hasFrame || pending

    private val asked = AtomicBoolean(false)
    @Volatile private var released = false

    /** Whether the copy has been asked for. */
    val requested: Boolean
        get() = asked.get()

    /** Asks for the copy of the video, unless it was asked for already or [release] came. */
    fun request() {
        if (released || !asked.compareAndSet(false, true)) return
        val size = source.size()
        if (size == null) {
            BorealisLog.w(LogTag.VIDEO, "Frozen frame failed", "reason" to "no_surface")
            return
        }
        val bitmap =
            createBitmap(
                (size.width / COPY_DOWNSCALE).coerceAtLeast(1),
                (size.height / COPY_DOWNSCALE).coerceAtLeast(1),
            )
        BorealisLog.i(
            LogTag.VIDEO,
            "Frozen frame requested",
            "width" to bitmap.width,
            "height" to bitmap.height,
        )
        val startedAt = clock()
        pending = true
        source.copy(bitmap) { ok -> finish(ok, bitmap, startedAt) }
    }

    private fun finish(ok: Boolean, bitmap: Bitmap, startedAt: Long) {
        pending = false
        if (released) return
        if (!ok) {
            BorealisLog.w(LogTag.VIDEO, "Frozen frame failed", "reason" to "copy")
            return
        }
        // On the main thread, where the views are laid out.
        val bounds = source.bounds()
        if (bounds == null) {
            BorealisLog.w(LogTag.VIDEO, "Frozen frame failed", "reason" to "no_surface")
            return
        }
        BorealisLog.i(
            LogTag.VIDEO,
            "Frozen frame copied",
            "ms" to (clock() - startedAt) / NANOS_PER_MILLI,
        )
        copy = Copy(bitmap.asImageBitmap(), bounds)
    }

    // The copy where the video and the chrome are now; the video's last bounds stand in for a
    // surface that has none.
    private fun placed(copy: Copy): VideoFrame {
        source.bounds()?.let { copy.bounds = it }
        val bounds = copy.bounds
        val chrome = origin()
        return VideoFrame(
            copy.image,
            left = (bounds.left - chrome.x).toFloat(),
            top = (bounds.top - chrome.y).toFloat(),
            scaleX = bounds.width.toFloat() / copy.image.width,
            scaleY = bounds.height.toFloat() / copy.image.height,
        )
    }

    /** Lets go of the frame: the activity is ending, and nothing draws it any more. */
    fun release() {
        released = true
        pending = false
        copy = null
    }
}

/** The frozen frame behind the error popup. Null where there is no stream to have copied. */
internal val LocalFrozenFrame = staticCompositionLocalOf<FrozenFrame?> { null }
