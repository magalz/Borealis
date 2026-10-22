package io.github.magalz.borealis.designsystem.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the theme does with the visual choices of the [BorealisLook] (ADR 0057, decision 11): the
 * colours of high contrast and of a colour-blind palette, and the text size.
 */
@RunWith(AndroidJUnit4::class)
class BorealisThemeLookEffectsTest {
    @get:Rule val composeRule = createComposeRule()

    private var colors: BorealisColors? = null
    private var scheme: ColorScheme? = null
    private var contentColor: Color? = null
    private var density: Density? = null

    @Composable
    private fun Probe() {
        colors = LocalBorealisColors.current
        scheme = MaterialTheme.colorScheme
        contentColor = LocalContentColor.current
        density = LocalDensity.current
    }

    private fun show(look: BorealisLook) {
        composeRule.setContent { BorealisTheme(look = look) { Probe() } }
        composeRule.waitForIdle()
    }

    @Test
    fun `a theme with no choices provides the colours of the tokens`() {
        show(BorealisLook())

        assertThat(colors).isEqualTo(BorealisColors())
        assertThat(scheme?.onSurface).isEqualTo(BorealisColors().ink)
        assertThat(scheme?.primary).isEqualTo(BorealisColors().accent)
        assertThat(scheme?.outline).isEqualTo(BorealisColors().lineStrong)
    }

    @Test
    fun `high contrast gives the content the high contrast colours`() {
        show(BorealisLook(highContrast = true))

        val high = BorealisColors().withHighContrast(AccentChoice.Violet)
        assertThat(colors).isEqualTo(high)
        assertThat(contentColor).isEqualTo(high.ink)
    }

    @Test
    fun `high contrast gives the Material components the high contrast colours too`() {
        show(BorealisLook(highContrast = true))

        val high = BorealisColors().withHighContrast(AccentChoice.Violet)
        assertThat(scheme?.onSurface).isEqualTo(high.ink)
        assertThat(scheme?.onSurfaceVariant).isEqualTo(high.ink2)
        assertThat(scheme?.primary).isEqualTo(high.accent)
        assertThat(scheme?.outline).isEqualTo(high.lineStrong)
        assertThat(scheme?.outlineVariant).isEqualTo(high.line)
        assertThat(scheme?.error).isEqualTo(high.dangerInk)
    }

    @Test
    fun `a colour-blind palette gives the content the status colours of the palette`() {
        show(BorealisLook(colourVision = ColourVision.RedGreen))

        assertThat(colors).isEqualTo(BorealisColors().withColourVision(ColourVision.RedGreen))
        assertThat(scheme?.error).isEqualTo(colors?.dangerInk)
    }

    @Test
    fun `an accent gives the content and Material the colours of its set`() {
        show(BorealisLook(accent = AccentChoice.Teal))

        assertThat(colors).isEqualTo(borealisColors(BorealisLook(accent = AccentChoice.Teal)))
        assertThat(colors?.accent).isEqualTo(Color(0xFF4BECD0))
        assertThat(scheme?.primary).isEqualTo(Color(0xFF4BECD0))
        assertThat(scheme?.onPrimary).isEqualTo(Color(0xFF001713))
        assertThat(scheme?.secondary).isEqualTo(Color(0xFFA2FCE8))
        assertThat(scheme?.inversePrimary).isEqualTo(Color(0xFF027464))
    }

    @Test
    fun `a change of the accent reaches the colours of content that is already shown`() {
        var look by mutableStateOf(BorealisLook())
        composeRule.setContent { BorealisTheme(look = look) { Probe() } }
        composeRule.waitForIdle()
        assertThat(colors?.accent).isEqualTo(BorealisColors().accent)

        look = BorealisLook(accent = AccentChoice.White)
        composeRule.waitForIdle()

        assertThat(colors?.accent).isEqualTo(Color(0xFFEFF3FF))
        assertThat(scheme?.primary).isEqualTo(Color(0xFFEFF3FF))
        assertThat(contentColor).isEqualTo(colors?.ink)
    }

    @Test
    fun `a change of look reaches the colours of content that is already shown`() {
        var look by mutableStateOf(BorealisLook())
        composeRule.setContent { BorealisTheme(look = look) { Probe() } }
        composeRule.waitForIdle()
        assertThat(colors).isEqualTo(BorealisColors())

        look = BorealisLook(highContrast = true, colourVision = ColourVision.BlueYellow)
        composeRule.waitForIdle()

        assertThat(colors).isEqualTo(borealisColors(look))
        assertThat(scheme?.onSurface).isEqualTo(colors?.ink)
    }

    @Test
    fun `a theme inside another keeps the colours of the look of the outer one`() {
        val look = BorealisLook(highContrast = true)
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, look) { BorealisTheme(FormFactor.Tv) { Probe() } }
        }
        composeRule.waitForIdle()

        assertThat(colors).isEqualTo(borealisColors(look))
    }

    @Test
    @Config(fontScale = 1f)
    fun `the default text size leaves the font scale as it is`() {
        show(BorealisLook(textSize = TextSize.Default))

        assertThat(density?.fontScale).isEqualTo(1f)
    }

    @Test
    @Config(fontScale = 1f)
    fun `a larger text size multiplies the font scale`() {
        show(BorealisLook(textSize = TextSize.Larger))

        assertThat(density?.fontScale).isWithin(EPSILON).of(1.15f)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `the text size is multiplied by the font scale of the system`() {
        show(BorealisLook(textSize = TextSize.Largest))

        assertThat(density?.fontScale).isWithin(EPSILON).of(1.3f * 1.3f)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `the text size changes sp and leaves dp as they are`() {
        val system =
            ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        show(BorealisLook(textSize = TextSize.Largest))

        assertThat(density?.density).isEqualTo(system)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `a theme inside another does not multiply the text size again`() {
        val look = BorealisLook(textSize = TextSize.Largest)
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, look) { BorealisTheme(FormFactor.Tv) { Probe() } }
        }
        composeRule.waitForIdle()

        assertThat(density?.fontScale).isWithin(EPSILON).of(1.3f * 1.3f)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `a theme inside another can be given a text size of its own`() {
        composeRule.setContent {
            BorealisTheme(look = BorealisLook(textSize = TextSize.Largest)) {
                BorealisTheme(look = BorealisLook(textSize = TextSize.Larger)) { Probe() }
            }
        }
        composeRule.waitForIdle()

        assertThat(density?.fontScale).isWithin(EPSILON).of(1.3f * 1.15f)
    }

    @Test
    @Config(fontScale = 1f)
    fun `a change of the text size reaches content that is already shown`() {
        var look by mutableStateOf(BorealisLook())
        composeRule.setContent { BorealisTheme(look = look) { Probe() } }
        composeRule.waitForIdle()
        assertThat(density?.fontScale).isEqualTo(1f)

        look = BorealisLook(textSize = TextSize.Largest)
        composeRule.waitForIdle()

        assertThat(density?.fontScale).isWithin(EPSILON).of(1.3f)

        look = BorealisLook(textSize = TextSize.Default)
        composeRule.waitForIdle()

        assertThat(density?.fontScale).isWithin(EPSILON).of(1f)
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
