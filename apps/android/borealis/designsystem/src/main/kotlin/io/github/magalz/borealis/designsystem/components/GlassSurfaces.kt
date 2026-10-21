package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import io.github.magalz.borealis.designsystem.glass.LocalGlassScene
import io.github.magalz.borealis.designsystem.glass.OverlayGlass
import io.github.magalz.borealis.designsystem.glass.glassSurface
import io.github.magalz.borealis.designsystem.glass.onGlass
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.TextSizeInWindow

/**
 * A glass surface of [shape] for a surface that was not the `surface` colour before the glass: it
 * is the glass, and where the glass is solid it is [solid], as it was.
 */
@Composable
internal fun Modifier.glassOrSolid(shape: Shape, solid: Color): Modifier =
    if (LocalGlassScene.current.spec.solid) background(solid, shape) else glassSurface(shape)

/**
 * The background of the panel of a dialog or a sheet: the `surface` colour at the tint of [glass],
 * which is what a panel over a blurred window is, or the solid colour where [glass] is null.
 */
@Composable
internal fun Modifier.overlayPanel(shape: Shape, glass: OverlayGlass?): Modifier =
    background(LocalBorealisColors.current.surface.copy(alpha = glass?.tint ?: 1f), shape)

/**
 * Gives [content], which is on the panel of a dialog or a sheet, the glass text colours where the
 * window behind it is blurred ([glass] is not null), and the colours as they are where the panel is
 * solid. The tree is the same either way. The panel is a window of its own, so [content] also gets
 * the text size of the theme ([TextSizeInWindow]).
 */
@Composable
internal fun OverlayTextColors(glass: OverlayGlass?, content: @Composable () -> Unit) {
    val scene = LocalGlassScene.current
    val colors = LocalBorealisColors.current
    val shown =
        remember(glass != null, scene, colors) {
            if (glass != null) colors.onGlass(scene.style, scene.spec) else colors
        }
    CompositionLocalProvider(LocalBorealisColors provides shown) { TextSizeInWindow(content) }
}
