package io.github.magalz.borealis.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalFormFactor = staticCompositionLocalOf { FormFactor.Phone }

/** The dark Borealis theme: Material 3 plus the Borealis colours, type, shapes and spacing. */
@Composable
fun BorealisTheme(formFactor: FormFactor = FormFactor.Phone, content: @Composable () -> Unit) {
    TODO("not implemented")
}
