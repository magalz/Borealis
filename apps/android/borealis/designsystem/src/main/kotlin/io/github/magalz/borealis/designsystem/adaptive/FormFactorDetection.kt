package io.github.magalz.borealis.designsystem.adaptive

import androidx.compose.runtime.Composable
import io.github.magalz.borealis.designsystem.theme.FormFactor

// Stub: the behaviour tests fail against it until the implementation lands.

fun formFactorOf(
    isTvFeature: Boolean,
    uiModeType: Int,
    widthDp: Int,
    touchscreen: Int,
    navigation: Int,
): FormFactor = FormFactor.Phone

@Composable fun rememberFormFactor(): FormFactor = FormFactor.Phone
