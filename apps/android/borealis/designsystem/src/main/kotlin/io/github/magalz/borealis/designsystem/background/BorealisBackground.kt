package io.github.magalz.borealis.designsystem.background

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Tags of the pieces of a video background, for the tests. */
internal object BackgroundTags {
    const val STILL = "background:still"
    const val VIDEO = "background:video"
}

/** The animated background of the Library: [style] fills the bounds of [modifier]. */
@Composable
fun BorealisBackground(style: BackgroundStyle, modifier: Modifier = Modifier) {
    Box(modifier)
}
