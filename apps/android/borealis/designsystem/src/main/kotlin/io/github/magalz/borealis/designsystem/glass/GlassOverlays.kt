package io.github.magalz.borealis.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What a dialog or a sheet does to the window it opens over, where the window is blurred: it draws
 * no scrim, and its panel takes the glass [tint] (0 to 1) of the surface colour.
 */
internal data class OverlayGlass(val tint: Float)

/**
 * The dialogs, sheets and panels that are open over a [GlassHost], and what that does to it.
 * [glass] is what an overlay over a blurred window does; it is null where the window is not
 * blurred, which is where the glass is solid and where the device cannot blur.
 *
 * A dialog or a sheet is a window of its own, over the host: it blurs all that the host holds. A
 * panel is inside the host (see [rememberPanelGlass]), so it blurs the background alone.
 */
@Stable
internal class GlassOverlays(private val glassState: State<OverlayGlass?>) {
    private val windows = mutableIntStateOf(0)
    private val panels = mutableIntStateOf(0)

    val glass: OverlayGlass?
        get() = glassState.value

    /** Whether any overlay is open. */
    val isOpen: Boolean
        get() = windows.intValue + panels.intValue > 0

    /** Whether the window is blurred, and the background held still, by a dialog or a sheet. */
    val windowBlurred: Boolean
        get() = windows.intValue > 0 && glass != null

    /** Whether the background alone is blurred, and held still, by a panel. */
    val backgroundBlurred: Boolean
        get() = panels.intValue > 0 && glass != null

    /** Whether the background is held still now, by whichever overlay blurs it. */
    val backgroundHeld: Boolean
        get() = windowBlurred || backgroundBlurred

    fun enter() {
        windows.intValue++
    }

    fun leave() {
        windows.intValue--
    }

    fun enterPanel() {
        panels.intValue++
    }

    fun leavePanel() {
        panels.intValue--
    }
}

internal val LocalGlassOverlays = staticCompositionLocalOf<GlassOverlays?> { null }

/** Counts the dialog or sheet that calls this as open for as long as it is in the composition. */
@Composable
internal fun OverlayOpen() {
    val overlays = LocalGlassOverlays.current
    DisposableEffect(overlays) {
        overlays?.enter()
        onDispose { overlays?.leave() }
    }
}

/**
 * What an overlay that is being composed does about the window behind it: the [OverlayGlass] where
 * the host blurs the window, null where the scrim and the solid panel stay. A dialog drops its
 * scrim and tints its panel when this is not null.
 */
@Composable internal fun rememberOverlayGlass(): OverlayGlass? = LocalGlassOverlays.current?.glass
