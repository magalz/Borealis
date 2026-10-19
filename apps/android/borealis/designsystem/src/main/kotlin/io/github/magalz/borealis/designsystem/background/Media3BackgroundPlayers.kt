package io.github.magalz.borealis.designsystem.background

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.annotation.RawRes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * The player of the video styles: Media3 ExoPlayer, muted, looping the one clip and drawing it on
 * the surface. A clip has no sound, so its audio track is switched off and no audio output opens.
 */
internal object Media3BackgroundPlayers : BackgroundPlayerFactory {
    override fun create(
        context: Context,
        video: Int,
        surface: SurfaceView,
        onFirstFrame: () -> Unit,
    ): BackgroundPlayer {
        val app = context.applicationContext
        val player = ExoPlayer.Builder(app).build()
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.volume = 0f
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        player.addListener(
            object : Player.Listener {
                override fun onRenderedFirstFrame() = onFirstFrame()
            }
        )
        player.setVideoSurfaceView(surface)
        player.setMediaItem(MediaItem.fromUri(rawResourceUri(app, video)))
        player.prepare()
        return object : BackgroundPlayer {
            override fun play() = player.play()

            override fun pause() = player.pause()

            override fun release() = player.release()
        }
    }
}

/** `android.resource://<package>/<id>`: the form that Media3 reads from the raw resources. */
private fun rawResourceUri(context: Context, @RawRes id: Int): Uri =
    Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(id.toString())
        .build()
