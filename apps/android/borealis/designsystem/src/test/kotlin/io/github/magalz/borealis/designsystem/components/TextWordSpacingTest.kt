package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The design-system [Text] adds the word spacing of the look (ADR 0058, decision 4): on each space,
 * the letter spacing of the style plus the word spacing of the level, as a span. The text that a
 * screen reader reads stays the original string, and at Normal the text is Material's.
 */
@RunWith(AndroidJUnit4::class)
class TextWordSpacingTest {
    @get:Rule val composeRule = createComposeRule()

    private var spacing by mutableStateOf(TextSpacing.Normal)

    /**
     * Shows [content] at [level]; a test has one composition, so a second call changes the level.
     */
    private var shown = false

    private fun themed(level: TextSpacing, content: @Composable () -> Unit = {}) {
        spacing = level
        if (!shown) {
            shown = true
            composeRule.setContent {
                BorealisTheme(FormFactor.Phone, BorealisLook(textSpacing = spacing)) { content() }
            }
        }
        composeRule.waitForIdle()
    }

    private fun layout(tag: String): TextLayoutResult = composeRule.onNodeWithTag(tag).textLayout()

    /** The spans that set a letter spacing in [text], as the range of each and its value in em. */
    private fun letterSpans(text: AnnotatedString): List<Triple<Int, Int, Float>> =
        text.spanStyles
            .filter { it.item.letterSpacing.type == TextUnitType.Em }
            .map { Triple(it.start, it.end, it.item.letterSpacing.value) }

    @Test
    fun `at Wide each space gets the letter spacing of the style plus the word spacing`() {
        themed(TextSpacing.Wide) {
            Text("one two three", Modifier.testTag("t"), style = MaterialTheme.typography.bodyLarge)
        }

        val spans = letterSpans(layout("t").layoutInput.text)

        assertThat(spans.map { it.first to it.second }).containsExactly(3 to 4, 7 to 8).inOrder()
        for ((_, _, value) in spans) {
            assertThat(value)
                .isWithin(EPSILON)
                .of(TextSpacing.Wide.letterEm + TextSpacing.Wide.wordEm)
        }
    }

    @Test
    fun `at Wider each space gets the letter spacing of the style plus the word spacing`() {
        themed(TextSpacing.Wider) {
            Text("one two three", Modifier.testTag("t"), style = MaterialTheme.typography.bodyLarge)
        }

        val spans = letterSpans(layout("t").layoutInput.text)

        assertThat(spans.map { it.first to it.second }).containsExactly(3 to 4, 7 to 8).inOrder()
        for ((_, _, value) in spans) {
            assertThat(value)
                .isWithin(EPSILON)
                .of(TextSpacing.Wider.letterEm + TextSpacing.Wider.wordEm)
        }
    }

    @Test
    fun `the letter spacing of the style is the one that the text is given, in whatever unit`() {
        themed(TextSpacing.Wide) {
            Column {
                Text("a b", Modifier.testTag("em"), letterSpacing = 0.3.em, fontSize = 16.sp)
                Text("a b", Modifier.testTag("sp"), letterSpacing = 4.sp, fontSize = 16.sp)
            }
        }

        assertThat(letterSpans(layout("em").layoutInput.text).single().third)
            .isWithin(EPSILON)
            .of(0.3f + TextSpacing.Wide.wordEm)
        // 4 sp at 16 sp is 0.25 em.
        assertThat(letterSpans(layout("sp").layoutInput.text).single().third)
            .isWithin(EPSILON)
            .of(0.25f + TextSpacing.Wide.wordEm)
    }

    @Test
    fun `an annotated string keeps its spans and gets the word spacing on each space`() {
        val text = buildAnnotatedString {
            append("Use ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("two words") }
            append(" here")
        }
        themed(TextSpacing.Wider) {
            Text(text, Modifier.testTag("t"), style = MaterialTheme.typography.bodyLarge)
        }

        val drawn = layout("t").layoutInput.text

        assertThat(drawn.text).isEqualTo("Use two words here")
        assertThat(
                drawn.spanStyles
                    .filter { it.item.fontWeight == FontWeight.Bold }
                    .map { it.start to it.end }
            )
            .containsExactly(4 to 13)
        assertThat(letterSpans(drawn).map { it.first }).containsExactly(3, 7, 13).inOrder()
    }

    @Test
    fun `an annotated string with no space is left as it is`() {
        val text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("word") }
        }
        themed(TextSpacing.Wider) { Text(text, Modifier.testTag("t")) }

        assertThat(layout("t").layoutInput.text).isEqualTo(text)
    }

    @Test
    fun `a text with no space is not given a span, at any level`() {
        for (level in TextSpacing.entries) {
            themed(level) { Text("word", Modifier.testTag("t")) }

            assertWithMessage("$level").that(layout("t").layoutInput.text.spanStyles).isEmpty()
        }
    }

    @Test
    fun `at Normal the text is the one that Material draws`() {
        themed(TextSpacing.Normal) {
            Column {
                Text(
                    "one two three",
                    Modifier.testTag("ours"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                MaterialText(
                    "one two three",
                    Modifier.testTag("material"),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        val ours = layout("ours")
        val material = layout("material")

        assertThat(ours.layoutInput.text.spanStyles).isEmpty()
        assertThat(ours.layoutInput.text).isEqualTo(material.layoutInput.text)
        assertThat(ours.layoutInput.style).isEqualTo(material.layoutInput.style)
        assertThat(ours.size).isEqualTo(material.size)
    }

    @Test
    fun `at Normal the annotated string is handed to Material as it is`() {
        val text = buildAnnotatedString {
            append("one ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("two") }
        }
        themed(TextSpacing.Normal) { Text(text, Modifier.testTag("t")) }

        assertThat(layout("t").layoutInput.text).isEqualTo(text)
    }

    @Test
    fun `at Normal the pixels are those of Material's text`() {
        themed(TextSpacing.Normal) {
            Column {
                Text(
                    "one two three",
                    Modifier.testTag("ours"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                MaterialText(
                    "one two three",
                    Modifier.testTag("material"),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        val ours = composeRule.frame("ours")
        val material = composeRule.frame("material")

        assertThat(ours.width).isEqualTo(material.width)
        assertThat(ours.buffer).isEqualTo(material.buffer)
    }

    @Test
    fun `each space is as wide as the word spacing of the level adds to Material's`() {
        for (level in listOf(TextSpacing.Wide, TextSpacing.Wider)) {
            themed(level) {
                Column {
                    Text(
                        "a b c",
                        Modifier.testTag("ours"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    MaterialText(
                        "a b c",
                        Modifier.testTag("material"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            val ours = layout("ours")
            val material = layout("material")
            val size = with(ours.layoutInput.density) { ours.layoutInput.style.fontSize.toPx() }

            // Two spaces: the text is wider by the word spacing of each.
            val added = (ours.size.width - material.size.width).toFloat()
            assertWithMessage("$level").that(added).isWithin(2f).of(2 * level.wordEm * size)
        }
    }

    @Test
    fun `the text that a screen reader reads is the original string`() {
        themed(TextSpacing.Wider) { Text("one two three", Modifier.testTag("t")) }

        val config = composeRule.onNodeWithTag("t").fetchSemanticsNode().config

        assertThat(config.getOrNull(SemanticsProperties.Text)?.map { it.text })
            .containsExactly("one two three")
        assertThat(config.getOrNull(SemanticsProperties.ContentDescription)).isNull()
        composeRule.onNodeWithText("one two three").assertExists()
    }

    @Test
    fun `an annotated string is read as its own text`() {
        themed(TextSpacing.Wider) { Text(AnnotatedString("one two"), Modifier.testTag("t")) }

        val config = composeRule.onNodeWithTag("t").fetchSemanticsNode().config

        assertThat(config.getOrNull(SemanticsProperties.Text)?.map { it.text })
            .containsExactly("one two")
        composeRule.onNodeWithText("one two").assertExists()
    }

    @Test
    fun `the layout is reported to the caller, as it is by Material`() {
        var reported: TextLayoutResult? = null
        themed(TextSpacing.Wider) { Text("one two", onTextLayout = { reported = it }) }

        assertThat(reported?.layoutInput?.text?.text).isEqualTo("one two")
    }

    @Test
    fun `a change of the setting reaches a text that is already shown`() {
        themed(TextSpacing.Normal) { Text("one two", Modifier.testTag("t")) }
        assertThat(layout("t").layoutInput.text.spanStyles).isEmpty()

        themed(TextSpacing.Wider)
        assertThat(letterSpans(layout("t").layoutInput.text).map { it.first }).containsExactly(3)

        themed(TextSpacing.Normal)
        assertThat(layout("t").layoutInput.text.spanStyles).isEmpty()
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
