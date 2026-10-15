package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Stub: the behaviour tests fail against it until the implementation lands.

internal fun initialsOf(name: String): String = ""

@Composable
fun CoverTile(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    runningLabel: String? = null,
    hidden: Boolean = false,
    onOptions: (() -> Unit)? = null,
) {}
