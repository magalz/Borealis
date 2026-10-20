package io.github.magalz.borealis.library.apps

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.StaysAsSnackbar
import io.github.magalz.borealis.library.testing.assertSnackbarIsAsWideAsItsText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AppsSnackbarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `a message on the Apps screen is as wide as its text`() {
        val snackbars = SnackbarHostState()
        composeRule.setContent {
            StaysAsSnackbar(snackbars, MESSAGE)
            AppsScreen(
                state = AppsUiState(loading = false, hostName = "Desk PC"),
                formFactor = FormFactor.Phone,
                snackbarHostState = snackbars,
                onBack = {},
                onAppSelected = {},
                onActions = {},
                onActionChosen = {},
                onActionsDismissed = {},
                onDetailsDismissed = {},
                onToggleShowHidden = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.assertSnackbarIsAsWideAsItsText(MESSAGE)
    }

    private companion object {
        const val MESSAGE = "App quit."
    }
}
