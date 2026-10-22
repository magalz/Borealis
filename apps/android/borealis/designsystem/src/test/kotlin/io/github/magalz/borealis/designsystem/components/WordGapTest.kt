package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The word gap stays clearly larger than the letter gap in every font (ADR 0058, decision 4): at
 * 0.35 em of word spacing the words ran together on the spike sheets, and the setting is only worth
 * having if the reader can tell a word from a letter. A text of two words is drawn large and its
 * gaps are measured on the pixels, from the layout that the text has: the letter gap is the space
 * between the two letters of a word, and the word gap the space between the two words, each from
 * the ink of one glyph to the ink of the next, so that the side bearings of the font count as they
 * do for the eye.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w1400dp-h700dp-mdpi")
class WordGapTest(private val font: FontChoice) {
    @get:Rule val composeRule = createComposeRule()

    private var spacing by mutableStateOf(TextSpacing.Normal)

    private class Gaps(val letter: Int, val word: Int) {
        val ratio: Float
            get() = word.toFloat() / letter

        override fun toString() =
            "letter $letter px, word $word px, ratio ${String.format(Locale.ROOT, "%.2f", ratio)}"
    }

    /** The gaps of "nn nn" at [level]: the layout is one composition, whose level changes. */
    private fun gaps(level: TextSpacing): Gaps {
        spacing = level
        composeRule.waitForIdle()
        val shot = composeRule.frame("text")
        val runs = inkRuns(shot)
        assertWithMessage("the ink runs of the four letters in $font at $level")
            .that(runs.size)
            .isEqualTo(4)
        return Gaps(
            letter = (runs[1].first - runs[0].last - 1 + runs[3].first - runs[2].last - 1) / 2,
            word = runs[2].first - runs[1].last - 1,
        )
    }

    /** The columns that have ink, as runs: white on black, so the ink is the bright columns. */
    private fun inkRuns(shot: PixelMap): List<IntRange> {
        val ink =
            (0 until shot.width).map { x ->
                (0 until shot.height).any { y -> shot[x, y].red > INK_THRESHOLD }
            }
        val runs = mutableListOf<IntRange>()
        var start = -1
        for ((x, inked) in ink.withIndex()) {
            if (inked && start < 0) start = x
            if (!inked && start >= 0) {
                runs += start until x
                start = -1
            }
        }
        if (start >= 0) runs += start until ink.size
        return runs
    }

    private fun show() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone, BorealisLook(font = font, textSpacing = spacing)) {
                Box(
                    Modifier.testTag("text")
                        .background(Color.Black)
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                ) {
                    Text(
                        "nn nn",
                        color = Color.White,
                        softWrap = false,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = BIG),
                    )
                }
            }
        }
    }

    /**
     * The measurement follows the setting: the letters are further apart by the letter spacing of
     * [level], and the words by the two letter spacings at the space and its word spacing.
     */
    private fun assertSpread(level: TextSpacing, normal: Gaps, measured: Gaps) {
        val em = BIG.value
        assertWithMessage("$font at $level: the letter gap grows by the letter spacing")
            .that((measured.letter - normal.letter).toFloat())
            .isWithin(TOLERANCE_PX)
            .of(level.letterEm * em)
        assertWithMessage("$font at $level: the word gap grows by two letter spacings and a word")
            .that((measured.word - normal.word).toFloat())
            .isWithin(TOLERANCE_PX)
            .of((2 * level.letterEm + level.wordEm) * em)
    }

    @Test
    fun `the word gap is at least twice the letter gap at Wide and at Wider`() {
        show()
        val normal = gaps(TextSpacing.Normal)
        for (level in listOf(TextSpacing.Wide, TextSpacing.Wider)) {
            val measured = gaps(level)
            println("WORDGAP $font normal=[$normal] $level=[$measured]")

            assertSpread(level, normal, measured)
            assertWithMessage("$font at $level: $measured")
                .that(measured.ratio)
                .isAtLeast(MINIMUM_RATIO)
        }
    }

    @Test
    fun `at Wider the word gap is as many letter gaps as at Normal, or more`() {
        show()
        val normal = gaps(TextSpacing.Normal)
        val wider = gaps(TextSpacing.Wider)
        println("WORDGAP $font normal=[$normal] ${TextSpacing.Wider}=[$wider]")

        assertSpread(TextSpacing.Wider, normal, wider)
        assertWithMessage("$font: Normal $normal, Wider $wider")
            .that(wider.ratio)
            .isAtLeast(normal.ratio)
    }

    companion object {
        private val BIG = 200.sp
        private const val INK_THRESHOLD = 0.5f
        private const val MINIMUM_RATIO = 2f
        private const val TOLERANCE_PX = 4f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries
    }
}
