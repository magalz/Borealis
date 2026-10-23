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
 *
 * A text that does not wrap (softWrap off) is laid out in the width it has and Compose reports what
 * does not fit as an overflow of the height, and of the lines it may show; that is the width, and
 * it is what the entry says.
 */
fun TextLayoutResult.fitProblems(): List<String> {
    val lines = 0 until lineCount
    val past = !layoutInput.softWrap && (didOverflowHeight || multiParagraph.didExceedMaxLines)
    return buildList {
        if (past || lines.any { visibleRight(it) > maxWidth(this@fitProblems) + HALF_PIXEL }) {
            add("runs past its width")
        }
        if (didOverflowHeight && !past) add("is cut off at the bottom")
        if (lines.any { isLineEllipsized(it) }) add("ends in an ellipsis")
        if (multiParagraph.didExceedMaxLines && !past) add("has more lines than it may show")
    }
}

private const val HALF_PIXEL = 0.5f

/**
 * How far the lines of this layout run past the width that the text was given, in px, with the room
 * that [fitProblems] allows them taken off: 0 or less when none does. [fitProblems] holds a line to
 * a pixel of rounding; the text of a line with several spaces that have spans can be a pixel or two
 * wider than that and still be what the screen draws (up to 2.3 px were measured at Wider), so a
 * test that is about a text running past its box by a word's worth reads this and not the problem.
 */
fun TextLayoutResult.widthOvershootPx(): Float =
    (0 until lineCount).maxOf { visibleRight(it) - maxWidth(this@widthOvershootPx) }

/**
 * The right edge of [line], less what Android lets a line end past the width when its text has the
 * word spacing of the Text spacing setting.
 *
 * Android breaks a line at a space and lets that space hang past the end, and it wraps the line as
 * if the extra spacing of that space (a span on it) were not there. So a line that ends in a spaced
 * space ends past the width by up to that spacing: the box of its last letter does, and so does its
 * ink. It is not blank. Measured: 12 px at Wider in IBM Plex, and up to 23 px at the largest text
 * of a TV in the System font, which is one word spacing (0.5 em). None of it is cut today, because
 * it lands in the padding or the gap that a text has at its end; a text that is clipped at its end
 * needs that much room before the clip. This check allows that spacing, and a pixel, on a line that
 * ends in a spaced space, and nothing else on it.
 *
 * A line that does not end in a space (the last line, or one broken inside a word) ends in the
 * letter spacing that follows its last letter, which Android puts in the line and not always in the
 * width that it wrapped to (2.15 px past a width for a letter spacing of 4.3 px, 3.15 px for 6.4
 * px, 2.18 px for a word broken in a text of 6.4 px, and a key caption that was sized without it).
 * That spacing is blank, and it is allowed there, with a pixel of rounding when the text has spans.
 */
private fun TextLayoutResult.visibleRight(line: Int): Float {
    val spans = layoutInput.text.spanStyles.filter { it.item.letterSpacing.isSpecified }
    val last = getLineEnd(line) - 1
    val endsInSpace = last >= getLineStart(line) && layoutInput.text.text[last] == ' '
    val hanging = if (endsInSpace) spans.lastOrNull { last >= it.start && last < it.end } else null
    val hang = hanging?.let { hangingSpacing(it.item.letterSpacing) + ROUNDING_PX } ?: 0f
    val spacing = letterSpacingPx(layoutInput.style.letterSpacing)
    val trailing = if (endsInSpace) 0f else spacing + (if (spans.isEmpty()) 0f else ROUNDING_PX)
    return getLineRight(line) - trailing - hang
}

private const val ROUNDING_PX = 1f

/** [spacing] of the text in px: an em is the size of its style. */
private fun TextLayoutResult.letterSpacingPx(spacing: TextUnit): Float =
    with(layoutInput.density) {
        when {
            !spacing.isSpecified -> 0f
            spacing.type == TextUnitType.Em -> spacing.value * layoutInput.style.fontSize.toPx()
            else -> spacing.toPx()
        }
    }

/** The spacing of a hanging space above the letter spacing of the style, in px. */
private fun TextLayoutResult.hangingSpacing(spacing: TextUnit): Float =
    maxOf(letterSpacingPx(spacing) - letterSpacingPx(layoutInput.style.letterSpacing), 0f)

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
