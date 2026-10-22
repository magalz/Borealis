package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The theme gives the type scale the Text spacing of the look (ADR 0058, decision 4): the Material
 * styles and the Borealis ones, on every screen under it, and a change of the setting reaches the
 * text that is already shown.
 */
@RunWith(AndroidJUnit4::class)
class TextSpacingThemeTest {
    @get:Rule val composeRule = createComposeRule()

    private var typography: Typography? = null
    private var type: BorealisType? = null

    @Composable
    private fun Probe() {
        typography = MaterialTheme.typography
        type = LocalBorealisType.current
    }

    private fun emOf(unit: TextUnit, style: TextStyle): Float =
        if (unit.type == TextUnitType.Em) unit.value else unit.value / style.fontSize.value

    @Test
    fun `a theme given Wide spaces every style of both scales by the table`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(textSpacing = TextSpacing.Wide)) { Probe() }
        }
        composeRule.waitForIdle()

        val styles =
            with(checkNotNull(typography)) { listOf(bodyLarge, titleLarge, labelSmall) } +
                with(checkNotNull(type)) { listOf(kicker, pin, rail) }
        for (style in styles) {
            assertThat(emOf(style.letterSpacing, style)).isAtLeast(TextSpacing.Wide.letterEm)
            assertThat(emOf(style.lineHeight, style)).isAtLeast(TextSpacing.Wide.lineHeightEm)
        }
    }

    @Test
    fun `a theme given no spacing keeps the design of the type scale`() {
        composeRule.setContent { BorealisTheme(FormFactor.Tv) { Probe() } }
        composeRule.waitForIdle()

        assertThat(checkNotNull(typography)).isEqualTo(borealisTypography(FormFactor.Tv))
        assertThat(checkNotNull(type).kicker).isEqualTo(borealisType(FormFactor.Tv).kicker)
    }

    @Test
    fun `a change of the setting reaches the text that is already shown`() {
        var spacing by mutableStateOf(TextSpacing.Normal)
        composeRule.setContent {
            BorealisTheme(look = BorealisLook(textSpacing = spacing)) { Probe() }
        }
        composeRule.waitForIdle()
        assertThat(checkNotNull(typography).bodyLarge.lineHeight.isSpecified).isFalse()

        spacing = TextSpacing.Wide
        composeRule.waitForIdle()
        val wide = checkNotNull(typography).bodyLarge
        assertThat(emOf(wide.letterSpacing, wide)).isWithin(EPSILON).of(0.12f)
        assertThat(emOf(checkNotNull(type).kicker.letterSpacing, checkNotNull(type).kicker))
            .isWithin(EPSILON)
            .of(0.12f)

        spacing = TextSpacing.Wider
        composeRule.waitForIdle()
        val wider = checkNotNull(typography).bodyLarge
        assertThat(emOf(wider.letterSpacing, wider)).isWithin(EPSILON).of(0.18f)

        spacing = TextSpacing.Normal
        composeRule.waitForIdle()
        assertThat(checkNotNull(typography).bodyLarge.lineHeight.isSpecified).isFalse()
    }

    @Test
    fun `a theme inside another keeps the spacing of the look of the outer one`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(textSpacing = TextSpacing.Wider)) {
                BorealisTheme(FormFactor.Tv) { Probe() }
            }
        }
        composeRule.waitForIdle()

        val body = checkNotNull(typography).bodyLarge
        assertThat(emOf(body.letterSpacing, body)).isWithin(EPSILON).of(0.18f)
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
