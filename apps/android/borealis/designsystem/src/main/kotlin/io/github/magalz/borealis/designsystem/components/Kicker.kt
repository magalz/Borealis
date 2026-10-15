package io.github.magalz.borealis.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType

/** A section label: capitals in the kicker style (small, spaced, ink-4). */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = LocalBorealisType.current.kicker)
}
