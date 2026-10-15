package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Stub: the behaviour tests fail against it until the implementation lands.

class HostCardAction(val label: String, val onClick: () -> Unit)

@Composable
fun HostCard(
    name: String,
    status: DotStatus,
    statusText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    runningApp: String? = null,
    action: HostCardAction? = null,
    onOptions: (() -> Unit)? = null,
) {}
