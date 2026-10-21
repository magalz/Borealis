package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The secondary text of the Settings screens is on glass, so it has the glass colours: over Flowing
 * light at 40 percent and 8dp, ink-3 is `#A8ACB1`. Where the glass is solid it has the tokens.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class SettingsGlassTextTest : SettingsScreenTestBase() {
    private val glassInk3 = Color(0xFFA8ACB1)
    private val privacySummary = "Logs, diagnostic bundle, audit trail"

    @Before fun theBackgroundIsItsStill() = noAnimations()

    private fun showWith(look: BorealisLook) {
        show { content ->
            InGlassHost(FormFactor.Phone, look, BackgroundStyle.FlowingLight, content)
        }
    }

    @Test
    fun `the summary of the Privacy row on a phone is the glass ink-3`() {
        showWith(BorealisLook())

        row("Privacy and diagnostics")
        assertThat(composeRule.textColor(privacySummary)).isEqualTo(glassInk3)
    }

    @Test
    fun `the summary of the Privacy row on a phone is the token ink-3 where the glass is off`() {
        showWith(BorealisLook(transparency = Transparency.Off))

        row("Privacy and diagnostics")
        assertThat(composeRule.textColor(privacySummary)).isEqualTo(BorealisColors().ink3)
    }
}
