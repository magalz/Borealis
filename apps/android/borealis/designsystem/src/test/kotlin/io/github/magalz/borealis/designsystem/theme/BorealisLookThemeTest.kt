package io.github.magalz.borealis.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The theme carries the [BorealisLook] to every screen under it (ADR 0057, decision 12). */
@RunWith(AndroidJUnit4::class)
class BorealisLookThemeTest {
    @get:Rule val composeRule = createComposeRule()

    private val chosen =
        BorealisLook(
            transparency = Transparency.Reduced,
            glassTintPercent = 70,
            glassBlurDp = 32,
            reduceMotion = ReduceMotion.On,
            thickFocus = true,
            textSize = TextSize.Larger,
            highContrast = true,
            colourVision = ColourVision.RedGreen,
            font = FontChoice.AtkinsonNext,
            accent = AccentChoice.Teal,
            backgroundIntensity = BackgroundIntensity.Half,
            cardDensity = CardDensity.Compact,
            textSpacing = TextSpacing.Wide,
        )

    private var seen: BorealisLook? = null

    @Composable
    private fun Probe() {
        seen = LocalBorealisLook.current
    }

    @Test
    fun `a theme given no look provides the defaults`() {
        composeRule.setContent { BorealisTheme { Probe() } }
        composeRule.waitForIdle()

        assertThat(seen).isEqualTo(BorealisLook())
    }

    @Test
    fun `the theme provides the look it is given to the content`() {
        composeRule.setContent { BorealisTheme(look = chosen) { Probe() } }
        composeRule.waitForIdle()

        assertThat(seen).isEqualTo(chosen)
    }

    @Test
    fun `a change of the look reaches the content that is already shown`() {
        var look by mutableStateOf(BorealisLook())
        composeRule.setContent { BorealisTheme(look = look) { Probe() } }
        composeRule.waitForIdle()
        assertThat(seen).isEqualTo(BorealisLook())

        look = chosen
        composeRule.waitForIdle()

        assertThat(seen).isEqualTo(chosen)
    }

    @Test
    fun `a theme inside another keeps the look of the outer one`() {
        // The screens of the app each wear their own theme, inside the one of the shell.
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, chosen) { BorealisTheme(FormFactor.Tv) { Probe() } }
        }
        composeRule.waitForIdle()

        assertThat(seen).isEqualTo(chosen)
    }

    @Test
    fun `a theme inside another can be given a look of its own`() {
        val inner = BorealisLook(highContrast = true)
        composeRule.setContent {
            BorealisTheme(look = chosen) { BorealisTheme(look = inner) { Probe() } }
        }
        composeRule.waitForIdle()

        assertThat(seen).isEqualTo(inner)
    }
}
