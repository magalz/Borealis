package io.github.magalz.borealis.stream.ui.glass

import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.magalz.borealis.designsystem.components.LocalTextHalo
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.rememberAnimationsRemoved
import io.github.magalz.borealis.designsystem.theme.rememberPowerSaveMode
import kotlinx.coroutines.delay

/** The copies of the video are asked for 30 times a second (ADR 0061, decision 1). */
private const val COPY_PERIOD_MS = 33L
private const val PERCENT = 100f

/** The menu glass that the panel and the popup under it draw, or null where they are solid. */
internal val LocalVideoGlass = staticCompositionLocalOf<VideoGlass?> { null }

/**
 * The live glass behind the in-game menu and its Quit popup: the copies of the stream video that
 * [frames] makes, a blurred copy of the last of them in [small], and [spec], what to draw it with.
 * The panels draw their region of [small] ([drawBackdrop]) once the host of the glass has recorded
 * it ([recordBackdrop]); until a first copy has arrived they are the solid surface.
 */
internal class VideoGlass(
    val spec: VideoGlassSpec,
    private val frames: VideoGlassFrames,
    private val blurred: GraphicsLayer,
    private val small: GraphicsLayer,
) {
    private var sigma = Float.NaN

    /**
     * Records the last copy into [small], blurred. The copy is a fourth of the video, so it is
     * blurred at that size, as the glass of the Library blurs its background, and drawn back up. A
     * blur effect applies at the resolution of the layer it is drawn into: that is why [small] is
     * rendered offscreen at the size of the copy.
     */
    fun recordBackdrop(scope: DrawScope) {
        with(scope) {
            val frame = frames.frame ?: return@with
            val next = spec.blurDp.dp.toPx() / frame.scaleX
            if (next != sigma) {
                sigma = next
                blurred.renderEffect =
                    if (next > 0f) BlurEffect(next, next, TileMode.Clamp) else null
            }
            val size = IntSize(frame.image.width, frame.image.height)
            blurred.record(size = size) { drawImage(frame.image) }
            small.compositingStrategy = CompositingStrategy.Offscreen
            small.record(size = size) { drawLayer(blurred) }
        }
    }

    /**
     * Draws what is behind a panel whose corner is at [position] in the chrome: the [surface]
     * colour while there is no copy; else the blurred copy of the video where the video is, black
     * where it is not, and the tint of the surface over it.
     */
    fun drawBackdrop(scope: DrawScope, position: Offset, surface: Color) {
        with(scope) {
            val frame = frames.frame
            if (frame == null) {
                drawRect(surface)
                return@with
            }
            drawRect(Color.Black)
            translate(frame.left - position.x, frame.top - position.y) {
                scale(frame.scaleX, frame.scaleY, Offset.Zero) { drawLayer(small) }
            }
            drawRect(surface.copy(alpha = spec.tintPercent / PERCENT))
        }
    }
}

/**
 * The glass for the menu that is composed now, or null where the surface is solid: the option is
 * off, there is no stream view to copy ([LocalVideoSource]), or the rules of [videoGlassSpec] say
 * so (the battery saver is followed live). The copies run for as long as this is non-null, and only
 * while the lifecycle is started: they stop when the menu leaves the composition, when the activity
 * stops and in picture-in-picture, where the chrome is not composed.
 */
@Composable
internal fun rememberVideoGlass(enabled: Boolean): VideoGlass? {
    val source = LocalVideoSource.current
    val look = LocalBorealisLook.current
    val colors = LocalBorealisColors.current
    // Listens only where there can be a glass: the Solid option, no video, and an API below 31
    // never register a receiver.
    val batterySaver =
        if (enabled && source != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rememberPowerSaveMode()
        } else {
            false
        }
    val animationsRemoved = rememberAnimationsRemoved()
    val spec =
        remember(look, colors, batterySaver, animationsRemoved) {
            videoGlassSpec(look, colors, Build.VERSION.SDK_INT, batterySaver, animationsRemoved)
        }
    if (!enabled || source == null || spec == null) return null
    return rememberRunningGlass(source, spec)
}

@Composable
private fun rememberRunningGlass(source: VideoSource, spec: VideoGlassSpec): VideoGlass {
    val view = LocalView.current
    val frames = remember(source, view) { VideoGlassFrames(source, { view.originOnScreen() }) }
    DisposableEffect(frames) { onDispose(frames::close) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(frames, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                frames.tick()
                delay(COPY_PERIOD_MS)
            }
        }
    }
    val blurred = rememberGraphicsLayer()
    val small = rememberGraphicsLayer()
    return remember(spec, frames, blurred, small) { VideoGlass(spec, frames, blurred, small) }
}

private fun View.originOnScreen(): IntOffset {
    val at = IntArray(2)
    getLocationOnScreen(at)
    return IntOffset(at[0], at[1])
}

/** Records the blurred copy of the video that the panels inside this draw their regions of. */
internal fun Modifier.recordsVideoBackdrop(glass: VideoGlass?): Modifier =
    if (glass == null) this else drawBehind { glass.recordBackdrop(this) }

/**
 * The surface of a panel of [shape] over the [glass]: the region of the blurred video behind it
 * under the tint, or the solid surface colour until a copy has arrived. It reads where the panel is
 * in the chrome, which is where the video is behind it.
 */
@Composable
internal fun Modifier.videoGlassSurface(glass: VideoGlass, shape: Shape): Modifier {
    val surface = LocalBorealisColors.current.surface
    var position by remember { mutableStateOf(Offset.Zero) }
    return clip(shape)
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawBehind { glass.drawBackdrop(this, position, surface) }
}

/**
 * What is drawn on the [glass]: the text colours of the glass, and the halo of its text. Where
 * [glass] is null it is the colours of the look and no halo, so the content is the same composition
 * with and without the glass, and a change of the battery saver does not compose it again.
 */
@Composable
internal fun OnVideoGlass(glass: VideoGlass?, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBorealisColors provides (glass?.spec?.colors ?: LocalBorealisColors.current),
        LocalTextHalo provides (if (glass == null) null else VIDEO_GLASS_HALO),
        content = content,
    )
}

/**
 * A control that sits on the `surface` colour whatever is behind the menu, as a segmented control
 * does: [content] gets the modifier that puts the surface behind it, in the shape of the control,
 * and draws in the colours of the look with no halo, so that its labels never stand on the video.
 * Without a glass the surface is already solid, so the modifier is empty.
 */
@Composable
internal fun OnSolidSurface(content: @Composable (Modifier) -> Unit) {
    val glass = LocalVideoGlass.current
    val colors = glass?.spec?.surfaceColors ?: LocalBorealisColors.current
    val container =
        if (glass == null) Modifier
        else Modifier.background(colors.surface, MaterialTheme.shapes.medium)
    CompositionLocalProvider(
        LocalBorealisColors provides colors,
        LocalTextHalo provides null,
    ) {
        content(container)
    }
}
