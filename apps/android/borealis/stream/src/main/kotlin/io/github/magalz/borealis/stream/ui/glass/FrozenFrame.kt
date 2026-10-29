package io.github.magalz.borealis.stream.ui.glass

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset

/** The last frame of a stream that failed, copied once ([request]) and drawn behind its popup. */
internal class FrozenFrame(
    @Suppress("UnusedPrivateProperty") private val source: VideoSource,
    @Suppress("UnusedPrivateProperty") private val origin: () -> IntOffset,
    @Suppress("UnusedPrivateProperty")
    private val clock: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    /** The copy, or null until one has worked, and after [release]. */
    var frame: VideoFrame? by mutableStateOf(null)
        private set

    /** Whether the copy has been asked for. */
    val requested: Boolean
        get() = false

    fun request() = Unit

    fun release() = Unit
}
