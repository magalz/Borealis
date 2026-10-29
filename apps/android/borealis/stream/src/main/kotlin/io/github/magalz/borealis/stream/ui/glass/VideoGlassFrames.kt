package io.github.magalz.borealis.stream.ui.glass

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset

/** A copy of the video, and where it goes in the chrome: its place in pixels and its scale. */
internal class VideoFrame(
    val image: ImageBitmap,
    val left: Float,
    val top: Float,
    val scaleX: Float,
    val scaleY: Float,
)

/** Not made yet. */
@Suppress("UnusedParameter", "UnusedPrivateProperty")
internal class VideoGlassFrames(
    private val source: VideoSource,
    private val origin: () -> IntOffset,
    private val clock: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    var frame: VideoFrame? by mutableStateOf(null)
        private set

    fun tick() = Unit

    fun close() = Unit
}
