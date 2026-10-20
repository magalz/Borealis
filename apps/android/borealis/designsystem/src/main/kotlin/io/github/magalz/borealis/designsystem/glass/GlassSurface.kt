package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

@Composable
fun Modifier.glassSurface(shape: Shape): Modifier =
    background(LocalBorealisColors.current.surface, shape)

@Composable
fun GlassTextColors(content: @Composable () -> Unit) {
    content()
}
