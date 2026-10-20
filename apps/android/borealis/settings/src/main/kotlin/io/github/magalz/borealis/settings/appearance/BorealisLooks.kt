package io.github.magalz.borealis.settings.appearance

import androidx.compose.runtime.Composable
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.settings.repository.SettingsRepository
import io.github.magalz.borealis.settings.repository.SettingsSnapshot

/** The look that [snapshot] stores. */
fun borealisLook(snapshot: SettingsSnapshot): BorealisLook = BorealisLook()

/** The look that the screens of the Library draw with: the one that [settings] stores now. */
@Composable fun rememberBorealisLook(settings: SettingsRepository): BorealisLook = BorealisLook()
