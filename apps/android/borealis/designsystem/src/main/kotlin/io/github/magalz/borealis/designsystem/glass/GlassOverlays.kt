package io.github.magalz.borealis.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal class GlassOverlays {
    val isOpen: Boolean = false
    val windowBlurred: Boolean = false
}

internal data class OverlayGlass(val tint: Float)

internal val LocalGlassOverlays = staticCompositionLocalOf<GlassOverlays?> { null }

@Composable internal fun OverlayOpen() = Unit

@Composable internal fun rememberOverlayGlass(): OverlayGlass? = null
