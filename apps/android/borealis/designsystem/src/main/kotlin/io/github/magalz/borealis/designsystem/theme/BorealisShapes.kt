package io.github.magalz.borealis.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/** The shapes Material's [Shapes] has no slot for. */
@Immutable class BorealisShapes(val cover: Shape, val sheet: Shape, val pill: Shape)

/** Chip, button and input, card; dialogs and menus (extra large) take the sheet radius. */
fun borealisShapes(): Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(BorealisTokens.Radius.Chip),
        small = RoundedCornerShape(BorealisTokens.Radius.Chip),
        medium = RoundedCornerShape(BorealisTokens.Radius.Button),
        large = RoundedCornerShape(BorealisTokens.Radius.Card),
        extraLarge = RoundedCornerShape(BorealisTokens.Radius.Sheet),
    )

fun borealisExtraShapes(): BorealisShapes =
    BorealisShapes(
        cover = RoundedCornerShape(BorealisTokens.Radius.Cover),
        sheet =
            RoundedCornerShape(
                topStart = BorealisTokens.Radius.Sheet,
                topEnd = BorealisTokens.Radius.Sheet,
                bottomEnd = 0.dp,
                bottomStart = 0.dp,
            ),
        pill = RoundedCornerShape(BorealisTokens.Radius.Pill),
    )

val LocalBorealisShapes = staticCompositionLocalOf { borealisExtraShapes() }
