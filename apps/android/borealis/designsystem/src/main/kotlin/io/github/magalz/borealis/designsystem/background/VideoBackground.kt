package io.github.magalz.borealis.designsystem.background

import android.view.SurfaceView
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

/** Every clip is 16:9; the surface is cut from the middle of it to fill any screen. */
private const val VIDEO_ASPECT = 16f / 9f

private const val STILL_FADE_MILLIS = 250

/**
 * The background of a video style: the clip plays on a surface behind the screens, and its still
 * covers the surface until the player has drawn a first frame. With [stillOnly], which is the
 * system without animations, the still is all there is: no player is made and no decoder runs.
 */
@Composable
internal fun VideoBackground(clip: BackgroundClip, stillOnly: Boolean, modifier: Modifier) {
    var frameShown by remember(clip) { mutableStateOf(false) }
    Box(modifier) {
        if (!stillOnly) {
            VideoSurface(
                clip,
                onFirstFrame = { frameShown = true },
                onReleased = { frameShown = false },
            )
        }
        AnimatedVisibility(
            visible = stillOnly || !frameShown,
            enter = EnterTransition.None,
            exit = fadeOut(tween(STILL_FADE_MILLIS)),
        ) {
            Image(
                painterResource(clip.still),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag(BackgroundTags.STILL),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * The surface of a clip, and its player while the screen is on: made when the screen starts,
 * playing while it is resumed, released when it stops, when [clip] changes and when this leaves the
 * composition. There is one player at a time and none while the screen is stopped, which is what a
 * stream does to the Library.
 */
@Composable
private fun VideoSurface(clip: BackgroundClip, onFirstFrame: () -> Unit, onReleased: () -> Unit) {
    val context = LocalContext.current
    val factory = LocalBackgroundPlayerFactory.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val surface =
        remember(context) {
            SurfaceView(context).apply {
                // The picture is decoration: a screen reader has nothing to say about it.
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
    DisposableEffect(lifecycle, factory, clip, surface) {
        var player: BackgroundPlayer? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START ->
                    player = factory.create(context, clip.video, surface, onFirstFrame)
                Lifecycle.Event.ON_RESUME -> player?.play()
                Lifecycle.Event.ON_PAUSE -> player?.pause()
                Lifecycle.Event.ON_STOP -> {
                    player?.release()
                    player = null
                    onReleased()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player?.release()
        }
    }
    AndroidView({ surface }, Modifier.testTag(BackgroundTags.VIDEO).coverAspect(VIDEO_ASPECT))
}

/**
 * Sizes the view to the smallest [aspect] rectangle that covers the incoming bounds and centres it,
 * so the video fills them and what does not fit is cut off at the edges.
 */
private fun Modifier.coverAspect(aspect: Float): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val wide = width >= height * aspect
    val coverWidth = if (wide) width else (height * aspect).roundToInt()
    val coverHeight = if (wide) (width / aspect).roundToInt() else height
    val placeable = measurable.measure(Constraints.fixed(coverWidth, coverHeight))
    layout(width, height) { placeable.place((width - coverWidth) / 2, (height - coverHeight) / 2) }
}
