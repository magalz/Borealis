package io.github.magalz.borealis.designsystem.testing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.Text
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
    fun `the letter spacing after the last letter of a spaced line is not reported`() {
        measuring()
        style = style.copy(letterSpacing = 0.12.em)
        val text =
            spaced(
                "aaa bbb ccc ddd eee fff ggg hhh iii jjj kkk lll mmm nnn ooo ppp qqq rrr sss ttt"
            )
        // A width at which the last line, which has no space at its end, ends past the room: the
        // breaker leaves out the letter spacing that follows its last letter.
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

    /** This layout with [overshoot] px of its widest line past the width it was given. */
    private fun TextLayoutResult.pastTheWidthBy(line: Int, overshoot: Float): TextLayoutResult =
        copy(
            layoutInput =
                layoutInput.copy(
                    constraints = Constraints(maxWidth = (getLineRight(line) - overshoot).toInt())
                )
        )

    @Test
    fun `a line that is not the last and does not end in a space is checked against the width`() {
        measuring()
        style = style.copy(letterSpacing = 0.18.em)
        // A word that does not fit: the lines break inside it, and none of them ends in a space.
        val wrapped = layout(spaced("a".repeat(60) + " bbb"), 100)
        assertThat(wrapped.lineCount).isGreaterThan(2)
        val spacing = style.letterSpacing.value * style.fontSize.value

        // A line of a word that ends past the room by about the letter spacing is no hang.
        assertThat(wrapped.pastTheWidthBy(0, spacing * 0.8f).fitProblems())
            .containsExactly("runs past its width")
    }

    @Test
    fun `the letter spacing after the last letter of the last line is allowed, and no more`() {
        measuring()
        style = style.copy(letterSpacing = 0.18.em)
        val wrapped = layout(spaced("aaa aaa aaa aaa aaa aaaa"), 60)
        // The last line is the widest, so that narrowing the room for it does not narrow it for the
        // rest.
        val last = wrapped.lineCount - 1
        val spacing = style.letterSpacing.value * style.fontSize.value

        assertThat(wrapped.pastTheWidthBy(last, spacing * 0.8f).fitProblems()).isEmpty()
        assertThat(wrapped.pastTheWidthBy(last, spacing + 4f).fitProblems())
            .containsExactly("runs past its width")
    }

    @Test
    fun `a hanging space is allowed up to its extra spacing, and no more`() {
        measuring()
        val wrapped = layout(spaced("aaaa aaa aaa aaa"), 40)
        assertThat(wrapped.lineCount).isGreaterThan(2)
        val extra = 0.5f * style.fontSize.value

        assertThat(wrapped.pastTheWidthBy(0, extra * 0.8f).fitProblems()).isEmpty()
        assertThat(wrapped.pastTheWidthBy(0, extra + 4f).fitProblems())
            .containsExactly("runs past its width")
    }

    @Test
    fun `a text that does not wrap and does not fit says that it runs past its width`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                Column {
                    Text(
                        "Enter the address of the host",
                        Modifier.width(60.dp).testTag("one line"),
                        softWrap = false,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Enter the address of the host",
                        Modifier.width(60.dp).testTag("no limit"),
                        softWrap = false,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        for (tag in listOf("one line", "no limit")) {
            assertThat(composeRule.onNodeWithTag(tag).textLayout().fitProblems())
                .containsExactly("runs past its width")
        }
    }

    @Test
    fun `a text that is cut off at the bottom says so`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                Text(
                    "Enter the address of the host that you want to stream from",
                    Modifier.width(80.dp).height(20.dp).testTag("short"),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        composeRule.waitForIdle()

        assertThat(composeRule.onNodeWithTag("short").textLayout().fitProblems())
            .doesNotContain("runs past its width")
    }

    @Test
    fun `a text that fits is not reported`() {
        measuring()

        val line = layout(spaced("aaa bbb"), Constraints.Infinity)

        assertThat(line.fitProblems()).isEmpty()
    }
}
