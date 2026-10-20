package io.github.magalz.borealis.settings.privacy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.StaysAsSnackbar
import io.github.magalz.borealis.settings.testing.assertSnackbarIsAsWideAsItsText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PrivacySnackbarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `a message on the Privacy pane is as wide as its text`() {
        val snackbars = SnackbarHostState()
        val actions =
            PrivacyActions(
                onVerboseLoggingChange = {},
                onMaskAddressesChange = {},
                onExport = {},
                onOpenAudit = {},
                onEraseRequested = {},
                dialogs = PrivacyDialogActions({}, {}, {}, { _, _ -> }),
            )
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                StaysAsSnackbar(snackbars, MESSAGE)
                PrivacyContent(PrivacyUiState(), snackbars, actions)
            }
        }
        composeRule.waitForIdle()

        composeRule.assertSnackbarIsAsWideAsItsText(MESSAGE)
    }

    private companion object {
        const val MESSAGE = "Audit trail erased."
    }
}
