package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntSize

/** Where the stream video is on the screen, in pixels. */
internal data class VideoBounds(val left: Int, val top: Int, val width: Int, val height: Int)

/** What the glass copies the video from. */
internal interface VideoSource {
    /** Where the video is, or null when there is nothing to copy: no surface, or an empty one. */
    fun bounds(): VideoBounds?

    /** How big the video is, or null when there is nothing to copy. */
    fun size(): IntSize?

    /**
     * Copies the video into [into], then calls [done] once, on the main thread, with the result.
     */
    fun copy(into: Bitmap, done: (Boolean) -> Unit)
}

/** The video of the stream the chrome is over. Null where there is none: the glass is solid. */
internal val LocalVideoSource = staticCompositionLocalOf<VideoSource?> { null }

/**
 * The copies of the stream [view], which is a `SurfaceView` that the hardware decoder renders to:
 * `PixelCopy` is the only way to read it. A view that is not one, or has no surface or no size, has
 * no video. Create it on the main thread: the copies end there.
 */
internal class PixelCopySource(private val view: View) : VideoSource {
    private val handler = Handler(Looper.getMainLooper())

    override fun size(): IntSize? = null

    override fun bounds(): VideoBounds? {
        val surfaceView = view as? SurfaceView ?: return null
        if (!hasSurface(surfaceView) || surfaceView.width == 0 || surfaceView.height == 0)
            return null
        val at = IntArray(2)
        surfaceView.getLocationOnScreen(at)
        return VideoBounds(at[0], at[1], surfaceView.width, surfaceView.height)
    }

    override fun copy(into: Bitmap, done: (Boolean) -> Unit) {
        val surfaceView = view as? SurfaceView
        if (surfaceView == null || !hasSurface(surfaceView)) {
            done(false)
            return
        }
        try {
            PixelCopy.request(
                surfaceView,
                into,
                { result -> done(result == PixelCopy.SUCCESS) },
                handler,
            )
        } catch (expected: IllegalArgumentException) {
            // The surface went away between the check and the request: this copy failed, and the
            // line of the log that counts the failures says so.
            done(false)
        }
    }

    private fun hasSurface(surfaceView: SurfaceView) = surfaceView.holder.surface?.isValid == true
}
