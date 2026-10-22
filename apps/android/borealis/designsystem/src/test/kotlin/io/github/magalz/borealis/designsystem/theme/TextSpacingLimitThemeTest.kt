package io.github.magalz.borealis.designsystem.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.testing.cardFitCases
import io.github.magalz.borealis.designsystem.testing.fitCases
import io.github.magalz.borealis.designsystem.testing.textLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The theme draws the Text spacing that the look chose only while the text is small enough for it:
 * Wider is drawn as Wide from a font scale of 2.0, and Wide and Normal are always drawn as chosen
 * (ADR 0058, decision 4). The scale is the one that the text is drawn at, the system font scale
 * times the Text size of the look. What the look chose does not change, so the level comes back
 * when the scale falls again.
 */
@RunWith(AndroidJUnit4::class)
class TextSpacingLimitThemeTest {
    @get:Rule val composeRule = createComposeRule()

    private var drawn: TextSpacing? = null
    private var typography: Typography? = null
    private var type: BorealisType? = null
    private var density: Density? = null

    private var systemScale by mutableFloatStateOf(1f)
    private var chosen by mutableStateOf(TextSpacing.Normal)
    private var textSize by mutableStateOf(TextSize.Default)
    private var shown = false

    @Composable
    private fun Probe() {
        drawn = LocalBorealisLook.current.textSpacing
        typography = MaterialTheme.typography
        type = LocalBorealisType.current
        density = LocalDensity.current
    }

    /** The theme of a TV, given the look [level] at the system font [scale] and the [size]. */
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        val system = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(system.density, systemScale)) {
            BorealisTheme(
                FormFactor.Tv,
                BorealisLook(textSpacing = chosen, textSize = textSize),
                content,
            )
        }
    }

    /**
     * Shows the theme with [level] chosen, at the system font [scale] and the Text [size]. A test
     * has one composition, so a second call changes the choice and the scale of the first.
     */
    private fun show(level: TextSpacing, scale: Float, size: TextSize = TextSize.Default) {
        chosen = level
        systemScale = scale
        textSize = size
        if (!shown) {
            shown = true
            composeRule.setContent { Themed { Probe() } }
        }
        composeRule.waitForIdle()
    }

    private fun emOf(unit: TextUnit, style: TextStyle): Float =
        if (unit.type == TextUnitType.Em) unit.value else unit.value / style.fontSize.value

    private fun assertDrawnWith(level: TextSpacing) {
        assertThat(drawn).isEqualTo(level)
        val body = checkNotNull(typography).bodyLarge
        val kicker = checkNotNull(type).kicker
        if (level == TextSpacing.Normal) {
            assertThat(typography).isEqualTo(borealisTypography(FormFactor.Tv))
        } else {
            assertThat(emOf(body.letterSpacing, body)).isWithin(EPSILON).of(level.letterEm)
            assertThat(emOf(kicker.letterSpacing, kicker)).isWithin(EPSILON).of(level.letterEm)
            assertThat(emOf(body.lineHeight, body)).isAtLeast(level.lineHeightEm)
        }
    }

    @Test
    fun `every level is drawn as chosen below a font scale of 2 point 0`() {
        for (scale in listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.69f, 1.7f, 1.8f, 1.95f, 1.99f)) {
            for (level in TextSpacing.entries) {
                show(level, scale)

                assertDrawnWith(level)
            }
        }
    }

    @Test
    fun `Wider is drawn as Wide from a font scale of 2 point 0, and the others stay`() {
        for (scale in listOf(2f, 2.2f, 2.6f)) {
            show(TextSpacing.Wider, scale)
            assertDrawnWith(TextSpacing.Wide)

            show(TextSpacing.Wide, scale)
            assertDrawnWith(TextSpacing.Wide)

            show(TextSpacing.Normal, scale)
            assertDrawnWith(TextSpacing.Normal)
        }
    }

    @Test
    fun `the level that the look chose comes back when the font scale falls again`() {
        show(TextSpacing.Wider, 2f)
        assertDrawnWith(TextSpacing.Wide)

        show(TextSpacing.Wider, 1.8f)
        assertDrawnWith(TextSpacing.Wider)

        show(TextSpacing.Wider, 2.6f)
        assertDrawnWith(TextSpacing.Wide)

        show(TextSpacing.Wider, 1f)
        assertDrawnWith(TextSpacing.Wider)
    }

    @Test
    fun `the Text size counts with the system font scale`() {
        // 1.55 times 1.3 is 2.015, past 2.0 though the system font scale alone is not.
        show(TextSpacing.Wider, 1.55f, TextSize.Largest)
        assertDrawnWith(TextSpacing.Wide)

        // 1.0 times 1.3 is 1.3, 1.3 times 1.3 is 1.69 (Largest on the largest system font that the
        // fit tests of the largest text use) and 1.5 times 1.3 is 1.95: all below 2.0.
        show(TextSpacing.Wider, 1f, TextSize.Largest)
        assertDrawnWith(TextSpacing.Wider)
        show(TextSpacing.Wider, 1.3f, TextSize.Largest)
        assertDrawnWith(TextSpacing.Wider)
        show(TextSpacing.Wider, 1.5f, TextSize.Largest)
        assertDrawnWith(TextSpacing.Wider)
        show(TextSpacing.Wider, 1.3f, TextSize.Larger)
        assertDrawnWith(TextSpacing.Wider)
    }

    @Test
    fun `the fit cases of the largest text are drawn as chosen, Wider among them`() {
        // The fit tests of the largest text run every font at Wider, at the Text size Largest over
        // a
        // system font scale of 1.3. They test nothing of Wider if the theme draws it as Wide there.
        for (case in fitCases() + cardFitCases()) {
            chosen = case.spacing
            systemScale = 1.3f
            textSize = TextSize.Largest
            if (!shown) {
                shown = true
                composeRule.setContent { Themed { Probe() } }
            }
            composeRule.waitForIdle()

            assertThat(drawn).isEqualTo(case.spacing)
        }
    }

    @Test
    fun `a theme inside another keeps the level that the outer one drew, and the Text size once`() {
        composeRule.setContent {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density, 1.6f)) {
                BorealisTheme(
                    FormFactor.Tv,
                    BorealisLook(textSpacing = TextSpacing.Wider, textSize = TextSize.Largest),
                ) {
                    BorealisTheme(FormFactor.Tv) { Probe() }
                }
            }
        }
        composeRule.waitForIdle()

        assertDrawnWith(TextSpacing.Wide)
        assertThat(checkNotNull(density).fontScale).isWithin(EPSILON).of(1.6f * 1.3f)
    }

    @Test
    fun `the design-system Text spaces its words by the level that is drawn`() {
        composeRule.setContent {
            Themed {
                Column {
                    Text(
                        "one two",
                        Modifier.testTag("text"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        // Wider is chosen all along: its spaces have 0.68 em below a font scale of 2.0, and the
        // 0.28
        // em of Wide from it.
        val wider = TextSpacing.Wider.letterEm + TextSpacing.Wider.wordEm
        val wide = TextSpacing.Wide.letterEm + TextSpacing.Wide.wordEm
        chosen = TextSpacing.Wider
        for ((scale, spaceEm) in listOf(2f to wide, 1.6f to wider, 1f to wider, 2.6f to wide)) {
            systemScale = scale
            composeRule.waitForIdle()

            val span =
                composeRule
                    .onNodeWithTag("text")
                    .textLayout()
                    .layoutInput
                    .text
                    .spanStyles
                    .singleOrNull { it.start == SPACE && it.end == SPACE + 1 }
                    ?.item
                    ?.letterSpacing
                    ?.value
            assertThat(span).isWithin(EPSILON).of(spaceEm)
        }
    }

    private companion object {
        const val EPSILON = 0.001f

        /** The index of the space in "one two". */
        const val SPACE = 3
    }
}
