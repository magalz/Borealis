package io.github.magalz.borealis.designsystem.focus

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

// Stub: the behaviour tests fail against it until the implementation lands.

fun Modifier.onOptionsRequest(onOptions: () -> Unit): Modifier = this

fun Modifier.optionsClickable(onClick: () -> Unit, onOptions: (() -> Unit)? = null): Modifier =
    clickable(onClick = onClick)
