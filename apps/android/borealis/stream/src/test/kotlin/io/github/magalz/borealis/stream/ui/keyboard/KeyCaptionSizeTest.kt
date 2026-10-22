package io.github.magalz.borealis.stream.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.clippedTexts
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.ui.StreamKeyboard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

private const val SYSTEM_SCALE = 1.3f

/**
 * The caption of a key at the largest text size (ADR 0057, decision 11): a key is only so wide, so
 * a caption that does not fit it wraps to a second line where it has a space, and only then
 * shrinks, and it never renders smaller than the same key does with the default look. The system
 * font scale is 1.3, so the largest text size is 1.69 of the text that the keys are laid out for.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(fontScale = SYSTEM_SCALE)
class KeyCaptionSizeTest(private val font: FontChoice) {
    @get:Rule val composeRule = createComposeRule()

    private var look by mutableStateOf(BorealisLook(font = font))

    @Test
    @Config(qualifiers = PHONE_320)
    fun `on a 320dp phone no caption is smaller than with the default look`() =
        assertNoCaptionIsSmaller()

    @Test
    @Config(qualifiers = PHONE_360)
    fun `on a 360dp phone no caption is smaller than with the default look`() =
        assertNoCaptionIsSmaller()

    @Test
    @Config(qualifiers = PHONE_320)
    fun `a caption with a space wraps to a second line at the largest text size, before it shrinks`() {
        show()
        look = BorealisLook(textSize = TextSize.Largest, font = font)
        composeRule.waitForIdle()

        val fn = checkNotNull(captions()["key:Fn 123"])
        assertThat(fn.lineCount).isEqualTo(2)
        assertThat(fn.getBoundingBox(0).width / fn.layoutInput.density.density)
            .isGreaterThan(MIN_WRAPPED_GLYPH_DP)
    }

    @Test
    @Config(qualifiers = PHONE_320)
    fun `a caption of one word stays on one line at the largest text size`() {
        show()
        look = BorealisLook(textSize = TextSize.Largest, font = font)
        composeRule.waitForIdle()

        for ((key, layout) in captions().filterKeys { " " !in it.removePrefix("key:") }) {
            assertWithMessage("the caption of $key").that(layout.lineCount).isEqualTo(1)
        }
    }

    @Test
    @Config(qualifiers = PHONE_320)
    fun `on a 320dp phone no caption is cut off at the largest text size`() {
        show()
        look = BorealisLook(textSize = TextSize.Largest, font = font)
        composeRule.waitForIdle()

        assertThat(composeRule.clippedTexts()).isEmpty()
    }

    private fun assertNoCaptionIsSmaller() {
        show()
        val byDefault = captionSizes()
        look = BorealisLook(textSize = TextSize.Largest, font = font)
        composeRule.waitForIdle()
        val largest = captionSizes()

        assertThat(largest.keys).isEqualTo(byDefault.keys)
        for ((key, size) in largest) {
            assertWithMessage(
                    "the caption of $key renders at $size dp, with the default look at ${byDefault[key]} dp"
                )
                .that(size)
                .isAtLeast(checkNotNull(byDefault[key]) - EPSILON)
        }
    }

    private fun show() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone, look) {
                Box(Modifier.fillMaxSize()) { StreamKeyboard(true, RecordingIntents()) }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * The size that the caption of each key renders at, by the tag of the key: the width in dp of
     * its first glyph. The layout input keeps the size of the style, not the one that the text
     * shrank to, and the width of a glyph follows the size and does not depend on where the text
     * wraps.
     */
    private fun captionSizes(): Map<String, Float> =
        captions().mapValues { (_, layout) ->
            layout.getBoundingBox(0).width / layout.layoutInput.density.density
        }

    private fun captions(): Map<String, TextLayoutResult> {
        fun SemanticsNode.keyTag() =
            parent?.config?.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.startsWith("key:") }

        return composeRule
            .onAllNodes(
                SemanticsMatcher("is the text of a key") {
                    it.keyTag() != null && SemanticsActions.GetTextLayoutResult in it.config
                },
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .associate { caption -> checkNotNull(caption.keyTag()) to caption.textLayout() }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries

        private const val PHONE_320 = "w320dp-h568dp-xhdpi"
        private const val PHONE_360 = "w360dp-h640dp-xhdpi"
        private const val EPSILON = 0.05f

        /** The first glyph of "Fn" is 5.3dp wide where the caption is shrunk to one line. */
        private const val MIN_WRAPPED_GLYPH_DP = 6f
    }
}
