package io.github.magalz.borealis.library.hosts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.testing.StaysAsSnackbar
import io.github.magalz.borealis.designsystem.testing.assertSnackbarIsAsWideAsItsText
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class HostsSnackbarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `a message on the Hosts screen is as wide as its text`() {
        val snackbars = SnackbarHostState()
        composeRule.setContent {
            StaysAsSnackbar(snackbars, MESSAGE)
            HostsScreen(
                state = withHosts,
                formFactor = FormFactor.Phone,
                snackbarHostState = snackbars,
                onHostSelected = {},
                onContinue = {},
                onWake = {},
                onForget = {},
                onDetails = {},
                onDetailsDismissed = {},
                onAddHost = {},
                onSettings = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.assertSnackbarIsAsWideAsItsText(MESSAGE)
    }

    private companion object {
        const val MESSAGE = "Wake-on-LAN sent."
    }
}
