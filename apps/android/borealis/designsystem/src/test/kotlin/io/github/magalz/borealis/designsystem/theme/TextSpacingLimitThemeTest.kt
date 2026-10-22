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
import io.github.magalz.borealis.designsystem.testing.textLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The theme draws the Text spacing that the look chose only while the text is small enough for it:
 * Wider is drawn as Wide from a font scale of 1.5, and Wide and Wider as Normal from 1.7 (ADR 0058,
 * decision 4). The scale is the one that the text is drawn at, the system font scale times the Text
 * size of the look. What the look chose does not change, so the level comes back when the scale
 * falls again.
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
    fun `every level is drawn as chosen below a font scale of 1 point 5`() {
        for (scale in listOf(0.85f, 1f, 1.15f, 1.3f, 1.49f)) {
            for (level in TextSpacing.entries) {
                show(level, scale)

                assertDrawnWith(level)
            }
        }
    }

    @Test
    fun `Wider is drawn as Wide from a font scale of 1 point 5 up to 1 point 7, and the others stay`() {
        for (scale in listOf(1.5f, 1.6f, 1.69f)) {
            show(TextSpacing.Wider, scale)
            assertDrawnWith(TextSpacing.Wide)

            show(TextSpacing.Wide, scale)
            assertDrawnWith(TextSpacing.Wide)

            show(TextSpacing.Normal, scale)
            assertDrawnWith(TextSpacing.Normal)
        }
    }

    @Test
    fun `Wide and Wider are drawn as Normal from a font scale of 1 point 7, 2 point 0 of Android among them`() {
        for (scale in listOf(1.7f, 1.8f, 2f)) {
            for (level in TextSpacing.entries) {
                show(level, scale)

                assertDrawnWith(TextSpacing.Normal)
            }
        }
    }

    @Test
    fun `the level that the look chose comes back when the font scale falls again`() {
        show(TextSpacing.Wider, 2f)
        assertDrawnWith(TextSpacing.Normal)

        show(TextSpacing.Wider, 1.6f)
        assertDrawnWith(TextSpacing.Wide)

        show(TextSpacing.Wider, 1f)
        assertDrawnWith(TextSpacing.Wider)
    }

    @Test
    fun `the Text size counts with the system font scale`() {
        // 1.2 times 1.3 is 1.56, past 1.5 though the system font scale alone is not.
        show(TextSpacing.Wider, 1.2f, TextSize.Largest)
        assertDrawnWith(TextSpacing.Wide)

        // 1.0 times 1.3 is 1.3, 1.3 times 1.15 is 1.495 and 1.1 times 1.15 is 1.265: all below 1.5.
        show(TextSpacing.Wider, 1f, TextSize.Largest)
        assertDrawnWith(TextSpacing.Wider)
        show(TextSpacing.Wider, 1.3f, TextSize.Larger)
        assertDrawnWith(TextSpacing.Wider)
        show(TextSpacing.Wider, 1.1f, TextSize.Larger)
        assertDrawnWith(TextSpacing.Wider)

        // 1.3 times 1.3 is 1.69, still Wide; 1.5 times 1.3 is 1.95, Normal.
        show(TextSpacing.Wider, 1.3f, TextSize.Largest)
        assertDrawnWith(TextSpacing.Wide)
        show(TextSpacing.Wider, 1.5f, TextSize.Largest)
        assertDrawnWith(TextSpacing.Normal)
    }

    @Test
    fun `a theme inside another keeps the level that the outer one drew, and the Text size once`() {
        composeRule.setContent {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density, 1.2f)) {
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
        assertThat(checkNotNull(density).fontScale).isWithin(EPSILON).of(1.2f * 1.3f)
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
        for ((level, scale, spaceEm) in
            listOf(
                Triple(
                    TextSpacing.Wider,
                    1.6f,
                    TextSpacing.Wide.letterEm + TextSpacing.Wide.wordEm,
                ),
                Triple(
                    TextSpacing.Wider,
                    1f,
                    TextSpacing.Wider.letterEm + TextSpacing.Wider.wordEm,
                ),
                Triple(TextSpacing.Wider, 2f, null),
            )) {
            chosen = level
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
            if (spaceEm == null) {
                assertThat(span).isNull()
            } else {
                assertThat(span).isWithin(EPSILON).of(spaceEm)
            }
        }
    }

    private companion object {
        const val EPSILON = 0.001f

        /** The index of the space in "one two". */
        const val SPACE = 3
    }
}
