package io.github.magalz.borealis.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.testing.OverMarker
import io.github.magalz.borealis.designsystem.testing.assertMarkerShows
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.audit.AuditScreen
import io.github.magalz.borealis.settings.audit.AuditUiState
import io.github.magalz.borealis.settings.privacy.PrivacyActions
import io.github.magalz.borealis.settings.privacy.PrivacyDialogActions
import io.github.magalz.borealis.settings.privacy.PrivacyScreenContent
import io.github.magalz.borealis.settings.privacy.PrivacyUiState
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The shell draws the background behind Settings, Privacy and diagnostics and the Audit trail, so a
 * screen must not paint a page of its own over it: each is shown over a marker colour that its
 * margin has to leave visible.
 */
@RunWith(AndroidJUnit4::class)
class ScreensOverBackgroundTest : SettingsScreenTestBase() {
    @Test
    @Config(qualifiers = PHONE)
    fun `Settings leaves the background to the shell on a phone`() {
        show(backdrop = { OverMarker(it) })

        composeRule.assertMarkerShows()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Settings leaves the background to the shell on a TV`() {
        asTelevision()
        show(keyboard = true, backdrop = { OverMarker(it) })

        composeRule.assertMarkerShows()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Privacy and diagnostics leaves the background to the shell on a phone`() {
        composeRule.setContent {
            OverMarker {
                BorealisTheme(FormFactor.Phone) {
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
        }
        composeRule.waitForIdle()

        composeRule.assertMarkerShows()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Audit trail leaves the background to the shell`() {
        composeRule.setContent {
            OverMarker {
                AuditScreen(
                    state = AuditUiState(false),
                    formFactor = FormFactor.Phone,
                    onEntrySelected = {},
                    onDetailDismissed = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.assertMarkerShows()
    }
}
