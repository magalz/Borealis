package io.github.magalz.borealis.settings.privacy

import androidx.compose.material3.SnackbarHostState
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.FlowingLightGlassCases
import io.github.magalz.borealis.settings.testing.InGlassHost
import io.github.magalz.borealis.settings.testing.Probe
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The rows of Privacy and diagnostics on a phone are on one glass card. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class PrivacyPhoneGlassTest : FlowingLightGlassCases() {
    // In the padding of the card, which is at 8dp, 2dp in from its edge, beside the first row; the
    // background is read 2dp outside the card.
    override val probes = listOf(Probe("Verbose logging", 10f, outside = 6f))

    override fun showScreen(look: BorealisLook, style: BackgroundStyle) {
        composeRule.setContent {
            InGlassHost(FormFactor.Phone, look, style) {
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
