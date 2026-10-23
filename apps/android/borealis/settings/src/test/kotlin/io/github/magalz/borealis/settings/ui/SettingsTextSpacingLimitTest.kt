package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.testing.dialogFrame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val TITLE_WIDER = "Wider spacing is limited"

private fun message(chosen: String, drawn: String) =
    "At this text size, Borealis uses $drawn spacing instead of $chosen, so that nothing is cut " +
        "off. Your choice stays: $chosen comes back when the system font size or the Text size " +
        "is smaller."

private fun summary(drawn: String) =
    "Drawn as $drawn at this text size, so that nothing is cut off."

/**
 * What Settings says when the Text spacing that the user chooses is drawn as a smaller level at the
 * font scale of the screen (ADR 0058, decision 4): a dialog when the level is chosen, and the
 * summary of the row for as long as it is so. The level that was chosen stays stored.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class SettingsTextSpacingLimitTest : SettingsScreenTestBase() {
    @Before fun theDeviceIsATelevision() = asTelevision()

    /** The page with the look that the Library gives the screens, at the Text [size]. */
    private fun withTextSize(size: TextSize): @Composable (@Composable () -> Unit) -> Unit = {
        CompositionLocalProvider(LocalBorealisLook provides BorealisLook(textSize = size)) {
            OnPage(it)
        }
    }

    private fun showAccessibility(size: TextSize = TextSize.Default) =
        show(
            keyboard = true,
            initialGroup = SettingsGroupId.ACCESSIBILITY,
            backdrop = withTextSize(size),
        )

    private fun choose(label: String) {
        row("Text spacing").performClick()
        composeRule.waitForIdle()
        sheetOption(label).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `choosing Wider at the largest font says that Wide is drawn, and keeps Wider`() {
        showAccessibility()

        choose("Wider")

        composeRule.onNodeWithText(TITLE_WIDER).assertExists()
        composeRule.onNodeWithText(message("Wider", "Wide")).assertExists()
        assertThat(snapshot[Settings.TEXT_SPACING]).isEqualTo("wider")

        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(TITLE_WIDER).assertDoesNotExist()
        assertThat(snapshot[Settings.TEXT_SPACING]).isEqualTo("wider")
    }

    @Test
    @Config(qualifiers = TV, fontScale = 1.69f)
    fun `choosing Wider below the limit shows no dialog`() {
        showAccessibility()

        choose("Wider")

        composeRule.onNodeWithText(TITLE_WIDER).assertDoesNotExist()
        assertThat(snapshot[Settings.TEXT_SPACING]).isEqualTo("wider")
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `choosing Wide or Normal at the largest font shows no dialog`() {
        showAccessibility()

        choose("Wide")
        composeRule.onNodeWithText("Wide spacing is limited").assertDoesNotExist()
        choose("Normal")
        composeRule.onNodeWithText("Normal spacing is limited").assertDoesNotExist()
        assertThat(snapshot[Settings.TEXT_SPACING]).isEqualTo("normal")
    }

    @Test
    @Config(qualifiers = TV, fontScale = 1.55f)
    fun `the Text size counts with the system font for the dialog`() {
        showAccessibility(TextSize.Largest)

        choose("Wider")

        composeRule.onNodeWithText(TITLE_WIDER).assertExists()
    }

    @Test
    @Config(qualifiers = TV, fontScale = 1.3f)
    fun `Largest text over a system font of 1 point 3 is below the limit`() {
        showAccessibility(TextSize.Largest)

        choose("Wider")

        composeRule.onNodeWithText(TITLE_WIDER).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `the dialog has OK in focus on a TV`() {
        showAccessibility()

        choose("Wider")

        composeRule.onNodeWithText("OK").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `a screen reader reads the title of the dialog as the name of the window`() {
        showAccessibility()

        choose("Wider")

        composeRule.dialogFrame(TITLE_WIDER).assertExists()
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `the row says that Wide is drawn for as long as Wider is limited`() {
        stored(Settings.TEXT_SPACING to "wider")
        showAccessibility()

        row("Text spacing")
        composeRule.onNodeWithText(summary("Wide")).assertExists()
    }

    @Test
    @Config(qualifiers = TV, fontScale = 1.69f)
    fun `the row has no summary below the limit`() {
        stored(Settings.TEXT_SPACING to "wider")
        showAccessibility()

        row("Text spacing")
        composeRule.onNodeWithText(summary("Wide")).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun `the row has no summary for a level that is drawn as chosen`() {
        stored(Settings.TEXT_SPACING to "wide")
        showAccessibility()

        row("Text spacing")
        composeRule.onNodeWithText(summary("Wide")).assertDoesNotExist()
        composeRule.onNodeWithText(summary("Normal")).assertDoesNotExist()
    }
}
