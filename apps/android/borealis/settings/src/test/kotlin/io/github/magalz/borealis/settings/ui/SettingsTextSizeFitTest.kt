package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.dumpFrame
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Settings screen at the largest text: the Text size setting at 130 percent over a system font
 * scale of 1.3, which is 1.69 of the text that the screen is laid out for (ADR 0057, decision 11).
 * Every text must fit where it is, or the pane scrolls: none runs past its width, is cut off at the
 * bottom or ends in an ellipsis.
 */
@RunWith(AndroidJUnit4::class)
class SettingsTextSizeFitTest : SettingsScreenTestBase() {
    private val largest = BorealisLook(textSize = TextSize.Largest)

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

    private fun assertFits(name: String) {
        composeRule.dumpFrame("fit_$name")
        composeRule.assertNoClippedText()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Stream settings on a TV fit`() {
        showLargest(FormFactor.Tv, SettingsGroupId.STREAM)

        assertFits("settings_tv_stream")
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Advanced settings on a TV fit`() {
        showLargest(FormFactor.Tv, SettingsGroupId.ADVANCED)

        assertFits("settings_tv_advanced")
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Appearance settings on a TV fit`() {
        showLargest(FormFactor.Tv, SettingsGroupId.APPEARANCE)

        assertFits("settings_tv_appearance")
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Accessibility settings on a TV fit`() {
        showLargest(FormFactor.Tv, SettingsGroupId.ACCESSIBILITY)

        assertFits("settings_tv_accessibility")
    }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `About on a TV fits`() {
        showLargest(FormFactor.Tv, SettingsGroupId.ABOUT)

        assertFits("settings_tv_about")
    }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = SYSTEM_SCALE)
    fun `Stream settings on a handheld fit`() {
        showLargest(FormFactor.Handheld, SettingsGroupId.STREAM)

        assertFits("settings_handheld_stream")
    }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = SYSTEM_SCALE)
    fun `Accessibility settings on a handheld fit`() {
        showLargest(FormFactor.Handheld, SettingsGroupId.ACCESSIBILITY)

        assertFits("settings_handheld_accessibility")
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = SYSTEM_SCALE)
    fun `the sections of the settings on a phone fit`() {
        showLargest(FormFactor.Phone, SettingsGroupId.STREAM)
        for (title in listOf("STREAM", "ADVANCED", "APPEARANCE", "ACCESSIBILITY")) {
            composeRule.onAllNodesWithText(title)[0].performScrollTo().performClick()
            composeRule.waitForIdle()
        }

        assertFits("settings_phone_sections")
    }

    private companion object {
        const val SYSTEM_SCALE = 1.3f
    }
}
