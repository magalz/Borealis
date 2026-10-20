package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.background.BorealisBackground

@Composable
fun GlassHost(
    backgroundStyle: BackgroundStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    GlassHostOver(
        backgroundStyle,
        { BorealisBackground(backgroundStyle, Modifier.fillMaxSize()) },
        modifier,
        content,
    )
}

@Composable
internal fun GlassHostOver(
    style: BackgroundStyle,
    background: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        background()
        content()
    }
}
