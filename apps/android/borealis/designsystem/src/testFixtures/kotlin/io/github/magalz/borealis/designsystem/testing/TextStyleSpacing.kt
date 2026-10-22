package io.github.magalz.borealis.designsystem.testing

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified

/** [value] in em of this style's font size, or `null` when it has none. */
private fun TextStyle.inEm(value: TextUnit): Float? =
    when {
        !value.isSpecified -> null
        value.type == TextUnitType.Em -> value.value
        fontSize.isSpecified -> value.value / fontSize.value
        else -> null
    }

/** The letter spacing of this style in em of its font size: 0 when it has none. */
fun TextStyle.letterSpacingEm(): Float = inEm(letterSpacing) ?: 0f

/** The line height of this style in em of its font size, or `null` when it has none. */
fun TextStyle.lineHeightEm(): Float? = inEm(lineHeight)
