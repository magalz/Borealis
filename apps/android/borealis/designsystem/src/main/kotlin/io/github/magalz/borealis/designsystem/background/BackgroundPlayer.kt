package io.github.magalz.borealis.designsystem.background

import android.content.Context
import android.view.TextureView
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
 * Makes the player of a video style, which draws the raw resource [video] on [texture], looping and
 * muted, and calls [onFirstFrame] when a frame is on the texture and [onError] with the name of the
 * error when it cannot go on. The app plays with Media3; a test stands in for it.
 */
internal fun interface BackgroundPlayerFactory {
    fun create(
        context: Context,
        @RawRes video: Int,
        texture: TextureView,
        onFirstFrame: () -> Unit,
        onError: (reason: String) -> Unit,
    ): BackgroundPlayer
}

internal val LocalBackgroundPlayerFactory =
    staticCompositionLocalOf<BackgroundPlayerFactory> { Media3BackgroundPlayers }
