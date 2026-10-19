package io.github.magalz.borealis.designsystem.background

import android.content.Context
import android.view.SurfaceView

/** Stands in for Media3: records every player the background makes and what it does with it. */
internal class FakeBackgroundPlayers : BackgroundPlayerFactory {
    val created = mutableListOf<FakePlayer>()

    /** The players that are not released: the decoders that are held. */
    val live: List<FakePlayer>
        get() = created.filter { !it.released }

    override fun create(
        context: Context,
        video: Int,
        surface: SurfaceView,
        onFirstFrame: () -> Unit,
    ): BackgroundPlayer = FakePlayer(video, surface, onFirstFrame).also { created += it }

    class FakePlayer(
        val video: Int,
        val surface: SurfaceView,
        private val onFirstFrame: () -> Unit,
    ) : BackgroundPlayer {
        var playing = false
            private set

        var released = false
            private set

        override fun play() {
            check(!released) { "play after release" }
            playing = true
        }

        override fun pause() {
            check(!released) { "pause after release" }
            playing = false
        }

        override fun release() {
            check(!released) { "released twice" }
            released = true
            playing = false
        }

        /** The player draws its first frame on the surface. */
        fun showFirstFrame() = onFirstFrame()
    }
}
