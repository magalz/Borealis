package io.github.magalz.borealis.designsystem.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.fontFiles
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The font of the look is the font of the app (ADR 0058, decision 2): the Material text styles and
 * the Borealis ones draw in its sans family and the codes (the PIN) in its monospaced companion.
 * Each size is the size of the token times the scale that sets the x-height of the font to that of
 * IBM Plex Sans, and never goes below the minimum of the form factor.
 */
@RunWith(AndroidJUnit4::class)
class FontChoiceTypographyTest {
    @get:Rule val composeRule = createComposeRule()

    private class Seen(val typography: Typography, val type: BorealisType)

    private var seen: Seen? = null
    private var shownFont by mutableStateOf(FontChoice.Plex)
    private var shownFormFactor by mutableStateOf(FormFactor.Phone)
    private var composed = false

    @Composable
    private fun Probe() {
        seen = Seen(MaterialTheme.typography, LocalBorealisType.current)
    }

    /**
     * What the theme gives for [font] on [formFactor]. A test has one composition, so a second call
     * changes the font and the form factor of the one that is shown.
     */
    private fun themed(font: FontChoice, formFactor: FormFactor = FormFactor.Phone): Seen {
        shownFont = font
        shownFormFactor = formFactor
        if (!composed) {
            composed = true
            composeRule.setContent {
                BorealisTheme(shownFormFactor, BorealisLook(font = shownFont)) { Probe() }
            }
        }
        composeRule.waitForIdle()
        return checkNotNull(seen) { "the themed content did not compose" }
    }

    /** The weight and the name of the file of each font of [family], as the resources name them. */
    private fun files(family: FontFamily?): List<Pair<Int, String>> =
        family.fontFiles(ApplicationProvider.getApplicationContext<Context>().resources)

    private fun every(typography: Typography): List<TextStyle> =
        with(typography) {
            listOf(
                displayLarge,
                displayMedium,
                displaySmall,
                headlineLarge,
                headlineMedium,
                headlineSmall,
                titleLarge,
                titleMedium,
                titleSmall,
                bodyLarge,
                bodyMedium,
                bodySmall,
                labelLarge,
                labelMedium,
                labelSmall,
            )
        }

    private fun everyBorealis(type: BorealisType): List<TextStyle> =
        listOf(type.kicker, type.pin, type.rail)

    private val plexSans =
        listOf(
            400 to "ibm_plex_sans_regular",
            500 to "ibm_plex_sans_medium",
            600 to "ibm_plex_sans_semibold",
        )
    private val plexMono =
        listOf(
            400 to "ibm_plex_mono_regular",
            500 to "ibm_plex_mono_medium",
            600 to "ibm_plex_mono_semibold",
        )

    /** What each font is: its files, the files of its codes, and its two scales. */
    private class Expected(
        val font: FontChoice,
        val sans: List<Pair<Int, String>>?,
        val codes: List<Pair<Int, String>>,
        val sansScale: Float,
        val codesScale: Float,
    )

    private val fonts =
        listOf(
            Expected(FontChoice.Plex, plexSans, plexMono, 1f, 1f),
            Expected(
                FontChoice.AtkinsonNext,
                listOf(
                    400 to "atkinson_next_regular",
                    500 to "atkinson_next_medium",
                    600 to "atkinson_next_semibold",
                ),
                listOf(
                    400 to "atkinson_mono_regular",
                    500 to "atkinson_mono_medium",
                    600 to "atkinson_mono_semibold",
                ),
                1.040f,
                1.040f,
            ),
            Expected(
                FontChoice.Luciole,
                listOf(400 to "luciole_regular", 700 to "luciole_bold"),
                plexMono,
                0.947f,
                1f,
            ),
            Expected(
                FontChoice.Lexend,
                listOf(
                    400 to "lexend_regular",
                    500 to "lexend_medium",
                    600 to "lexend_semibold",
                ),
                plexMono,
                0.983f,
                1f,
            ),
            // The app bundles Regular and SemiBold of Andika only: Compose takes the nearest weight
            // for Medium.
            Expected(
                FontChoice.Andika,
                listOf(400 to "andika_regular", 600 to "andika_semibold"),
                plexMono,
                1.034f,
                1f,
            ),
            Expected(
                FontChoice.B612,
                listOf(400 to "b612_regular", 700 to "b612_bold"),
                listOf(400 to "b612_mono_regular", 700 to "b612_mono_bold"),
                0.923f,
                0.921f,
            ),
            Expected(FontChoice.System, null, plexMono, 1f, 1f),
        )

    private fun minimum(formFactor: FormFactor): TextUnit =
        when (formFactor) {
            FormFactor.Phone -> BorealisTokens.Size.Phone.Kicker
            FormFactor.Tv -> BorealisTokens.Size.Tv.Minimum
            FormFactor.Handheld -> BorealisTokens.Size.Handheld.Minimum
        }

    private fun body(formFactor: FormFactor): TextUnit =
        when (formFactor) {
            FormFactor.Phone -> BorealisTokens.Size.Phone.Body
            FormFactor.Tv -> BorealisTokens.Size.Tv.Body
            FormFactor.Handheld -> BorealisTokens.Size.Handheld.Body
        }

    private fun title(formFactor: FormFactor): TextUnit =
        if (formFactor == FormFactor.Phone) BorealisTokens.Size.Phone.Title
        else BorealisTokens.Size.Tv.Title

    /** The size of a [token] drawn in a font of [scale]: scaled, and never below the minimum. */
    private fun sized(token: TextUnit, scale: Float, formFactor: FormFactor): Float =
        maxOf(token.value * scale, minimum(formFactor).value)

    @Test
    fun `every font draws the text of the app in its sans family`() {
        for (expected in fonts) {
            val seen = themed(expected.font)

            for (style in every(seen.typography) + seen.type.kicker + seen.type.rail) {
                if (expected.sans == null) {
                    assertThat(style.fontFamily).isEqualTo(FontFamily.Default)
                } else {
                    assertWithMessage("${expected.font}")
                        .that(files(style.fontFamily))
                        .containsExactlyElementsIn(expected.sans)
                        .inOrder()
                }
            }
        }
    }

    @Test
    fun `every font draws the codes in the monospaced companion, or in IBM Plex Mono`() {
        for (expected in fonts) {
            val seen = themed(expected.font)

            assertWithMessage("${expected.font}")
                .that(files(seen.type.pin.fontFamily))
                .containsExactlyElementsIn(expected.codes)
                .inOrder()
        }
    }

    @Test
    fun `the default look draws in IBM Plex Sans and the PIN in IBM Plex Mono`() {
        composeRule.setContent { BorealisTheme { Probe() } }
        composeRule.waitForIdle()
        val plain = checkNotNull(seen)

        assertThat(files(plain.typography.bodyLarge.fontFamily)).isEqualTo(plexSans)
        assertThat(files(plain.type.pin.fontFamily)).isEqualTo(plexMono)
    }

    @Test
    fun `every size is the token times the scale of the font`() {
        for (formFactor in FormFactor.entries) {
            for (expected in fonts) {
                val seen = themed(expected.font, formFactor)
                val label = "${expected.font} on $formFactor"

                assertWithMessage("$label, body")
                    .that(seen.typography.bodyLarge.fontSize.value)
                    .isWithin(EPSILON)
                    .of(sized(body(formFactor), expected.sansScale, formFactor))
                assertWithMessage("$label, title")
                    .that(seen.typography.titleLarge.fontSize.value)
                    .isWithin(EPSILON)
                    .of(sized(title(formFactor), expected.sansScale, formFactor))
                assertWithMessage("$label, rail")
                    .that(seen.type.rail.fontSize.value)
                    .isWithin(EPSILON)
                    .of(sized(BorealisTokens.Size.Tv.Rail, expected.sansScale, formFactor))
                assertWithMessage("$label, PIN")
                    .that(seen.type.pin.fontSize.value)
                    .isWithin(EPSILON)
                    .of(sized(BorealisTokens.Size.Phone.Pin, expected.codesScale, formFactor))
            }
        }
    }

    @Test
    fun `the sizes of the default font are the tokens`() {
        val seen = themed(FontChoice.Plex, FormFactor.Tv)

        assertThat(seen.typography.bodyLarge.fontSize).isEqualTo(16.sp)
        assertThat(seen.typography.titleLarge.fontSize).isEqualTo(24.sp)
        assertThat(seen.type.rail.fontSize).isEqualTo(14.5.sp)
        assertThat(seen.type.pin.fontSize).isEqualTo(34.sp)
    }

    @Test
    fun `no size goes below the minimum of the form factor in any font`() {
        for (formFactor in FormFactor.entries) {
            for (expected in fonts) {
                val seen = themed(expected.font, formFactor)

                for (style in every(seen.typography) + everyBorealis(seen.type)) {
                    assertWithMessage("${expected.font} on $formFactor")
                        .that(style.fontSize.value)
                        .isAtLeast(minimum(formFactor).value)
                }
            }
        }
    }

    @Test
    fun `B612 on a TV keeps the minimum of 13 sp, which its scale would take below`() {
        val seen = themed(FontChoice.B612, FormFactor.Tv)

        // 13 sp times 0.923 is 12 sp: the floor holds the label and the kicker at 13 sp.
        assertThat(seen.typography.labelSmall.fontSize).isEqualTo(13.sp)
        assertThat(seen.type.kicker.fontSize).isEqualTo(13.sp)
    }

    @Test
    fun `B612 on a handheld keeps the minimum of 12 sp and on a phone the kicker of 11 sp`() {
        assertThat(themed(FontChoice.B612, FormFactor.Handheld).type.kicker.fontSize)
            .isEqualTo(12.sp)
        assertThat(themed(FontChoice.B612, FormFactor.Phone).type.kicker.fontSize).isEqualTo(11.sp)
    }

    @Test
    fun `a font that is larger than Plex grows the minimum`() {
        val seen = themed(FontChoice.AtkinsonNext, FormFactor.Tv)

        assertThat(seen.type.kicker.fontSize.value).isWithin(EPSILON).of(13f * 1.040f)
    }

    @Test
    fun `codes puts a style of the type scale in the codes font and keeps the rest of it`() {
        val seen = themed(FontChoice.B612, FormFactor.Tv)

        val style = seen.typography.labelMedium
        val codes = seen.type.codes(style)

        assertThat(files(codes.fontFamily).map { it.second })
            .containsExactly("b612_mono_regular", "b612_mono_bold")
        assertThat(codes.fontWeight).isEqualTo(style.fontWeight)
        assertThat(codes.letterSpacing).isEqualTo(style.letterSpacing)
    }

    @Test
    fun `codes sets the codes to the x-height of the text around them`() {
        for (formFactor in FormFactor.entries) {
            for (expected in fonts) {
                val seen = themed(expected.font, formFactor)
                val style = seen.typography.bodySmall
                val ratio = expected.codesScale / expected.sansScale

                assertWithMessage("${expected.font} on $formFactor")
                    .that(seen.type.codes(style).fontSize.value)
                    .isWithin(EPSILON)
                    .of(maxOf(style.fontSize.value * ratio, minimum(formFactor).value))
            }
        }
    }

    @Test
    fun `codes never goes below the minimum of the form factor`() {
        for (formFactor in FormFactor.entries) {
            for (expected in fonts) {
                val seen = themed(expected.font, formFactor)

                assertWithMessage("${expected.font} on $formFactor")
                    .that(seen.type.codes(seen.type.kicker).fontSize.value)
                    .isAtLeast(minimum(formFactor).value)
            }
        }
    }

    @Test
    fun `a change of font reaches the text that is already shown`() {
        var font by mutableStateOf(FontChoice.Plex)
        composeRule.setContent { BorealisTheme(look = BorealisLook(font = font)) { Probe() } }
        composeRule.waitForIdle()
        assertThat(files(checkNotNull(seen).typography.bodyLarge.fontFamily)).isEqualTo(plexSans)

        font = FontChoice.B612
        composeRule.waitForIdle()

        val b612 = checkNotNull(seen)
        assertThat(files(b612.typography.bodyLarge.fontFamily).map { it.second })
            .containsExactly("b612_regular", "b612_bold")
        assertThat(files(b612.type.pin.fontFamily).map { it.second })
            .containsExactly("b612_mono_regular", "b612_mono_bold")

        font = FontChoice.System
        composeRule.waitForIdle()

        assertThat(checkNotNull(seen).typography.bodyLarge.fontFamily).isEqualTo(FontFamily.Default)
    }

    @Test
    fun `a theme inside another keeps the font of the look of the outer one`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(font = FontChoice.Lexend)) {
                BorealisTheme(FormFactor.Tv) { Probe() }
            }
        }
        composeRule.waitForIdle()

        assertThat(files(checkNotNull(seen).typography.bodyLarge.fontFamily).map { it.second })
            .containsExactly("lexend_regular", "lexend_medium", "lexend_semibold")
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
