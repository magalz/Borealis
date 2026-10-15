package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

/** The text styles Material's [Typography] has no slot for. */
@Immutable class BorealisType(val kicker: TextStyle, val pin: TextStyle, val rail: TextStyle)

/** The Material type scale of a form factor; handheld uses the TV scale. */
fun borealisTypography(formFactor: FormFactor): Typography = TODO("not implemented")

fun borealisType(formFactor: FormFactor): BorealisType = TODO("not implemented")

val LocalBorealisType = staticCompositionLocalOf { borealisType(FormFactor.Phone) }
