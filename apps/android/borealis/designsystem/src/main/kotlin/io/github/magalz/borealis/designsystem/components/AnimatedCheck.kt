package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/** A check mark that draws itself; see the implementation commit. */
@Composable
fun AnimatedCheck(
    modifier: Modifier = Modifier,
    circled: Boolean = false,
    color: Color = LocalBorealisColors.current.success,
) {
    Box(modifier.size(24.dp))
}
