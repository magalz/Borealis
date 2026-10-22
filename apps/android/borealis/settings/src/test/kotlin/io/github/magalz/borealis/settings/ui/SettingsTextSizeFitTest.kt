package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.settings.testing.InGlassHost
import io.github.magalz.borealis.settings.testing.dialogFrame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Settings screen at the largest text: the Text size setting at 130 percent over a system font
 * scale of 1.3, which is 1.69 of the text that the screen is laid out for (ADR 0057, decision 11).
 * Every text must fit where it is, or the pane scrolls: none runs past its width, is cut off at the
 * bottom or ends in an ellipsis.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class SettingsTextSizeFitTest(font: FontChoice) : SettingsScreenTestBase() {
    private val largest = BorealisLook(textSize = TextSize.Largest, font = font)

    private fun showLargest(
        formFactor: FormFactor,
        group: SettingsGroupId,
        keyboard: Boolean = formFactor != FormFactor.Phone,
    ) {
        if (formFactor == FormFactor.Tv) asTelevision()
        show(keyboard = keyboard, initialGroup = group) { content ->
            InGlassHost(formFactor, largest, BackgroundStyle.Off, content)
        }
    }

    private fun assertFits() {
        composeRule.assertNoClippedText()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Stream settings on a TV fit`() {
        showLargest(FormFactor.Tv, SettingsGroupId.STREAM)

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Advanced settings on a TV fit`() {
        showLargest(FormFactor.Tv, SettingsGroupId.ADVANCED)

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Appearance settings on a TV fit`() {
        showLargest(FormFactor.Tv, SettingsGroupId.APPEARANCE)

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Accessibility settings on a TV fit`() {
        showLargest(FormFactor.Tv, SettingsGroupId.ACCESSIBILITY)

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `About on a TV fits`() {
        showLargest(FormFactor.Tv, SettingsGroupId.ABOUT)

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `the link dialog with its QR code on a TV fits`() {
        showLargest(FormFactor.Tv, SettingsGroupId.ABOUT)
        composeRule.onNodeWithText("Privacy policy").performClick()
        composeRule.waitForIdle()
        composeRule.dialogFrame("Privacy policy").assertExists()

        assertFits()
    }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = SYSTEM_SCALE)
    fun `Stream settings on a handheld fit`() {
        showLargest(FormFactor.Handheld, SettingsGroupId.STREAM)

        assertFits()
    }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = SYSTEM_SCALE)
    fun `Accessibility settings on a handheld fit`() {
        showLargest(FormFactor.Handheld, SettingsGroupId.ACCESSIBILITY)

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `a choice sheet on a TV fits`() {
        showLargest(FormFactor.Tv, SettingsGroupId.STREAM)
        composeRule.onNodeWithText("Frame pacing").performClick()
        composeRule.waitForIdle()

        assertFits()
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = SYSTEM_SCALE)
    fun `the sections of the settings on a phone fit`() {
        showLargest(FormFactor.Phone, SettingsGroupId.STREAM)
        for (title in listOf("STREAM", "ADVANCED", "APPEARANCE", "ACCESSIBILITY")) {
            composeRule.onAllNodesWithText(title)[0].performScrollTo().performClick()
            composeRule.waitForIdle()
        }

        assertFits()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries

        private const val SYSTEM_SCALE = 1.3f
    }
}
