package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.max

/**
 * A video that a test copies from: a real `PixelCopy` needs a surface and a GPU, which Robolectric
 * has not. A copy ends when the test says so ([finish]), or at once ([completing]); when it works,
 * it paints [frameColor] over the whole bitmap (or what [paint] draws), as the video would fill it,
 * and not before, so a bitmap that a copy has not finished is a bitmap that was never filled.
 */
internal class FakeVideoSource(
    var bounds: VideoBounds? = VIDEO,
    var frameColor: Int = android.graphics.Color.WHITE,
    var completing: Boolean = false,
    var paint: ((Bitmap) -> Unit)? = null,
) : VideoSource {
    /** The bitmap of every copy asked for, in order. */
    val requests = mutableListOf<Bitmap>()

    /** The bitmaps that a copy has finished filling. */
    val filled = mutableSetOf<Bitmap>()

    /** The most copies that were in flight at once. */
    var mostInFlight = 0
        private set

    private val pending = ArrayDeque<(Boolean) -> Unit>()
    private var inFlight = 0

    /** The bitmaps that a copy is writing now. */
    val beingWritten: List<Bitmap>
        get() = requests.takeLast(inFlight)

    /** The threads that asked where the video is, in order. */
    val boundsThreads = mutableListOf<Thread>()

    override fun size(): IntSize? = bounds?.let { IntSize(it.width, it.height) }

    override fun bounds(): VideoBounds? {
        boundsThreads += Thread.currentThread()
        return bounds
    }

    override fun copy(into: Bitmap, done: (Boolean) -> Unit) {
        requests += into
        inFlight++
        mostInFlight = max(mostInFlight, inFlight)
        val end = { ok: Boolean ->
            if (ok) {
                paint?.invoke(into) ?: into.eraseColor(frameColor)
                filled += into
            }
            inFlight--
            done(ok)
        }
        if (completing) end(true) else pending += end
    }

    /** The oldest copy that has not ended ends, well or badly. */
    fun finish(ok: Boolean = true) {
        assertWithMessage("a copy was asked for").that(pending).isNotEmpty()
        pending.removeFirst()(ok)
    }

    val pendingCount: Int
        get() = pending.size

    companion object {
        /** A 720p stream on a screen of the same size. */
        val VIDEO = VideoBounds(left = 0, top = 0, width = 1280, height = 720)
    }
}
