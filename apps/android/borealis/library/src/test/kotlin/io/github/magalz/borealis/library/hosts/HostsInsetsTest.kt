package io.github.magalz.borealis.library.hosts

import android.graphics.Insets
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.addhost.AddHostScreen
import io.github.magalz.borealis.library.addhost.AddHostUiState
import io.github.magalz.borealis.library.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The activity is edge-to-edge, so the screens keep their content out of the system bars. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class HostsInsetsTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the Hosts screen keeps its title below the status bar`() {
        composeRule.setContent {
            HostsScreen(
                state = withHosts,
                formFactor = FormFactor.Phone,
                snackbarHostState = SnackbarHostState(),
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
        applyStatusBar()

        assertThat(composeRule.onNodeWithText("Hosts").getUnclippedBoundsInRoot().top)
            .isAtLeast(STATUS_BAR)
    }

    @Test
    fun `the Add host screen keeps its title below the status bar`() {
        composeRule.setContent {
            AddHostScreen(
                state = AddHostUiState(),
                formFactor = FormFactor.Phone,
                onAddressChanged = {},
                onSubmit = {},
                onCancel = {},
            )
        }
        applyStatusBar()

        assertThat(
                composeRule
                    .onNode(hasText("Add host") and hasNoClickAction())
                    .getUnclippedBoundsInRoot()
                    .top
            )
            .isAtLeast(STATUS_BAR)
    }

    private fun applyStatusBar() {
        composeRule.runOnUiThread {
            val window = composeRule.activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // One dp is two pixels at xhdpi.
            val insets =
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, STATUS_BAR_PX, 0, 0))
                    .build()
            window.decorView.dispatchApplyWindowInsets(insets)
        }
        composeRule.waitForIdle()
    }

    private companion object {
        val STATUS_BAR = 40.dp
        const val STATUS_BAR_PX = 80
    }
}
