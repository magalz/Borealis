package io.github.magalz.borealis.designsystem.testing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What [fitProblems] reports as a text that does not fit. Android lets a space at the end of a
 * wrapped line hang past the width, and wraps the line as if the extra spacing of that space (the
 * word spacing of the Text spacing setting) were not there, so the box of the last letter ends past
 * the width by up to that spacing. A hang that the setting itself causes is not a text that runs
 * past its width; a line that does not wrap and is wider than the room is.
 */
@RunWith(AndroidJUnit4::class)
class TextFitTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var measurer: TextMeasurer
    private lateinit var style: TextStyle

    private fun measuring() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                measurer = rememberTextMeasurer()
                style = MaterialTheme.typography.bodyLarge
            }
        }
        composeRule.waitForIdle()
    }

    private fun layout(
        text: AnnotatedString,
        maxWidth: Int,
        softWrap: Boolean = true,
    ): TextLayoutResult =
        measurer.measure(
            text,
            style,
            softWrap = softWrap,
            constraints = Constraints(maxWidth = maxWidth),
        )

    /** [text] with the word spacing of Wider (0.5 em, and no letter spacing) at each space. */
    private fun spaced(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        text.forEachIndexed { index, char ->
            if (char == ' ') addStyle(SpanStyle(letterSpacing = 0.5.em), index, index + 1)
        }
    }

    @Test
    fun `a word spacing that hangs at the end of a wrapped line is not reported`() {
        measuring()
        val text = spaced("aaa bbb ccc")
        val first = text.subSequence(0, "aaa bbb".length)
        val whole = layout(first, Constraints.Infinity).size.width
        // The widest room that wraps after "bbb" and has the line end past it.
        val room =
            (whole downTo whole / 2).firstOrNull {
                val wrapped = layout(text, it)
                wrapped.lineCount == 2 && wrapped.getLineRight(0) > it + 1f
            }
        checkNotNull(room) { "no room makes the space of the first line hang past it" }

        assertThat(layout(text, room).fitProblems()).isEmpty()
    }

    @Test
    fun `the rounding of the line breaker over the spans of a line is not reported`() {
        measuring()
        style = style.copy(letterSpacing = 0.12.em)
        val text =
            spaced(
                "aaa bbb ccc ddd eee fff ggg hhh iii jjj kkk lll mmm nnn ooo ppp qqq rrr sss ttt"
            )
        // A width at which the last line, which has no space at its end, ends past the room: the
        // breaker rounds the width of each of the many runs that the spans make.
        val room =
            (200..900).firstOrNull {
                val wrapped = layout(text, it)
                wrapped.getLineRight(wrapped.lineCount - 1) > it + 1f
            }
        checkNotNull(room) { "no room makes the last line end past it" }

        assertThat(layout(text, room).fitProblems()).isEmpty()
    }

    @Test
    fun `a line that does not wrap and is wider than the room is reported`() {
        measuring()
        val text = spaced("aaa bbb ccc")
        val room = layout(text, Constraints.Infinity).size.width / 2

        val line = layout(text, room, softWrap = false)

        assertThat(line.fitProblems()).containsExactly("runs past its width")
    }

    @Test
    fun `a text that fits is not reported`() {
        measuring()

        val line = layout(spaced("aaa bbb"), Constraints.Infinity)

        assertThat(line.fitProblems()).isEmpty()
    }
}
