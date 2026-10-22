package io.github.magalz.borealis.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Screenshots of the dark Borealis theme; the references live in `src/test/screenshots`. */
@RunWith(AndroidJUnit4::class)
class ThemeScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun themeSwatchesPhone() {
        composeRule.setContent { BorealisTheme(FormFactor.Phone) { Swatches() } }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/theme_swatches_phone.png")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun typographyPhone() {
        composeRule.setContent { BorealisTheme(FormFactor.Phone) { TypeSpecimen(columns = 1) } }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/typography_phone.png")
    }

    @Test
    @Config(qualifiers = TV)
    fun typographyTv() {
        composeRule.setContent { BorealisTheme(FormFactor.Tv) { TypeSpecimen(columns = 2) } }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/typography_tv.png")
    }

    private companion object {
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
    }
}

@Composable
private fun Swatches() {
    val colors = LocalBorealisColors.current
    val spacing = LocalBorealisSpacing.current
    val named =
        listOf(
            "page" to colors.page,
            "chrome" to colors.chrome,
            "surface" to colors.surface,
            "surface-raised" to colors.surfaceRaised,
            "line" to colors.line,
            "line-strong" to colors.lineStrong,
            "hover" to colors.hover,
            "scrim" to colors.scrim,
            "ink" to colors.ink,
            "ink-2" to colors.ink2,
            "ink-3" to colors.ink3,
            "ink-4" to colors.ink4,
            "accent" to colors.accent,
            "accent-soft" to colors.accentSoft,
            "accent-strong" to colors.accentStrong,
            "on-accent" to colors.onAccent,
            "accent-tint" to colors.accentTint,
            "accent-line" to colors.accentLine,
            "accent-inverse" to colors.accentInverse,
            "success" to colors.success,
            "success-ink" to colors.successInk,
            "warning" to colors.warning,
            "warning-ink" to colors.warningInk,
            "danger" to colors.danger,
            "danger-ink" to colors.dangerInk,
            "offline" to colors.offline,
        )
    val half = (named.size + 1) / 2
    Column(
        Modifier.fillMaxSize().background(colors.page).padding(spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Colour tokens", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InkSample("Ink on page", colors.page, Modifier.weight(1f))
            InkSample("Ink on surface", colors.surface, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (column in named.chunked(half)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((name, color) in column) Swatch(name, color)
                }
            }
        }
    }
}

@Composable
private fun InkSample(label: String, background: Color, modifier: Modifier) {
    val colors = LocalBorealisColors.current
    Box(
        modifier
            .background(background, MaterialTheme.shapes.large)
            .border(1.dp, colors.line, MaterialTheme.shapes.large)
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    val colors = LocalBorealisColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp)
                .background(
                    if (color.alpha < 1f) MID_GREY else Color.Transparent,
                    MaterialTheme.shapes.small,
                )
                .background(color, MaterialTheme.shapes.small)
                .border(1.dp, colors.ink3, MaterialTheme.shapes.small)
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
    }
}

@Composable
private fun TypeSpecimen(columns: Int) {
    val colors = LocalBorealisColors.current
    val spacing = LocalBorealisSpacing.current
    val type = LocalBorealisType.current
    val typography = MaterialTheme.typography
    val styles: List<Pair<String, TextStyle>> =
        listOf(
            "titleLarge" to typography.titleLarge,
            "titleMedium" to typography.titleMedium,
            "titleSmall" to typography.titleSmall,
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelLarge" to typography.labelLarge,
            "labelMedium" to typography.labelMedium,
            "labelSmall" to typography.labelSmall,
            "kicker" to type.kicker,
            "rail" to type.rail,
            "pin" to type.pin,
        )
    Row(
        Modifier.fillMaxSize().background(colors.page).padding(spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(spacing.gutter),
    ) {
        for (column in styles.chunked((styles.size + columns - 1) / columns)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((name, style) in column) {
                    Column {
                        Text(
                            "$name ${style.fontSize.value}sp",
                            style = typography.labelSmall,
                            color = colors.ink3,
                        )
                        Text(
                            sample(name),
                            style = style,
                            color = if (name == "kicker") Color.Unspecified else colors.ink,
                        )
                    }
                }
            }
        }
    }
}

/** Kickers are set in capitals, and the PIN shows digits. */
private fun sample(style: String) =
    when (style) {
        "pin" -> "482 913"
        "kicker" -> "LIVING ROOM PC"
        else -> "Living room PC"
    }

/** Translucent tokens (lines, hover, scrim, tints) are drawn over this so they can be seen. */
private val MID_GREY = Color(0xFF808080)
