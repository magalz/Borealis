package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Stub: the behaviour tests fail against it until the implementation lands.

class EmptyStateAction(val label: String, val onClick: () -> Unit)

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: EmptyStateAction? = null,
) {}
