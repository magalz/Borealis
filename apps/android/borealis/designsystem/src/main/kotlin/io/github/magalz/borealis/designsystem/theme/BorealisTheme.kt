package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

val LocalFormFactor = staticCompositionLocalOf { FormFactor.Phone }

/**
 * The factor of the text size that the themes above have already put into the font scale. A theme
 * inside another multiplies the font scale by the ratio of its own factor to this one, so the
 * screens that wear their own theme inside the shell's do not apply the text size twice.
 */
private val LocalAppliedTextSize = compositionLocalOf { 1f }

/**
 * The dark Borealis theme: Material 3 plus the Borealis colours, type, shapes and spacing, and the
 * [look] that the viewer chose. A theme given no look keeps the one of the theme around it, so the
 * screens that wear their own theme inside the shell do not undo what the shell provides.
 *
 * The look reaches the content in three ways. High contrast and the colour-blind palette make the
 * colours ([borealisColors]), for the Borealis components and for Material. The text size
 * multiplies the font scale of the system, so every `sp` follows it, and `dp` stay as they are.
 */
@Composable
fun BorealisTheme(
    formFactor: FormFactor = FormFactor.Phone,
    look: BorealisLook = LocalBorealisLook.current,
    content: @Composable () -> Unit,
) {
    val colors = remember(look.highContrast, look.colourVision) { borealisColors(look) }
    val colorScheme = remember(colors) { borealisColorScheme(colors) }
    val shapes = remember { borealisShapes() }
    val typography = remember(formFactor) { borealisTypography(formFactor) }
    val system = LocalDensity.current
    val applied = LocalAppliedTextSize.current
    val density =
        remember(system, look.textSize, applied) {
            val factor = look.textSize.scale / applied
            if (factor == 1f) system else Density(system.density, system.fontScale * factor)
        }
    CompositionLocalProvider(
        LocalFormFactor provides formFactor,
        LocalBorealisLook provides look,
        LocalBorealisColors provides colors,
        LocalDensity provides density,
        LocalAppliedTextSize provides look.textSize.scale,
        // Material leaves the content colour black; text and icons outside a Surface need ink.
        LocalContentColor provides colors.ink,
        LocalBorealisType provides remember(formFactor) { borealisType(formFactor) },
        LocalBorealisShapes provides remember { borealisExtraShapes() },
        LocalBorealisSpacing provides remember(formFactor) { borealisSpacing(formFactor) },
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}
