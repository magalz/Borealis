package io.github.magalz.borealis.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/**
 * What a panel inside a [GlassHost] draws where the host blurs its window: the glass [tint] (0 to
 * 1) of its surface colour, and the text [colors] that keep their contrast on that glass.
 */
@Immutable class PanelGlass(val tint: Float, val colors: BorealisColors)

/**
 * Opens the overlay of a panel that is in the layer of a [GlassHost], not in a window of its own,
 * as a dialog does (ADR 0057, decision 8; ADR 0061, decision 5): while the caller is composed, the
 * host blurs its background and holds it on its still frame. The blur reaches the background only,
 * so the panel, which is not a window, stays sharp, and so does anything else that the host holds.
 *
 * It answers the [PanelGlass] where the host blurs, which is where the panel draws no scrim and
 * takes the tint, and null where the scrim and the solid panel stay: below API 31, with the glass
 * off, with high contrast, and outside any host.
 */
@Composable
fun rememberPanelGlass(): PanelGlass? {
    val overlays = LocalGlassOverlays.current
    DisposableEffect(overlays) {
        overlays?.enterPanel()
        onDispose { overlays?.leavePanel() }
    }
    val glass = overlays?.glass
    val scene = LocalGlassScene.current
    val colors = LocalBorealisColors.current
    return remember(glass, scene, colors) {
        glass?.let { PanelGlass(it.tint, colors.onGlass(scene.style, scene.spec)) }
    }
}
