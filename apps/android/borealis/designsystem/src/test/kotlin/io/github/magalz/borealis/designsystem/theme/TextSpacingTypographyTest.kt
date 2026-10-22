package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The Text spacing setting in the type scale (ADR 0058, decision 4, WCAG 1.4.12). A level is a
 * minimum in em of the font size of each style, for the letters and for the lines: a style that has
 * more of its own keeps it, and Normal leaves every style as the design has it. The values are read
 * from [TextSpacing], and the table of the ADR is pinned once.
 */
class TextSpacingTypographyTest {
    private class Named(val name: String, val style: TextStyle)

    private fun every(typography: Typography): List<Named> =
        with(typography) {
            listOf(
                Named("displayLarge", displayLarge),
                Named("displayMedium", displayMedium),
                Named("displaySmall", displaySmall),
                Named("headlineLarge", headlineLarge),
                Named("headlineMedium", headlineMedium),
                Named("headlineSmall", headlineSmall),
                Named("titleLarge", titleLarge),
                Named("titleMedium", titleMedium),
                Named("titleSmall", titleSmall),
                Named("bodyLarge", bodyLarge),
                Named("bodyMedium", bodyMedium),
                Named("bodySmall", bodySmall),
                Named("labelLarge", labelLarge),
                Named("labelMedium", labelMedium),
                Named("labelSmall", labelSmall),
            )
        }

    private fun every(type: BorealisType): List<Named> =
        listOf(Named("kicker", type.kicker), Named("pin", type.pin), Named("rail", type.rail))

    /** Every style of the type scale and of [BorealisType] for [formFactor] and [font]. */
    private fun styles(
        formFactor: FormFactor,
        font: FontChoice,
        spacing: TextSpacing,
    ): List<Named> =
        every(borealisTypography(formFactor, font, spacing)) +
            every(borealisType(formFactor, font, spacing))

    private fun cases(): List<Triple<FormFactor, FontChoice, String>> =
        FormFactor.entries.flatMap { formFactor ->
            FontChoice.entries.map { font -> Triple(formFactor, font, "$font on $formFactor") }
        }

    /** The value of [unit] in em of [size], or null when it has none or cannot be converted. */
    private fun inEm(unit: TextUnit, size: TextUnit): Float? =
        when {
            !unit.isSpecified -> null
            unit.type == TextUnitType.Em -> unit.value
            size.isSpecified && size.type == TextUnitType.Sp -> unit.value / size.value
            else -> null
        }

    private fun TextStyle.letterEm(): Float = inEm(letterSpacing, fontSize) ?: 0f

    private fun TextStyle.lineHeightEm(): Float? = inEm(lineHeight, fontSize)

    @Test
    fun `the levels are the table of the ADR`() {
        assertThat(TextSpacing.Normal.letterEm).isEqualTo(0f)
        assertThat(TextSpacing.Normal.wordEm).isEqualTo(0f)
        assertThat(TextSpacing.Normal.lineHeightEm).isEqualTo(0f)
        assertThat(TextSpacing.Wide.letterEm).isEqualTo(0.12f)
        assertThat(TextSpacing.Wide.wordEm).isEqualTo(0.16f)
        assertThat(TextSpacing.Wide.lineHeightEm).isEqualTo(1.5f)
        assertThat(TextSpacing.Wider.letterEm).isEqualTo(0.18f)
        assertThat(TextSpacing.Wider.wordEm).isEqualTo(0.5f)
        assertThat(TextSpacing.Wider.lineHeightEm).isEqualTo(1.5f)
    }

    @Test
    fun `Wider is wider than Wide in letters and in words, with the same line height`() {
        assertThat(TextSpacing.Wider.letterEm).isGreaterThan(TextSpacing.Wide.letterEm)
        assertThat(TextSpacing.Wider.wordEm).isGreaterThan(TextSpacing.Wide.wordEm)
        assertThat(TextSpacing.Wider.lineHeightEm).isEqualTo(TextSpacing.Wide.lineHeightEm)
    }

    @Test
    fun `Normal leaves the letter spacing and the line height of the design as they are`() {
        for ((formFactor, font, label) in cases()) {
            val styles = styles(formFactor, font, TextSpacing.Normal).associateBy { it.name }

            // Material's own styles keep their sp values, the Borealis ones have no line height.
            assertWithMessage(label)
                .that(styles.getValue("displayLarge").style.letterSpacing)
                .isEqualTo(Typography().displayLarge.letterSpacing)
            assertWithMessage(label)
                .that(styles.getValue("displayLarge").style.lineHeight)
                .isEqualTo(Typography().displayLarge.lineHeight)
            assertWithMessage(label)
                .that(styles.getValue("bodyLarge").style.lineHeight.isSpecified)
                .isFalse()
            assertWithMessage(label)
                .that(styles.getValue("labelSmall").style.letterSpacing)
                .isEqualTo(0.07.em)
            assertWithMessage(label)
                .that(styles.getValue("kicker").style.letterSpacing)
                .isEqualTo(0.07.em)
            assertWithMessage(label)
                .that(styles.getValue("pin").style.letterSpacing.isSpecified)
                .isFalse()
            assertWithMessage(label)
                .that(borealisTypography(formFactor, font))
                .isEqualTo(borealisTypography(formFactor, font, TextSpacing.Normal))
        }
    }

    @Test
    fun `a level gives every style the letter spacing of the table, and keeps the larger one`() {
        for ((formFactor, font, label) in cases()) {
            val normal = styles(formFactor, font, TextSpacing.Normal)
            for (level in listOf(TextSpacing.Wide, TextSpacing.Wider)) {
                val spaced = styles(formFactor, font, level)

                for ((own, got) in normal.zip(spaced)) {
                    assertWithMessage("$label, ${got.name} at $level")
                        .that(got.style.letterEm())
                        .isWithin(EPSILON)
                        .of(maxOf(own.style.letterEm(), level.letterEm))
                }
            }
        }
    }

    @Test
    fun `a level gives every style a line height of at least the table, kept when it is more`() {
        for ((formFactor, font, label) in cases()) {
            val normal = styles(formFactor, font, TextSpacing.Normal)
            for (level in listOf(TextSpacing.Wide, TextSpacing.Wider)) {
                val spaced = styles(formFactor, font, level)

                for ((own, got) in normal.zip(spaced)) {
                    assertWithMessage("$label, ${got.name} at $level")
                        .that(got.style.lineHeightEm())
                        .isNotNull()
                    assertWithMessage("$label, ${got.name} at $level")
                        .that(got.style.lineHeightEm()!!)
                        .isWithin(EPSILON)
                        .of(maxOf(own.style.lineHeightEm() ?: 0f, level.lineHeightEm))
                }
            }
        }
    }

    @Test
    fun `Wider is wider than Wide in the letters of every style and has the same line height`() {
        for ((formFactor, font, label) in cases()) {
            val wide = styles(formFactor, font, TextSpacing.Wide)
            val wider = styles(formFactor, font, TextSpacing.Wider)

            for ((narrow, broad) in wide.zip(wider)) {
                assertWithMessage("$label, ${broad.name}")
                    .that(broad.style.letterEm())
                    .isGreaterThan(narrow.style.letterEm())
                assertWithMessage("$label, ${broad.name}")
                    .that(broad.style.lineHeightEm())
                    .isEqualTo(narrow.style.lineHeightEm())
            }
        }
    }

    @Test
    fun `a level changes nothing else in a style`() {
        for ((formFactor, font, label) in cases()) {
            val normal = styles(formFactor, font, TextSpacing.Normal)
            val spaced = styles(formFactor, font, TextSpacing.Wider)

            for ((own, got) in normal.zip(spaced)) {
                val reference = own.style.copy(letterSpacing = got.style.letterSpacing)
                assertWithMessage("$label, ${got.name}")
                    .that(got.style.copy(lineHeight = own.style.lineHeight))
                    .isEqualTo(reference)
            }
        }
    }

    @Test
    fun `the codes of a spaced style keep its spacing`() {
        val type = borealisType(FormFactor.Tv, FontChoice.B612, TextSpacing.Wider)
        val typography = borealisTypography(FormFactor.Tv, FontChoice.B612, TextSpacing.Wider)

        val codes = type.codes(typography.bodySmall)

        assertThat(codes.letterEm()).isWithin(EPSILON).of(TextSpacing.Wider.letterEm)
        assertThat(codes.lineHeightEm()).isEqualTo(TextSpacing.Wider.lineHeightEm)
    }

    @Test
    fun `a style with more of its own than the level keeps it, in whatever unit it has it`() {
        val own = TextStyle(fontSize = 16.sp, letterSpacing = 4.sp, lineHeight = 32.sp)

        for (level in listOf(TextSpacing.Wide, TextSpacing.Wider)) {
            val spaced = own.spaced(level)

            // 4 sp at 16 sp is 0.25 em, more than either level; 32 sp is a line height of 2 em.
            assertThat(spaced.letterEm()).isWithin(EPSILON).of(0.25f)
            assertThat(spaced.lineHeightEm()).isWithin(EPSILON).of(2f)
        }
    }

    @Test
    fun `the letter spacing is compared in em of the size of the style, not by its number`() {
        // The same 2 sp is 0.25 em at 8 sp, 0.125 em at 16 sp and 0.0625 em at 32 sp.
        val small = TextStyle(fontSize = 8.sp, letterSpacing = 2.sp).spaced(TextSpacing.Wider)
        val middle = TextStyle(fontSize = 16.sp, letterSpacing = 2.sp)
        val large = TextStyle(fontSize = 32.sp, letterSpacing = 2.sp)

        assertThat(small.letterEm()).isWithin(EPSILON).of(0.25f)
        assertThat(middle.spaced(TextSpacing.Wide).letterEm()).isWithin(EPSILON).of(0.125f)
        assertThat(middle.spaced(TextSpacing.Wider).letterEm())
            .isWithin(EPSILON)
            .of(TextSpacing.Wider.letterEm)
        assertThat(large.spaced(TextSpacing.Wide).letterEm())
            .isWithin(EPSILON)
            .of(TextSpacing.Wide.letterEm)
    }

    @Test
    fun `a style with less than the level is raised to it, whatever its unit`() {
        val inEm = TextStyle(fontSize = 16.sp, letterSpacing = 0.05.em, lineHeight = 1.2.em)
        val inSp = TextStyle(fontSize = 16.sp, letterSpacing = 0.5.sp, lineHeight = 20.sp)
        val none = TextStyle(fontSize = 16.sp)

        for (style in listOf(inEm, inSp, none)) {
            val spaced = style.spaced(TextSpacing.Wide)

            assertThat(spaced.letterEm()).isWithin(EPSILON).of(TextSpacing.Wide.letterEm)
            assertThat(spaced.lineHeightEm()).isWithin(EPSILON).of(TextSpacing.Wide.lineHeightEm)
        }
    }

    @Test
    fun `Normal gives a style back as it is`() {
        val own = TextStyle(fontSize = 16.sp, letterSpacing = -0.25.sp, lineHeight = 20.sp)

        assertThat(own.spaced(TextSpacing.Normal)).isEqualTo(own)
        assertThat(TextStyle(fontSize = 16.sp).spaced(TextSpacing.Normal))
            .isEqualTo(TextStyle(fontSize = 16.sp))
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
