package io.github.magalz.borealis.settings.privacy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.InGlassHost
import io.github.magalz.borealis.settings.testing.noAnimations
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A screenshot of Privacy and diagnostics on a phone, on glass, over the still of Northern lights:
 * `privacyglass_phone_default.png`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class PrivacyGlassScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun theBackgroundIsItsStill() = noAnimations()

    @Test
    @Config(qualifiers = PHONE)
    fun privacyPhoneDefault() {
        composeRule.setContent {
            InGlassHost(FormFactor.Phone, BorealisLook(), BackgroundStyle.NorthernLights) {
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

        captureScreenRoboImage("src/test/screenshots/privacyglass_phone_default.png")
    }
}
