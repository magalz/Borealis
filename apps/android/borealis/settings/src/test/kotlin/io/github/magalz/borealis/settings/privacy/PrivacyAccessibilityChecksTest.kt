package io.github.magalz.borealis.settings.privacy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.NoTouchOnTv
import io.github.magalz.borealis.designsystem.testing.assertAccessible
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.InGlassHost
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Google's Accessibility Test Framework over Privacy and diagnostics. The glass is off here
 * (`BackgroundStyle.Off`), so the contrast checks see the colours that the text really has.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyAccessibilityChecksTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun `Privacy on a phone`() {
        show(FormFactor.Phone)

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Privacy on a handheld`() {
        show(FormFactor.Handheld)

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Privacy on a TV`() {
        show(FormFactor.Tv)

        composeRule.assertAccessible(NoTouchOnTv)
    }

    private fun show(formFactor: FormFactor) {
        composeRule.setContent {
            InGlassHost(formFactor, style = BackgroundStyle.Off) {
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
}
