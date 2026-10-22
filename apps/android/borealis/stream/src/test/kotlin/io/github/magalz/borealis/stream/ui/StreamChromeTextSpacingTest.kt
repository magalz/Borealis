package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.aSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The stream chrome draws with the Text spacing that the stream started with (ADR 0058, decision
 * 4): the letter spacing of the level in its text, and the word spacing on each of its spaces.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class StreamChromeTextSpacingTest {
    @get:Rule val composeRule = createComposeRule()

    private var look by mutableStateOf(BorealisLook())

    private fun layout(text: String): TextLayoutResult =
        composeRule.onNodeWithText(text, useUnmergedTree = true).textLayout()

    private fun letterEm(layout: TextLayoutResult): Float {
        val style = layout.layoutInput.style
        val spacing = style.letterSpacing
        if (!spacing.isSpecified) return 0f
        return if (spacing.type == TextUnitType.Em) spacing.value
        else spacing.value / style.fontSize.value
    }

    /** The extra spacing of the space at [index], in em, or null when the text has none there. */
    private fun spaceEm(layout: TextLayoutResult, index: Int): Float? =
        layout.layoutInput.text.spanStyles
            .singleOrNull { it.start == index && it.end == index + 1 }
            ?.item
            ?.letterSpacing
            ?.value

    private fun showMenu() {
        composeRule.setContent {
            StreamChromeTheme(look) {
                StreamingLayer(
                    "Desktop",
                    "Gaming PC",
                    aStreaming(Panel.MENU),
                    aPerf(),
                    RecordingIntents(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `with the default look the menu has the spacing of the design`() {
        showMenu()

        val label = layout("Session info")
        assertThat(letterEm(label)).isWithin(EPSILON).of(0f)
        assertThat(spaceEm(label, SESSION_INFO_SPACE)).isNull()
    }

    @Test
    fun `the menu has the letter and word spacing that the stream started with`() {
        look = BorealisLook(textSpacing = TextSpacing.Wider)
        showMenu()

        val label = layout("Session info")
        assertThat(letterEm(label)).isWithin(EPSILON).of(TextSpacing.Wider.letterEm)
        assertThat(spaceEm(label, SESSION_INFO_SPACE))
            .isWithin(EPSILON)
            .of(TextSpacing.Wider.letterEm + TextSpacing.Wider.wordEm)
    }

    @Test
    @Config(qualifiers = TV, fontScale = 1.69f)
    fun `at a font scale of 1 point 69 Wider is still drawn as Wider in the menu`() {
        look = BorealisLook(textSpacing = TextSpacing.Wider)
        showMenu()

        val label = layout("Session info")
        assertThat(letterEm(label)).isWithin(EPSILON).of(TextSpacing.Wider.letterEm)
        assertThat(spaceEm(label, SESSION_INFO_SPACE))
            .isWithin(EPSILON)
            .of(TextSpacing.Wider.letterEm + TextSpacing.Wider.wordEm)
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `at the largest font Wider is drawn as Wide in the menu, as in the app`() {
        look = BorealisLook(textSpacing = TextSpacing.Wider)
        showMenu()

        val label = layout("Session info")
        assertThat(letterEm(label)).isWithin(EPSILON).of(TextSpacing.Wide.letterEm)
        assertThat(spaceEm(label, SESSION_INFO_SPACE))
            .isWithin(EPSILON)
            .of(TextSpacing.Wide.letterEm + TextSpacing.Wide.wordEm)
    }

    @Test
    fun `the error screen has the spacing that the stream started with`() {
        look = BorealisLook(textSpacing = TextSpacing.Wide)
        composeRule.setContent {
            StreamChromeTheme(look) {
                ConnectionErrorScreen(
                    "Desktop",
                    "Desk PC",
                    StreamFailure(FailureKind.HOST_BUSY, "Error -1", null),
                    aSession(RecordingActions()),
                )
            }
        }
        composeRule.waitForIdle()

        val headline = layout("Desk PC is busy")
        assertThat(letterEm(headline)).isWithin(EPSILON).of(TextSpacing.Wide.letterEm)
        assertThat(spaceEm(headline, DESK_PC_SPACE))
            .isWithin(EPSILON)
            .of(TextSpacing.Wide.letterEm + TextSpacing.Wide.wordEm)
    }

    private companion object {
        const val EPSILON = 0.001f

        /** The index of the space in "Session info". */
        const val SESSION_INFO_SPACE = 7

        /** The index of the first space in "Desk PC is busy". */
        const val DESK_PC_SPACE = 4
    }
}
