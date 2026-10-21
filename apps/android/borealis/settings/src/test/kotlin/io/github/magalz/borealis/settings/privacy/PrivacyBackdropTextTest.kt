package io.github.magalz.borealis.settings.privacy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.components.BackdropTextHalo
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.InGlassHost
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The title in the bar of Privacy and diagnostics stands on the background and has the halo. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class PrivacyBackdropTextTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the title of the phone screen is on the background`() {
        composeRule.setContent {
            InGlassHost(FormFactor.Phone) {
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

        composeRule
            .onNode(
                hasText("Privacy and diagnostics") and
                    SemanticsMatcher.expectValue(BackdropTextHalo, true),
                useUnmergedTree = true,
            )
            .assertExists()
    }
}
