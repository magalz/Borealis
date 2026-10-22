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
 * The right edge of [line], less the extra spacing of a space that ends it. Android breaks a line
 * at a space and lets that space hang past the width, and it wraps the line as if the extra spacing
 * of the space (the word spacing of the Text spacing setting, a span on it) were not there, so the
 * box of the last letter ends past the width by up to that spacing. That is not text that runs past
 * its width: the spacing above the letter spacing of the style is blank.
 */
private fun TextLayoutResult.visibleRight(line: Int): Float {
    val right = getLineRight(line)
    val last = getLineEnd(line) - 1
    if (last < getLineStart(line) || layoutInput.text.text[last] != ' ') return right
    val span =
        layoutInput.text.spanStyles.lastOrNull {
            it.item.letterSpacing.isSpecified && last >= it.start && last < it.end
        } ?: return right
    val hang =
        with(layoutInput.density) {
            val size = layoutInput.style.fontSize.toPx()
            fun TextUnit.px() =
                when {
                    !isSpecified -> 0f
                    type == TextUnitType.Em -> value * size
                    else -> toPx()
                }
            span.item.letterSpacing.px() - layoutInput.style.letterSpacing.px()
        }
    return right - maxOf(hang, 0f)
}

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
