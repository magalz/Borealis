package io.github.magalz.borealis.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.privacy.PrivacyActions
import io.github.magalz.borealis.settings.privacy.PrivacyDialogActions
import io.github.magalz.borealis.settings.privacy.PrivacyScreenContent
import io.github.magalz.borealis.settings.privacy.PrivacyUiState
import io.github.magalz.borealis.settings.testing.InGlassHost
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings and Privacy and diagnostics at a system font scale of 2.0, the largest that Android
 * offers (WCAG 1.4.4: text resizes to 200 percent without loss of content). Every text must fit
 * where it is, or the pane scrolls: none runs past its width, is cut off at the bottom or ends in
 * an ellipsis.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class SettingsLargeTextFitTest(font: FontChoice) : SettingsScreenTestBase() {
    private val look = BorealisLook(font = font)

    private fun showSettings(formFactor: FormFactor, group: SettingsGroupId) {
        if (formFactor == FormFactor.Tv) asTelevision()
        show(keyboard = formFactor != FormFactor.Phone, initialGroup = group) { content ->
            InGlassHost(formFactor, look, BackgroundStyle.Off, content)
        }
    }

    private fun assertFits() {
        composeRule.assertNoClippedText()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Stream settings on a TV fit`() {
        showSettings(FormFactor.Tv, SettingsGroupId.STREAM)

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Accessibility settings on a TV fit`() {
        showSettings(FormFactor.Tv, SettingsGroupId.ACCESSIBILITY)

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `About on a TV fits`() {
        showSettings(FormFactor.Tv, SettingsGroupId.ABOUT)

        assertFits()
    }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = SCALE)
    fun `Appearance settings on a handheld fit`() {
        showSettings(FormFactor.Handheld, SettingsGroupId.APPEARANCE)

        assertFits()
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `the sections of the settings on a phone fit`() {
        showSettings(FormFactor.Phone, SettingsGroupId.STREAM)
        for (title in listOf("STREAM", "ADVANCED", "APPEARANCE", "ACCESSIBILITY", "ABOUT")) {
            composeRule.onAllNodesWithText(title)[0].performScrollTo().performClick()
            composeRule.waitForIdle()
        }

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `a choice sheet on a TV fits`() {
        showSettings(FormFactor.Tv, SettingsGroupId.STREAM)
        composeRule.onNodeWithText("Frame pacing").performClick()
        composeRule.waitForIdle()

        assertFits()
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `Privacy on a phone fits`() {
        showPrivacy(FormFactor.Phone)

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Privacy on a TV fits`() {
        showPrivacy(FormFactor.Tv)

        assertFits()
    }

    private fun showPrivacy(formFactor: FormFactor) {
        composeRule.setContent {
            InGlassHost(formFactor, look, BackgroundStyle.Off) {
                PrivacyScreenContent(
                    PrivacyUiState(),
                    SnackbarHostState(),
                    PrivacyActions(
                        {},
                        {},
                        {},
                        {},
                        {},
                        PrivacyDialogActions({}, {}, {}, { _: File, _: String -> }),
                    ),
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries

        private const val SCALE = 2f
    }
}
