package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the Text spacing of the look does in Settings on a TV (ADR 0058, decision 4): the text of
 * the screen, and of the sheet of a choice, is drawn with the letter spacing of the level and the
 * word spacing on each space, and a change of the look reaches what is shown. "Text spacing" is the
 * title of a row of Accessibility and has one space.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class SettingsTextSpacingTest : SettingsScreenTestBase() {
    @Before fun theDeviceIsATelevision() = asTelevision()

    private var look by mutableStateOf(BorealisLook())

    /** The page with the look that the Library gives the screens, which the test can change. */
    private val withLook: @Composable (@Composable () -> Unit) -> Unit = {
        CompositionLocalProvider(LocalBorealisLook provides look) { OnPage(it) }
    }

    private fun layout(text: String): TextLayoutResult =
        composeRule.onNode(hasText(text), useUnmergedTree = true).textLayout()

    private fun letterEm(layout: TextLayoutResult): Float {
        val style = layout.layoutInput.style
        val spacing = style.letterSpacing
        if (!spacing.isSpecified) return 0f
        return if (spacing.type == TextUnitType.Em) spacing.value
        else spacing.value / style.fontSize.value
    }

    /** The extra spacing of the space of "Text spacing", in em, or null when it has none. */
    private fun spaceEm(layout: TextLayoutResult): Float? =
        layout.layoutInput.text.spanStyles
            .singleOrNull { it.start == SPACE && it.end == SPACE + 1 }
            ?.item
            ?.letterSpacing
            ?.value

    private fun showAccessibility() =
        show(keyboard = true, initialGroup = SettingsGroupId.ACCESSIBILITY, backdrop = withLook)

    @Test
    fun `the text of the screen has the spacing of the design in the default look`() {
        showAccessibility()

        val title = layout("Text spacing")
        assertThat(letterEm(title)).isWithin(EPSILON).of(0f)
        assertThat(spaceEm(title)).isNull()
    }

    @Test
    fun `the text of the screen has the letter and word spacing of the look`() {
        look = BorealisLook(textSpacing = TextSpacing.Wider)
        showAccessibility()

        val title = layout("Text spacing")
        assertThat(letterEm(title)).isWithin(EPSILON).of(TextSpacing.Wider.letterEm)
        assertThat(spaceEm(title))
            .isWithin(EPSILON)
            .of(TextSpacing.Wider.letterEm + TextSpacing.Wider.wordEm)
    }

    @Test
    fun `a change of the look reaches the text that is already shown`() {
        showAccessibility()

        look = BorealisLook(textSpacing = TextSpacing.Wide)
        composeRule.waitForIdle()
        assertThat(letterEm(layout("Text spacing"))).isWithin(EPSILON).of(TextSpacing.Wide.letterEm)
        assertThat(spaceEm(layout("Text spacing")))
            .isWithin(EPSILON)
            .of(TextSpacing.Wide.letterEm + TextSpacing.Wide.wordEm)

        look = BorealisLook()
        composeRule.waitForIdle()
        assertThat(spaceEm(layout("Text spacing"))).isNull()
    }

    @Test
    fun `the sheet of a choice has the spacing of the look too`() {
        look = BorealisLook(textSpacing = TextSpacing.Wide)
        showAccessibility()
        composeRule.onNodeWithText("Text spacing").performScrollTo().performClick()
        composeRule.waitForIdle()

        // The row and the title of the sheet over it have the same text.
        val titles =
            composeRule
                .onAllNodesWithText("Text spacing", useUnmergedTree = true)
                .fetchSemanticsNodes()
        assertThat(titles).hasSize(2)
        for (title in titles) {
            assertThat(spaceEm(title.textLayout()))
                .isWithin(EPSILON)
                .of(TextSpacing.Wide.letterEm + TextSpacing.Wide.wordEm)
        }
    }

    private companion object {
        const val EPSILON = 0.001f

        /** The index of the space in "Text spacing". */
        const val SPACE = 4
    }
}
