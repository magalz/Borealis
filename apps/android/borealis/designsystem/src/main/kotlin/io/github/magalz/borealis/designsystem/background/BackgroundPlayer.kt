package io.github.magalz.borealis.designsystem.background

import android.content.Context
import android.view.SurfaceView
import androidx.annotation.RawRes
import androidx.compose.runtime.staticCompositionLocalOf

/** What the background asks of the player of a video style. */
internal interface BackgroundPlayer {
    fun play()

    fun pause()

    /** Frees the player and its decoder; it is not used again. */
    fun release()
}

/**
 * Makes the player of a video style, which draws the raw resource [video] on [surface], looping and
 * muted, and calls [onFirstFrame] when a frame is on the surface. The app plays with Media3; a test
 * stands in for it.
 */
internal fun interface BackgroundPlayerFactory {
    fun create(
        context: Context,
        @RawRes video: Int,
        surface: SurfaceView,
        onFirstFrame: () -> Unit,
    ): BackgroundPlayer
}

internal val LocalBackgroundPlayerFactory =
    staticCompositionLocalOf<BackgroundPlayerFactory> {
        error("No background player: Media3 comes with the implementation")
    }
