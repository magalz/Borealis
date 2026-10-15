package io.github.magalz.borealis.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

/** Screen gutter and card padding of a form factor. */
@Immutable class BorealisSpacing(val gutter: Dp, val cardPadding: Dp)

fun borealisSpacing(formFactor: FormFactor): BorealisSpacing = TODO("not implemented")

val LocalBorealisSpacing = staticCompositionLocalOf { borealisSpacing(FormFactor.Phone) }
