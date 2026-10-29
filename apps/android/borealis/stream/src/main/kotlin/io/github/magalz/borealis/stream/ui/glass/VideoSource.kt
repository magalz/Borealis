package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

/** Where the stream video is on the screen, in pixels. */
internal data class VideoBounds(val left: Int, val top: Int, val width: Int, val height: Int)

/** What the glass copies the video from. */
internal interface VideoSource {
    /** Where the video is, or null when there is nothing to copy: no surface, or an empty one. */
    fun bounds(): VideoBounds?

    /**
     * Copies the video into [into], then calls [done] once, on the main thread, with the result.
     */
    fun copy(into: Bitmap, done: (Boolean) -> Unit)
}

/** The video of the stream the chrome is over. Null where there is none: the glass is solid. */
internal val LocalVideoSource = staticCompositionLocalOf<VideoSource?> { null }

/** Not made yet. */
@Suppress("UnusedParameter")
internal class PixelCopySource(view: View) : VideoSource {
    override fun bounds(): VideoBounds? = null

    override fun copy(into: Bitmap, done: (Boolean) -> Unit) = Unit
}
