package io.github.magalz.borealis.designsystem.adaptive

import androidx.compose.runtime.Composable

internal fun isGameController(sources: Int, isVirtual: Boolean, hasJoystickXy: Boolean): Boolean =
    false

fun isGamepadConnected(): Boolean = false

@Composable internal fun rememberGamepadConnected(): Boolean = false
