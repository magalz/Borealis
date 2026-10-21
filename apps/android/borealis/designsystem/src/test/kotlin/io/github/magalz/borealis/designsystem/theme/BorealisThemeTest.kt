package io.github.magalz.borealis.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Test

class BorealisThemeTest {
    private val colors = BorealisTokens.Color

    @Test
    fun `the colour scheme maps the page surface and accent tokens`() {
        val scheme = borealisColorScheme(BorealisColors())

        assertThat(scheme.background).isEqualTo(colors.Page)
        assertThat(scheme.surface).isEqualTo(colors.Surface)
        assertThat(scheme.onBackground).isEqualTo(colors.Ink)
        assertThat(scheme.onSurface).isEqualTo(colors.Ink)
        assertThat(scheme.primary).isEqualTo(colors.Accent)
        assertThat(scheme.onPrimary).isEqualTo(colors.OnAccent)
        assertThat(scheme.secondary).isEqualTo(colors.AccentSoft)
    }

    @Test
    fun `the colour scheme maps lines errors containers and the scrim`() {
        val scheme = borealisColorScheme(BorealisColors())

        assertThat(scheme.outline).isEqualTo(colors.LineStrong)
        assertThat(scheme.outlineVariant).isEqualTo(colors.Line)
        assertThat(scheme.error).isEqualTo(colors.DangerInk)
        assertThat(scheme.scrim).isEqualTo(colors.Scrim)
        assertThat(scheme.surfaceContainer).isEqualTo(colors.Surface)
        assertThat(scheme.surfaceContainerHigh).isEqualTo(colors.SurfaceRaised)
    }

    @Test
    fun `no container puts text on accent strong`() {
        val scheme = borealisColorScheme(BorealisColors())

        assertThat(scheme.primaryContainer).isEqualTo(colors.SurfaceRaised)
        assertThat(scheme.onPrimaryContainer).isEqualTo(colors.AccentSoft)
        // Inverse primary is text on the light inverse surface, where accent soft would be 1.5:1.
        assertThat(scheme.inverseSurface).isEqualTo(colors.Ink)
        assertThat(scheme.inversePrimary).isEqualTo(colors.AccentStrong)
    }

    @Test
    fun `Sans has regular medium and semibold`() {
        val fonts = (BorealisFonts.Sans as? FontListFontFamily)?.fonts

        assertThat(fonts?.map { it.weight })
            .containsExactly(
                BorealisTokens.FontWeight.Regular,
                BorealisTokens.FontWeight.Medium,
                BorealisTokens.FontWeight.Semibold,
            )
            .inOrder()
    }

    @Test
    fun `Mono has regular medium and semibold and differs from Sans`() {
        val fonts = (BorealisFonts.Mono as? FontListFontFamily)?.fonts

        assertThat(fonts?.map { it.weight })
            .containsExactly(
                BorealisTokens.FontWeight.Regular,
                BorealisTokens.FontWeight.Medium,
                BorealisTokens.FontWeight.Semibold,
            )
            .inOrder()
        assertThat(BorealisFonts.Mono).isNotEqualTo(BorealisFonts.Sans)
    }

    @Test
    fun `the Borealis colours hold every token`() {
        val borealis = BorealisColors()

        assertThat(borealis.page).isEqualTo(colors.Page)
        assertThat(borealis.ink2).isEqualTo(colors.Ink2)
        assertThat(borealis.ink3).isEqualTo(colors.Ink3)
        assertThat(borealis.ink4).isEqualTo(colors.Ink4)
        assertThat(borealis.accentTint).isEqualTo(colors.AccentTint)
        assertThat(borealis.successInk).isEqualTo(colors.SuccessInk)
        assertThat(borealis.warning).isEqualTo(colors.Warning)
        assertThat(borealis.danger).isEqualTo(colors.Danger)
        assertThat(borealis.offline).isEqualTo(colors.Offline)
    }

    @Test
    fun `the phone scale is 18 sp title and 14 sp body`() {
        val typography = borealisTypography(FormFactor.Phone)

        assertThat(typography.titleLarge.fontSize).isEqualTo(18.sp)
        assertThat(typography.titleLarge.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(typography.bodyMedium.fontSize).isEqualTo(14.sp)
        assertThat(typography.bodySmall.fontSize).isEqualTo(12.5.sp)
        assertThat(typography.titleLarge.fontFamily).isEqualTo(BorealisFonts.Sans)
    }

    @Test
    fun `the phone kicker is 11 sp semibold with 0 point 07 em spacing in ink 4`() {
        val kicker = borealisType(FormFactor.Phone).kicker

        assertThat(kicker.fontSize).isEqualTo(11.sp)
        assertThat(kicker.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(kicker.letterSpacing).isEqualTo(0.07.em)
        assertThat(kicker.color).isEqualTo(colors.Ink4)
    }

    @Test
    fun `the TV scale is 24 sp title 16 sp body and 14 point 5 sp rail`() {
        val typography = borealisTypography(FormFactor.Tv)

        assertThat(typography.titleLarge.fontSize).isEqualTo(24.sp)
        assertThat(typography.titleLarge.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(typography.bodyMedium.fontSize).isEqualTo(16.sp)
        assertThat(borealisType(FormFactor.Tv).rail.fontSize).isEqualTo(14.5.sp)
        assertThat(borealisType(FormFactor.Tv).rail.fontWeight).isEqualTo(FontWeight.Medium)
    }

    @Test
    fun `the TV minimum is 13 sp in the small label and the kicker`() {
        val smallLabel = borealisTypography(FormFactor.Tv).labelSmall
        val kicker = borealisType(FormFactor.Tv).kicker

        assertThat(smallLabel.fontSize).isEqualTo(13.sp)
        assertThat(smallLabel.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(kicker.fontSize).isEqualTo(13.sp)
        assertThat(kicker.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(kicker.letterSpacing).isEqualTo(0.07.em)
    }

    @Test
    fun `the PIN is 34 sp semibold mono on every form factor`() {
        for (formFactor in FormFactor.entries) {
            val pin = borealisType(formFactor).pin

            assertThat(pin.fontSize).isEqualTo(34.sp)
            assertThat(pin.fontWeight).isEqualTo(FontWeight.SemiBold)
            assertThat(pin.fontFamily).isEqualTo(BorealisFonts.Mono)
        }
    }

    @Test
    fun `no TV text style is below 13 sp`() {
        val type = borealisType(FormFactor.Tv)
        val styles =
            borealisTypography(FormFactor.Tv).allStyles() + type.kicker + type.pin + type.rail

        for (style in styles) {
            assertThat(style.fontSize.value).isAtLeast(13f)
        }
    }

    @Test
    fun `the handheld keeps 24 sp title 15 sp body 14 point 5 sp rail and the 12 sp minimum`() {
        val typography = borealisTypography(FormFactor.Handheld)
        val type = borealisType(FormFactor.Handheld)

        assertThat(typography.titleLarge.fontSize).isEqualTo(24.sp)
        assertThat(typography.bodyMedium.fontSize).isEqualTo(15.sp)
        assertThat(typography.titleMedium.fontSize).isEqualTo(15.sp)
        assertThat(typography.labelLarge.fontSize).isEqualTo(15.sp)
        assertThat(type.rail.fontSize).isEqualTo(14.5.sp)
        assertThat(typography.labelSmall.fontSize).isEqualTo(12.sp)
        assertThat(type.kicker.fontSize).isEqualTo(12.sp)
    }

    @Test
    fun `no handheld text style is below 12 sp`() {
        val type = borealisType(FormFactor.Handheld)
        val styles =
            borealisTypography(FormFactor.Handheld).allStyles() + type.kicker + type.pin + type.rail

        for (style in styles) {
            assertThat(style.fontSize.value).isAtLeast(12f)
        }
    }

    @Test
    fun `the phone scale keeps its sizes down to the small label`() {
        val typography = borealisTypography(FormFactor.Phone)

        assertThat(typography.titleLarge.fontSize).isEqualTo(18.sp)
        assertThat(typography.bodyLarge.fontSize).isEqualTo(14.sp)
        assertThat(typography.bodySmall.fontSize).isEqualTo(12.5.sp)
        assertThat(typography.labelSmall.fontSize).isEqualTo(11.sp)
        assertThat(borealisType(FormFactor.Phone).kicker.fontSize).isEqualTo(11.sp)
        assertThat(borealisType(FormFactor.Phone).rail.fontSize).isEqualTo(14.5.sp)
    }

    @Test
    fun `the handheld has the spacing of a TV`() {
        assertThat(borealisSpacing(FormFactor.Handheld).gutter)
            .isEqualTo(borealisSpacing(FormFactor.Tv).gutter)
        assertThat(borealisSpacing(FormFactor.Handheld).cardPadding)
            .isEqualTo(borealisSpacing(FormFactor.Tv).cardPadding)
    }

    @Test
    fun `gutter and card padding follow the form factor`() {
        assertThat(borealisSpacing(FormFactor.Phone).gutter).isEqualTo(16.dp)
        assertThat(borealisSpacing(FormFactor.Phone).cardPadding).isEqualTo(14.dp)
        assertThat(borealisSpacing(FormFactor.Tv).gutter).isEqualTo(34.dp)
        assertThat(borealisSpacing(FormFactor.Tv).cardPadding).isEqualTo(18.dp)
    }

    @Test
    fun `Material shapes use the chip button and card radii`() {
        val shapes = borealisShapes()

        assertThat(shapes.small).isEqualTo(RoundedCornerShape(7.dp))
        assertThat(shapes.medium).isEqualTo(RoundedCornerShape(8.dp))
        assertThat(shapes.large).isEqualTo(RoundedCornerShape(12.dp))
    }

    @Test
    fun `cover sheet and pill shapes come from the radius tokens`() {
        val shapes = borealisExtraShapes()

        assertThat(shapes.cover).isEqualTo(RoundedCornerShape(10.dp))
        assertThat(shapes.sheet)
            .isEqualTo(
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomEnd = 0.dp,
                    bottomStart = 0.dp,
                )
            )
        assertThat(shapes.pill).isEqualTo(RoundedCornerShape(999.dp))
    }

    private fun Typography.allStyles(): List<TextStyle> =
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
