package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Stub: the behaviour tests fail against it until the implementation lands.

enum class BorealisButtonStyle {
    Primary,
    Secondary,
    Danger,
    Text,
}

@Composable
fun BorealisButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BorealisButtonStyle = BorealisButtonStyle.Primary,
    enabled: Boolean = true,
) {}
