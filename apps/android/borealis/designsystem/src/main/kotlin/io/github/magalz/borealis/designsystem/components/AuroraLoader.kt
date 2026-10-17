package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Borealis loading indicator; see the implementation commit. */
@Composable
fun AuroraLoader(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(modifier.size(size, size * 82 / 108))
}
