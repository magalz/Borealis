package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the lines of a text with the word spacing of the Text spacing setting do at the end. Android
 * breaks a line at a space and lets the space hang past the end, and when the extra spacing is a
 * span on that space the line is wrapped as if the space carried half of it less: the letters of
 * the line end past the width of the text, by up to the word spacing (11 px past 623 px for 0.5 em
 * at 27 sp). The spacing stays on the space, because a span on the letter before it pushes the last
 * letter of every word away from its word (0.25 em at Wider). This test keeps the overrun within
 * the word spacing, at many widths: without that allowance, 35 of the 101 widths of the sentence
 * below have a line past the width in Andika at Wider.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class TextWrapTest {
    @get:Rule val composeRule = createComposeRule()

    private var width by mutableStateOf(MIN_WIDTH)
    private var look by mutableStateOf(BorealisLook())

    /**
     * The widths in dp at which a line ends more than the word spacing of [level] past the text.
     */
    private fun widthsWithOverrun(level: TextSpacing, font: FontChoice): List<Int> {
        look = BorealisLook(font = font, textSpacing = level)
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, look) {
                Text(
                    SENTENCE,
                    Modifier.width(width.dp).testTag("t"),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        val overruns = mutableListOf<Int>()
        for (candidate in MIN_WIDTH..MAX_WIDTH step STEP) {
            width = candidate
            composeRule.waitForIdle()
            val layout = composeRule.onNodeWithTag("t").textLayout()
            val room = layout.layoutInput.constraints.maxWidth
            val style = layout.layoutInput.style
            val allowed = level.wordEm * style.fontSize.value * layout.layoutInput.density.density
            if ((0 until layout.lineCount).any { layout.getLineRight(it) > room + allowed + 1f }) {
                overruns += candidate
            }
        }
        return overruns
    }

    @Test
    fun `no line ends more than the word spacing past the text at Wide, in IBM Plex`() {
        assertWithMessage("widths in dp where a line ends too far past the text")
            .that(widthsWithOverrun(TextSpacing.Wide, FontChoice.Plex))
            .isEmpty()
    }

    @Test
    fun `no line ends more than the word spacing past the text at Wider, in IBM Plex`() {
        assertWithMessage("widths in dp where a line ends too far past the text")
            .that(widthsWithOverrun(TextSpacing.Wider, FontChoice.Plex))
            .isEmpty()
    }

    @Test
    fun `no line ends more than the word spacing past the text at Wider, in Andika`() {
        assertWithMessage("widths in dp where a line ends too far past the text")
            .that(widthsWithOverrun(TextSpacing.Wider, FontChoice.Andika))
            .isEmpty()
    }

    private companion object {
        const val SENTENCE =
            "A built-in driver for Xbox 360 and Xbox One controllers. Off also stops the USB " +
                "attach dialog."
        const val MIN_WIDTH = 120
        const val MAX_WIDTH = 420
        const val STEP = 3
    }
}
