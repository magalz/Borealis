package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Stub: the behaviour tests fail against it until the implementation lands.

enum class DotStatus {
    Online,
    Offline,
    Unknown,
}

@Composable
fun StatusDot(status: DotStatus, contentDescription: String?, modifier: Modifier = Modifier) {}
