package io.github.magalz.borealis.settings.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.settleWindows
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Privacy and diagnostics pane; the references live in `src/test/screenshots`,
 * named `privacy_<device>_<state>.png`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class PrivacyScreenshotTest {
    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private val actions =
        PrivacyActions(
            {},
            {},
            {},
            {},
            {},
            PrivacyDialogActions({}, {}, {}, { _: File, _: String -> }),
        )

    private fun saved(): PrivacyDialog.Saved {
        val bundle = bundleFile(folder.root)
        return PrivacyDialog.Saved(
            location = "/sdcard/Download/${bundle.name}",
            adbPullCommand = "adb pull /sdcard/Download/${bundle.name}",
            bundle = bundle,
            fileName = bundle.name,
            sizeBytes = bundle.length(),
        )
    }

    @Test
    @Config(qualifiers = PHONE)
    fun privacyPhoneDefault() {
        composeRule.setContent {
            OnPage {
                BorealisTheme(FormFactor.Phone) {
                    PrivacyScreenContent(
                        PrivacyUiState(verboseLogging = false, maskAddresses = true),
                        SnackbarHostState(),
                        actions,
                        onBack = {},
                    )
                }
            }
        }

        shoot("privacy_phone_default")
    }

    @Test
    @Config(qualifiers = TV)
    fun privacyTvPane() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                PrivacyContent(
                    PrivacyUiState(verboseLogging = true, maskAddresses = true),
                    SnackbarHostState(),
                    actions,
                    Modifier.width(640.dp),
                )
            }
        }

        composeRule.onNodeWithText("Export diagnostic bundle").requestFocus()
        shoot("privacy_tv_pane")
    }

    @Test
    @Config(qualifiers = TV)
    fun privacyTvSaved() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                PrivacyContent(
                    PrivacyUiState(dialog = saved()),
                    SnackbarHostState(),
                    actions,
                    Modifier.width(640.dp),
                )
            }
        }

        shoot("privacy_tv_saved")
    }

    @Test
    @Config(qualifiers = TV)
    fun privacyTvEraseConfirm() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                PrivacyContent(
                    PrivacyUiState(dialog = PrivacyDialog.EraseConfirmation),
                    SnackbarHostState(),
                    actions,
                    Modifier.width(640.dp),
                )
            }
        }

        shoot("privacy_tv_erase-confirm")
    }

    @Composable
    private fun Page(formFactor: FormFactor, content: @Composable () -> Unit) {
        BorealisTheme(formFactor) {
            Box(
                Modifier.fillMaxSize()
                    .background(LocalBorealisColors.current.page)
                    .padding(horizontal = 34.dp, vertical = 24.dp)
            ) {
                content()
            }
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.settleWindows()
        // The whole screen, not the root node: a dialog is a window of its own.
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }
}
