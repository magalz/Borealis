package io.github.magalz.borealis.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.FitCase
import io.github.magalz.borealis.designsystem.testing.HANDHELD_NO_TOUCH
import io.github.magalz.borealis.designsystem.testing.PHONE
import io.github.magalz.borealis.designsystem.testing.TV
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.assumeNormalSpacing
import io.github.magalz.borealis.designsystem.testing.fitCases
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
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
 * an ellipsis. The Text spacing of the runs is Normal, Wide or Wider, and the look draws the level
 * that the font scale lets it (ADR 0058, decision 4), so none fails. The Audio settings, and the
 * Stream ones on a TV, also fit at the Text size Largest over the system font of 2.0, a combined
 * font scale of 2.6, at Normal.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class SettingsLargeTextFitTest(private val case: FitCase) : SettingsScreenTestBase() {
    private val look = BorealisLook(font = case.font, textSpacing = case.spacing)
    private val largest = look.copy(textSize = TextSize.Largest)

    private fun showSettings(
        formFactor: FormFactor,
        group: SettingsGroupId,
        look: BorealisLook = this.look,
    ) {
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
    fun `Stream settings on a TV fit at the largest text size`() {
        case.assumeNormalSpacing()
        showSettings(FormFactor.Tv, SettingsGroupId.STREAM, largest)
        // The Bitrate slider is the row that ran out of height in the components rig at 2.6.
        row("Bitrate").assertExists()

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
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Audio settings on a TV fit`() {
        showSettings(FormFactor.Tv, SettingsGroupId.AUDIO)
        assertAudioEffectsShown()

        assertFits()
    }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Audio settings on a TV fit at the largest text size`() {
        case.assumeNormalSpacing()
        showSettings(FormFactor.Tv, SettingsGroupId.AUDIO, largest)
        assertAudioEffectsShown()

        assertFits()
    }

    @Test
    @Config(qualifiers = HANDHELD_NO_TOUCH, fontScale = SCALE)
    fun `Audio settings on a handheld fit`() {
        showSettings(FormFactor.Handheld, SettingsGroupId.AUDIO)
        assertAudioEffectsShown()

        assertFits()
    }

    @Test
    @Config(qualifiers = HANDHELD_NO_TOUCH, fontScale = SCALE)
    fun `Audio settings on a handheld fit at the largest text size`() {
        case.assumeNormalSpacing()
        showSettings(FormFactor.Handheld, SettingsGroupId.AUDIO, largest)
        assertAudioEffectsShown()

        assertFits()
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `the Audio section on a phone fits`() {
        showSettings(FormFactor.Phone, SettingsGroupId.STREAM)
        openAudioSection()
        assertAudioEffectsShown()

        assertFits()
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `the Audio section on a phone fits at the largest text size`() {
        case.assumeNormalSpacing()
        showSettings(FormFactor.Phone, SettingsGroupId.STREAM, largest)
        openAudioSection()
        assertAudioEffectsShown()

        assertFits()
    }

    @Test
    @Config(qualifiers = HANDHELD_NO_TOUCH, fontScale = SCALE)
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
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `the Touch sheet on a TV fits at the largest text size`() {
        case.assumeNormalSpacing()
        showSettings(FormFactor.Tv, SettingsGroupId.INPUT, largest)
        composeRule.onNodeWithText("Touch").performClick()
        composeRule.waitForIdle()
        // The sheet has its three entries, and the Native one has its whole summary.
        composeRule.onNodeWithText(NATIVE_SUMMARY).assertExists()

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
        fun cases(): List<FitCase> = fitCases()

        private const val SCALE = 2f
    }
}
