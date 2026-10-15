package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape

/** The shapes Material's [Shapes] has no slot for. */
@Immutable class BorealisShapes(val cover: Shape, val sheet: Shape, val pill: Shape)

fun borealisShapes(): Shapes = TODO("not implemented")

fun borealisExtraShapes(): BorealisShapes = TODO("not implemented")

val LocalBorealisShapes = staticCompositionLocalOf { borealisExtraShapes() }
