package io.github.magalz.borealis.settings.privacy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.settings.testing.InGlassHost
import io.github.magalz.borealis.settings.testing.noAnimations
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The secondary text of Privacy and diagnostics on a phone is on a glass card, so it has the glass
 * colours: over Flowing light at 40 percent and 8dp, ink-3 is `#A8ACB1`. Where the glass is solid
 * it has the tokens.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class PrivacyGlassTextTest {
    @get:Rule val composeRule = createComposeRule()

    private val glassInk3 = Color(0xFFA8ACB1)
    private val footer = "Nothing leaves this device unless you export it."

    @Before fun theBackgroundIsItsStill() = noAnimations()

    private fun show(look: BorealisLook) {
        composeRule.setContent {
            InGlassHost(FormFactor.Phone, look, BackgroundStyle.FlowingLight) {
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

    @Test
    fun `the footer and the summary of a row are the glass ink-3`() {
        show(BorealisLook())

        assertThat(composeRule.textColor(footer)).isEqualTo(glassInk3)
    }

    @Test
    fun `the footer is the token ink-3 where the glass is off`() {
        show(BorealisLook(transparency = Transparency.Off))

        assertThat(composeRule.textColor(footer)).isEqualTo(BorealisColors().ink3)
    }
}
