@file:Suppress("MatchingDeclarationName", "UNUSED_PARAMETER")

package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** How a row of settings looks: danger for what loses something. */
enum class SettingsRowTone {
    Normal,
    Danger,
}

/** A row with a switch at the end. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    disabledNote: String? = null,
) {
    // Stub: the behaviour comes with the implementation.
}

/** A row that shows its current value, or none for a link or an action. */
@Composable
fun SettingsValueRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    disabledNote: String? = null,
    tone: SettingsRowTone = SettingsRowTone.Normal,
) {
    // Stub: the behaviour comes with the implementation.
}
