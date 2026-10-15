package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalFormFactor = staticCompositionLocalOf { FormFactor.Phone }

/** The dark Borealis theme: Material 3 plus the Borealis colours, type, shapes and spacing. */
@Composable
fun BorealisTheme(formFactor: FormFactor = FormFactor.Phone, content: @Composable () -> Unit) {
    val colorScheme = remember { borealisColorScheme() }
    val shapes = remember { borealisShapes() }
    val typography = remember(formFactor) { borealisTypography(formFactor) }
    val colors = remember { BorealisColors() }
    CompositionLocalProvider(
        LocalFormFactor provides formFactor,
        LocalBorealisColors provides colors,
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
