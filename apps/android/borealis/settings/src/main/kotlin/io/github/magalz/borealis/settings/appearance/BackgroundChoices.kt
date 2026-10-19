package io.github.magalz.borealis.settings.appearance

import androidx.compose.runtime.Composable
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.settings.repository.SettingsRepository

/** A background style and the stable id that stores it. */
class BackgroundChoice(val id: String, val style: BackgroundStyle)

/** The background styles that the Appearance setting offers. */
object BackgroundChoices {
    val all: List<BackgroundChoice> = emptyList()

    fun styleOf(id: String): BackgroundStyle = BackgroundStyle.Off

    fun idOf(style: BackgroundStyle): String = ""
}

/** The style that the user chose. */
@Composable
fun rememberBackgroundStyle(settings: SettingsRepository): BackgroundStyle = BackgroundStyle.Default
