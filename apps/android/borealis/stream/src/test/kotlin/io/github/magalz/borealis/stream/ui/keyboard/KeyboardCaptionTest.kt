package io.github.magalz.borealis.stream.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.ui.StreamKeyboard
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the keys show and say, and the panel's own edge. */
@RunWith(AndroidJUnit4::class)
class KeyboardCaptionTest {
    @get:Rule val composeRule = createComposeRule()
    private val intents = RecordingIntents()

    private fun show(formFactor: FormFactor = FormFactor.Phone) {
        composeRule.setContent { Host(formFactor) }
    }

    @Composable
    private fun Host(formFactor: FormFactor) {
        BorealisTheme(formFactor) { Box(Modifier.fillMaxSize()) { StreamKeyboard(true, intents) } }
    }

    private fun contentDescriptionOf(tag: String): List<String> =
        composeRule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            .orEmpty()

    /** The keys whose text does not fit the width the key gives it. */
    private fun clippedKeys(): List<String> {
        fun SemanticsNode.keyTag() =
            parent?.config?.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.startsWith("key:") }

        // The text is its own node in the unmerged tree, under the key that carries the tag.
        val captions =
            composeRule
                .onAllNodes(
                    SemanticsMatcher("is the text of a key") {
                        it.keyTag() != null && SemanticsActions.GetTextLayoutResult in it.config
                    },
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
        assertThat(captions).isNotEmpty()
        return captions
            .filter { caption ->
                val layouts = mutableListOf<TextLayoutResult>()
                caption.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                // hasVisualOverflow is also true for centred text that is narrower than its slot.
                layouts.single().multiParagraph.didExceedMaxLines
            }
            .map { checkNotNull(it.keyTag()) }
    }

    @Test
    @Config(qualifiers = "w568dp-h320dp-land-xhdpi")
    fun `Backspace shows a symbol and is announced as Backspace`() {
        show()

        composeRule.onNodeWithText("⌫").assertExists()
        assertThat(contentDescriptionOf("key:Backspace")).containsExactly("Backspace")
    }

    @Test
    @Config(qualifiers = "w568dp-h320dp-land-xhdpi")
    fun `the Backspace symbol sends Backspace`() {
        show()

        composeRule.onNodeWithTag("key:Backspace").performClick()

        assertThat(intents.chords.map { it.vk }).containsExactly(0x08)
    }

    @Test
    @Config(qualifiers = "w568dp-h320dp-land-xhdpi", fontScale = 1.3f)
    fun `no key of the letter layer is clipped on a narrow landscape phone at font scale 1_3`() {
        show()

        assertThat(clippedKeys()).isEmpty()
    }

    @Test
    @Config(qualifiers = "w568dp-h320dp-land-xhdpi", fontScale = 1.3f)
    fun `no key of the Fn layer is clipped on a narrow landscape phone at font scale 1_3`() {
        show()
        composeRule.onNodeWithTag("key:Fn").performClick()

        assertThat(clippedKeys()).isEmpty()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun `the panel has a 1dp line-strong top border over its surface`() {
        show()

        val pixels = composeRule.onNodeWithTag("keyboard").captureToImage().toPixelMap()
        val x = pixels.width / 2
        val surface = BorealisTokens.Color.Surface
        val border = BorealisTokens.Color.LineStrong.compositeOver(surface)

        // At xhdpi the border is the first two pixel rows, and the surface follows.
        for (y in 0..1) assertThat(distance(pixels[x, y], border)).isLessThan(TOLERANCE)
        assertThat(distance(pixels[x, 3], surface)).isLessThan(TOLERANCE)
    }

    private fun distance(
        actual: Color,
        expected: Color,
    ) =
        abs(actual.red - expected.red) +
            abs(actual.green - expected.green) +
            abs(actual.blue - expected.blue)

    private companion object {
        const val TOLERANCE = 0.02f
    }
}
