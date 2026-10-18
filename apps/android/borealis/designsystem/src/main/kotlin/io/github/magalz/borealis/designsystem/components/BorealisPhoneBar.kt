package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stub for the red test commit: draws nothing. */
@Suppress("UnusedParameter")
@Composable
fun BorealisPhoneBar(
    onBack: () -> Unit,
    backDescription: String,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit,
) = Unit
