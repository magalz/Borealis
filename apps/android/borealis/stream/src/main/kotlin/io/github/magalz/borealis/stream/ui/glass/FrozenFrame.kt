package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
 * thread. It reads where the video is (its size and place, which do not change while the stream
 * ends), and the copy ends on the main thread, where [frame] is set. [origin] is where the chrome
 * is on the screen, read then.
 *
 * Only the first [request] copies. [release] lets go of the frame, once the activity is ending and
 * nothing draws it any more, and a copy that ends after it is dropped.
 */
internal class FrozenFrame(
    private val source: VideoSource,
    private val origin: () -> IntOffset,
    private val clock: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    /** The copy, or null until one has worked, and after [release]. */
    var frame: VideoFrame? by mutableStateOf(null)
        private set

    private val asked = AtomicBoolean(false)
    @Volatile private var released = false

    /** Whether the copy has been asked for. */
    val requested: Boolean
        get() = asked.get()

    /** Asks for the copy of the video, unless it was asked for already or [release] came. */
    fun request() {
        if (released || !asked.compareAndSet(false, true)) return
        val bounds = source.bounds()
        if (bounds == null) {
            BorealisLog.w(LogTag.VIDEO, "Frozen frame failed", "reason" to "no_surface")
            return
        }
        val bitmap =
            createBitmap(
                (bounds.width / COPY_DOWNSCALE).coerceAtLeast(1),
                (bounds.height / COPY_DOWNSCALE).coerceAtLeast(1),
            )
        BorealisLog.i(
            LogTag.VIDEO,
            "Frozen frame requested",
            "width" to bitmap.width,
            "height" to bitmap.height,
        )
        val startedAt = clock()
        source.copy(bitmap) { ok -> finish(ok, bitmap, bounds, startedAt) }
    }

    private fun finish(
        ok: Boolean,
        bitmap: Bitmap,
        bounds: VideoBounds,
        at: Long,
    ) {
        if (released) return
        if (!ok) {
            BorealisLog.w(LogTag.VIDEO, "Frozen frame failed", "reason" to "copy")
            return
        }
        BorealisLog.i(LogTag.VIDEO, "Frozen frame copied", "ms" to (clock() - at) / NANOS_PER_MILLI)
        val chrome = origin()
        frame =
            VideoFrame(
                bitmap.asImageBitmap(),
                left = (bounds.left - chrome.x).toFloat(),
                top = (bounds.top - chrome.y).toFloat(),
                scaleX = bounds.width.toFloat() / bitmap.width,
                scaleY = bounds.height.toFloat() / bitmap.height,
            )
    }

    /** Lets go of the frame: the activity is ending, and nothing draws it any more. */
    fun release() {
        released = true
        frame = null
    }
}

/** The frozen frame behind the error popup. Null where there is no stream to have copied. */
internal val LocalFrozenFrame = staticCompositionLocalOf<FrozenFrame?> { null }
