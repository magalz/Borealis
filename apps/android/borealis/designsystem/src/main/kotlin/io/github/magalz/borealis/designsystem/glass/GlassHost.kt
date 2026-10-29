package io.github.magalz.borealis.designsystem.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.background.BorealisBackground
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import kotlin.math.roundToInt

/** The backdrop is blurred at a fourth of the size of the screen, and drawn back up. */
internal const val BACKDROP_DOWNSCALE = 4

/** The blur of the whole window while a dialog or a sheet is open over it, in dp. */
private const val WINDOW_BLUR_DP = 24

/**
 * What the glass surfaces under a [GlassHost] draw from: how they look ([spec]) over which
 * background ([style]), and, where there is a blur to draw, the one [backdrop] that every surface
 * takes its region of.
 */
internal class GlassScene(
    val spec: GlassSpec,
    val style: BackgroundStyle,
    val backdrop: GraphicsLayer? = null,
)

/** A surface outside any host is solid. */
internal val LocalGlassScene = staticCompositionLocalOf {
    GlassScene(GlassSpec.Solid, BackgroundStyle.Off)
}

/**
 * The frame of a window with glass surfaces (ADR 0057): it draws the animated background of [style]
 * behind [content] and lets the surfaces in [content] ([glassSurface]) show it through. It works in
 * any Compose root, as long as the theme provides the look ([LocalBorealisLook]).
 *
 * The background is recorded into one layer. A copy at a fourth of the size is blurred once per
 * frame, and each surface draws its own region of that copy, scaled up, with its tint over it.
 * Where the glass is solid, or the device cannot blur (below API 31), there is no copy and a
 * surface is its tint, or its solid colour. The host fills the window, from its corner: a surface
 * takes its position in the root as its position in the host.
 *
 * While a dialog or a sheet is open over the host ([OverlayOpen]) and the device can blur, the host
 * blurs all of its content and holds the background still: no frame of it is drawn while the sheet
 * is up. A drawn background runs at 24 frames a second on a TV while the blur is drawn.
 */
@Composable
fun GlassHost(
    backgroundStyle: BackgroundStyle,
    modifier: Modifier = Modifier,
    frozen: Boolean = false,
    clipsPlay: Boolean = true,
    content: @Composable () -> Unit,
) {
    GlassHostOver(backgroundStyle, { HostBackground(backgroundStyle) }, modifier, content)
}

/** The background of the host: that of its style, held still while the window is blurred. */
@Composable
private fun HostBackground(style: BackgroundStyle) {
    val scene = LocalGlassScene.current
    // The state is read here, so that opening a sheet recomposes the background and nothing else.
    val frozen = LocalGlassOverlays.current?.windowBlurred == true
    BorealisBackground(
        style,
        Modifier.fillMaxSize(),
        frozen = frozen,
        framesPerSecond = glassFrameRate(scene.spec, LocalFormFactor.current),
    )
}

/**
 * The host over any [background], which a test makes plain.
 *
 * The tree of the host is the same whatever the glass is: the background in a box of its own, then
 * [content], and the three layers whether they are used or not. Only modifiers change when the look
 * does, because a content that moved in the tree would be composed again and lose its state: the
 * Library keeps its navigation in the host, and the look changes while the screen is on.
 */
@Composable
internal fun GlassHostOver(
    style: BackgroundStyle,
    background: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val look = LocalBorealisLook.current
    val sdk = Build.VERSION.SDK_INT
    val spec = remember(look, style, sdk) { glassSpec(look, style, sdk) }
    val overlayGlass =
        if (!spec.solid && sdk >= Build.VERSION_CODES.S) OverlayGlass(spec.tint) else null
    val glass = rememberUpdatedState(overlayGlass)
    val overlays = remember { GlassOverlays(glass) }
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val small = rememberGraphicsLayer()
    val scene =
        remember(spec, style, small) {
            GlassScene(spec, style, backdrop = small.takeIf { spec.blurDp > 0 })
        }
    val radiusPx = with(LocalDensity.current) { spec.blurDp.dp.toPx() }
    val blur = remember(radiusPx) { backdropBlur(radiusPx) }
    CompositionLocalProvider(LocalGlassOverlays provides overlays, LocalGlassScene provides scene) {
        Box(modifier.fillMaxSize().windowBlur(overlays)) {
            Box(Modifier.fillMaxSize().recordBackdrop(scene, source, blurred, blur)) {
                background()
            }
            content()
        }
    }
}

/**
 * The blur of the copy at a fourth of the size. The radius shrinks with the size, so the blur
 * reaches as far as it would at full size.
 */
private fun backdropBlur(radiusPx: Float): BlurEffect {
    val sigma = radiusPx / BACKDROP_DOWNSCALE
    return BlurEffect(sigma, sigma, TileMode.Clamp)
}

/**
 * Where the glass blurs, records what this draws, which is the background, into [source] and draws
 * it from there, and makes the blurred copy at a fourth of the size that the surfaces take their
 * regions of. Where it does not, this draws as it is.
 */
private fun Modifier.recordBackdrop(
    scene: GlassScene,
    source: GraphicsLayer,
    blurred: GraphicsLayer,
    blur: BlurEffect,
): Modifier {
    val small = scene.backdrop ?: return this
    return drawWithContent {
        source.record { this@drawWithContent.drawContent() }
        blurBackdrop(source, blurred, small, blur)
        drawLayer(source)
    }
}

/**
 * Records the blurred copy of [source] into [small]. [blurred] holds [source] at a fourth of the
 * size with [blur] on it, and the effect is applied when its layer is drawn, at the resolution of
 * the layer it is drawn into: that is why [small] is rendered offscreen at that size.
 */
private fun DrawScope.blurBackdrop(
    source: GraphicsLayer,
    blurred: GraphicsLayer,
    small: GraphicsLayer,
    blur: BlurEffect,
) {
    val reduced =
        IntSize(
            (size.width / BACKDROP_DOWNSCALE).roundToInt().coerceAtLeast(1),
            (size.height / BACKDROP_DOWNSCALE).roundToInt().coerceAtLeast(1),
        )
    blurred.renderEffect = blur
    blurred.record(size = reduced) {
        scale(1f / BACKDROP_DOWNSCALE, Offset.Zero) { drawLayer(source) }
    }
    small.compositingStrategy = CompositingStrategy.Offscreen
    small.record(size = reduced) { drawLayer(blurred) }
}

/**
 * Blurs all that this holds while an overlay is open over a device that can blur. The effect is set
 * in the layer, which reads the state of the counter: an overlay that opens redraws the layer and
 * recomposes nothing.
 */
private fun Modifier.windowBlur(overlays: GlassOverlays): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        this
    } else {
        graphicsLayer {
            val radius = WINDOW_BLUR_DP.dp.toPx()
            renderEffect =
                if (overlays.windowBlurred) BlurEffect(radius, radius, TileMode.Clamp) else null
        }
    }
