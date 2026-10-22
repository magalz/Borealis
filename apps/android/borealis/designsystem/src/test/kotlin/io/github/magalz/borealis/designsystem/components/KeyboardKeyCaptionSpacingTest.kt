package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.letterSpacingEm
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The caption of a key at the Text spacing (ADR 0058, decision 4). A key is only so wide, so its
 * caption cannot take the half em between the words and the 0.18 em between the letters that Wider
 * gives: at Wider the caption is drawn with the spacing of Wide, 0.12 em between the letters and
 * 0.16 em between the words (UAT of 2026-10-22). At Normal and at Wide it has what the level says.
 * A key of the widest style holds the caption without shrinking it, so that its width is the width
 * of the text and the spacing is all that tells two runs apart.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class KeyboardKeyCaptionSpacingTest(private val font: FontChoice) {
    @get:Rule val composeRule = createComposeRule()

    private var look by mutableStateOf(BorealisLook(font = font))
    private var shown by mutableStateOf("")
    private var started = false

    @Test
    fun `a caption has the letter spacing of the level at Normal and at Wide`() {
        assertThat(captionOf("Esc", TextSpacing.Normal).layoutInput.style.letterSpacingEm())
            .isEqualTo(0f)
        assertThat(captionOf("Esc", TextSpacing.Wide).layoutInput.style.letterSpacingEm())
            .isWithin(EPSILON)
            .of(TextSpacing.Wide.letterEm)
    }

    @Test
    fun `at Wider a caption has the letter spacing of Wide`() {
        assertThat(captionOf("Esc", TextSpacing.Wider).layoutInput.style.letterSpacingEm())
            .isWithin(EPSILON)
            .of(TextSpacing.Wide.letterEm)
    }

    @Test
    fun `at Wider a caption of one word is as wide as it is at Wide`() = assertAsWideAsAtWide("Esc")

    @Test
    fun `at Wider a caption of two words is as wide as it is at Wide`() =
        assertAsWideAsAtWide("Fn 123")

    @Test
    fun `a caption at Wide is wider than at Normal, by the spacing`() {
        val normal = width(captionOf("Fn 123", TextSpacing.Normal))
        val wide = width(captionOf("Fn 123", TextSpacing.Wide))

        assertThat(wide).isGreaterThan(normal)
    }

    private fun assertAsWideAsAtWide(label: String) {
        val wide = width(captionOf(label, TextSpacing.Wide))
        val wider = width(captionOf(label, TextSpacing.Wider))

        assertWithMessage("\"$label\" in $font is $wider px wide at Wider and $wide px at Wide")
            .that(wider)
            .isWithin(EPSILON)
            .of(wide)
    }

    /** The first line of the caption, from its left to its right: the ink of a line and no more. */
    private fun width(caption: TextLayoutResult): Float =
        caption.getLineRight(0) - caption.getLineLeft(0)

    /** The layout of the caption [label] of a key of the widest style, at [spacing]. */
    private fun captionOf(label: String, spacing: TextSpacing): TextLayoutResult {
        look = BorealisLook(font = font, textSpacing = spacing)
        shown = label
        if (!started) {
            started = true
            composeRule.setContent {
                BorealisTheme(FormFactor.Phone, look) {
                    Row { KeyboardKey(shown, {}, style = KeyStyle.Space) }
                }
            }
        }
        composeRule.waitForIdle()
        return composeRule.onNodeWithText(label, useUnmergedTree = true).textLayout()
    }

    private companion object {
        const val EPSILON = 0.01f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = listOf(FontChoice.Plex, FontChoice.Andika)
    }
}
