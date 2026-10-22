package io.github.magalz.borealis.designsystem.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified
import com.google.common.truth.Truth.assertWithMessage

/**
 * The texts on the screen that do not fit where they are: one whose line runs past the width it was
 * given (a word that cannot break, or a line that does not wrap), one that is cut off at the
 * bottom, or one that ends in an ellipsis. A line that the text wraps is not cut, so it is not
 * listed. Each entry names the text and what is wrong with it.
 *
 * It reads the text layouts that the tree gives, so it finds every
 * [androidx.compose.material3.Text] and [androidx.compose.foundation.text.BasicText]. The
 * `didOverflowWidth` of a layout is not used: it is true for any text that is narrower than the
 * width it was measured in, and a line of a text aligned to the end is measured past the node.
 */
fun ComposeTestRule.clippedTexts(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), true)
        .fetchSemanticsNodes()
        .mapNotNull { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: return@mapNotNull null
            layout
                .fitProblems()
                .takeIf { it.isNotEmpty() }
                ?.let { "\"${layout.layoutInput.text.text}\" ${it.joinToString()}" }
        }

/**
 * What is wrong with how this layout fits the room it was given: the same four things as
 * [clippedTexts], one entry for each that applies, and none when the text fits.
 */
fun TextLayoutResult.fitProblems(): List<String> {
    val lines = 0 until lineCount
    return buildList {
        if (lines.any { visibleRight(it) > maxWidth(this@fitProblems) + HALF_PIXEL }) {
            add("runs past its width")
        }
        if (didOverflowHeight) add("is cut off at the bottom")
        if (lines.any { isLineEllipsized(it) }) add("ends in an ellipsis")
        if (multiParagraph.didExceedMaxLines) add("has more lines than it may show")
    }
}

private const val HALF_PIXEL = 0.5f

/**
 * The right edge of [line], less what Android lets a spaced line end past the width. It breaks a
 * line at a space and lets that space hang past the width, wrapping the line as if the extra
 * spacing of the space (the word spacing of the Text spacing setting, a span on it) were not there:
 * the box of the last letter then ends past the width by up to that spacing, which is blank. And it
 * measures each run that a span makes when it breaks the line, so a line with a span on every space
 * ends past the width by a fraction of a pixel for each of them: half a pixel is the most.
 */
private fun TextLayoutResult.visibleRight(line: Int): Float {
    val spans = layoutInput.text.spanStyles.filter { it.item.letterSpacing.isSpecified }
    val inLine = spans.count { it.start < getLineEnd(line) && it.end > getLineStart(line) }
    val rounding = ROUNDING_PX * inLine
    val last = getLineEnd(line) - 1
    val hanging =
        if (last >= getLineStart(line) && layoutInput.text.text[last] == ' ') {
            spans.lastOrNull { last >= it.start && last < it.end }
        } else {
            null
        }
    return getLineRight(line) -
        rounding -
        (hanging?.let { hangingSpacing(it.item.letterSpacing) } ?: 0f)
}

/** The spacing of a hanging space above the letter spacing of the style, in px. */
private fun TextLayoutResult.hangingSpacing(spacing: TextUnit): Float =
    with(layoutInput.density) {
        val size = layoutInput.style.fontSize.toPx()
        fun TextUnit.px() =
            when {
                !isSpecified -> 0f
                type == TextUnitType.Em -> value * size
                else -> toPx()
            }
        maxOf(spacing.px() - layoutInput.style.letterSpacing.px(), 0f)
    }

private const val ROUNDING_PX = 0.5f

/**
 * The width the text was given to lay its lines out in; a text with no limit has an infinite one.
 */
private fun maxWidth(layout: TextLayoutResult): Float =
    layout.layoutInput.constraints.maxWidth.takeIf { it != Constraints.Infinity }?.toFloat()
        ?: Float.MAX_VALUE

/** Fails, naming each text, when [clippedTexts] is not empty. */
fun ComposeTestRule.assertNoClippedText() {
    assertWithMessage("texts that do not fit").that(clippedTexts()).isEmpty()
}
