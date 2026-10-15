package io.github.magalz.borealis.buildlogic.tokens

import java.math.BigDecimal

/** Validates one token value and renders it as Kotlin; failures name the token path. */
internal object TokenValues {
    private const val MIN_WEIGHT = 1
    private const val MAX_WEIGHT = 1000

    private val COLOR = Regex("#([0-9A-Fa-f]{6})([0-9A-Fa-f]{2})?")
    private val DIMENSION = Regex("(\\d+(?:\\.\\d+)?)(dp|sp)")
    private val WHITESPACE = Regex("\\s+")

    /** `#RRGGBB` or `#RRGGBBAA` as the `AARRGGBB` digits Compose and Android expect. */
    fun argb(dotted: String, value: Any?): String {
        val match = (value as? String)?.let(COLOR::matchEntire)
        requireNotNull(match) { "$dotted: \"$value\" is not a #RRGGBB or #RRGGBBAA colour" }
        val rgb = match.groupValues[1]
        val alpha = match.groupValues[2].ifEmpty { "FF" }
        return (alpha + rgb).uppercase()
    }

    fun dimension(dotted: String, value: Any?): String {
        val match = (value as? String)?.let(DIMENSION::matchEntire)
        requireNotNull(match) { "$dotted: \"$value\" is not a number followed by dp or sp" }
        return "${match.groupValues[1]}.${match.groupValues[2]}"
    }

    fun number(dotted: String, value: Any?): String {
        require(value is Number) { "$dotted: \"$value\" is not a number" }
        val plain = BigDecimal(value.toString()).toPlainString()
        return if ('.' in plain) "${plain}f" else "$plain.0f"
    }

    fun fontWeight(dotted: String, value: Any?): String {
        require(value is Int && value in MIN_WEIGHT..MAX_WEIGHT) {
            "$dotted: \"$value\" is not a font weight between $MIN_WEIGHT and $MAX_WEIGHT"
        }
        return "FontWeight($value)"
    }

    /** A description for a one-line KDoc comment. */
    fun singleLine(text: String) = text.replace("*/", "* /").replace(WHITESPACE, " ")
}
