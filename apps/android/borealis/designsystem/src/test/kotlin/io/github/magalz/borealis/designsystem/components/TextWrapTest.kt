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
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the lines of a text with the word spacing of the Text spacing setting do at the end. Android
 * breaks a line at a space and lets the space hang past the end, and when the extra spacing is a
 * span on that space the line is wrapped as if the space carried less of it: the letters of the
 * line end past the width of the text, by up to the word spacing (11 px past 623 px for 0.5 em at
 * 27 sp), and so does the ink. The spacing stays on the space, because a span on the letter before
 * it pushes the last letter of every word away from its word (0.25 em at Wider).
 *
 * This test keeps the overrun within the word spacing, in every font, for three texts of the
 * settings, at the default text size and at the largest, at many widths. A text that is clipped at
 * its end needs that much room before the clip. Without the allowance, a third of the widths have a
 * line past the width.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.3f)
class TextWrapTest(private val font: FontChoice) {
    @get:Rule val composeRule = createComposeRule()

    private var width by mutableStateOf(MIN_WIDTH)
    private var sentence by mutableStateOf(SENTENCES.first())
    private var look by mutableStateOf(BorealisLook())

    /**
     * The widths in dp at which a line ends more than the word spacing of [level] past the text.
     */
    private fun widthsWithOverrun(level: TextSpacing): List<String> {
        look = BorealisLook(font = font, textSpacing = level)
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, look) {
                Text(
                    sentence,
                    Modifier.width(width.dp).testTag("t"),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        val overruns = mutableListOf<String>()
        for (size in listOf(TextSize.Default, TextSize.Largest)) {
            look = BorealisLook(font = font, textSpacing = level, textSize = size)
            for (text in SENTENCES) {
                sentence = text
                for (candidate in MIN_WIDTH..MAX_WIDTH step STEP) {
                    width = candidate
                    composeRule.waitForIdle()
                    val layout = composeRule.onNodeWithTag("t").textLayout()
                    val room = layout.layoutInput.constraints.maxWidth
                    val style = layout.layoutInput.style
                    val allowed =
                        level.wordEm * with(layout.layoutInput.density) { style.fontSize.toPx() }
                    if (
                        (0 until layout.lineCount).any {
                            layout.getLineRight(it) > room + allowed + 1f
                        }
                    ) {
                        overruns += "$size, ${text.take(12)}, $candidate dp"
                    }
                }
            }
        }
        return overruns
    }

    @Test
    fun `no line ends more than the word spacing past the text at Wide`() {
        assertWithMessage("where a line ends too far past the text")
            .that(widthsWithOverrun(TextSpacing.Wide))
            .isEmpty()
    }

    @Test
    fun `no line ends more than the word spacing past the text at Wider`() {
        assertWithMessage("where a line ends too far past the text")
            .that(widthsWithOverrun(TextSpacing.Wider))
            .isEmpty()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries

        private val SENTENCES =
            listOf(
                "A built-in driver for Xbox 360 and Xbox One controllers. Off also stops the USB " +
                    "attach dialog.",
                "On removes animations; Off keeps them even when the device removes them. Follow " +
                    "system uses the device's animation setting.",
                "Uses status colours that are easy to tell apart with red-green or blue-yellow " +
                    "colour blindness.",
            )
        private const val MIN_WIDTH = 120
        private const val MAX_WIDTH = 420
        private const val STEP = 5
    }
}
