package io.github.magalz.borealis.settings.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsRepository

/** A background style and the stable id that stores it. */
class BackgroundChoice(val id: String, val style: BackgroundStyle)

/**
 * The background styles that the Appearance setting offers, and how [Settings.BACKGROUND] stores
 * them: by a stable string id, never by the ordinal of the [BackgroundStyle], so that reordering or
 * adding a style cannot change what a user chose.
 */
object BackgroundChoices {
    /** Every style, in the order of the Background row. The first is the default. */
    val all: List<BackgroundChoice> =
        listOf(
            BackgroundChoice(Settings.BACKGROUND_AURORA_CURTAINS, BackgroundStyle.AuroraCurtains),
            BackgroundChoice(Settings.BACKGROUND_SOFT_GLOW, BackgroundStyle.SoftGlow),
            BackgroundChoice(Settings.BACKGROUND_NORTHERN_LIGHTS, BackgroundStyle.NorthernLights),
            BackgroundChoice(Settings.BACKGROUND_LIGHT_MIST, BackgroundStyle.LightMist),
            BackgroundChoice(Settings.BACKGROUND_FLOWING_LIGHT, BackgroundStyle.FlowingLight),
            BackgroundChoice(Settings.BACKGROUND_STARFIELD, BackgroundStyle.Starfield),
            BackgroundChoice(Settings.BACKGROUND_OFF, BackgroundStyle.Off),
        )

    /** The style that the stored [id] stands for: the default style when no style has that id. */
    fun styleOf(id: String): BackgroundStyle =
        all.firstOrNull { it.id == id }?.style ?: BackgroundStyle.Default

    /** The id that stores [style]. */
    fun idOf(style: BackgroundStyle): String = all.first { it.style == style }.id
}

/**
 * The style that the Library draws behind its screens: the one that [settings] stores now, and the
 * next one as soon as the user chooses it. An unknown stored value gives the default style.
 */
@Composable
fun rememberBackgroundStyle(settings: SettingsRepository): BackgroundStyle {
    val snapshot = settings.snapshot.collectAsStateWithLifecycle()
    // A change of another setting makes a new snapshot but not a new style: it recomposes nothing.
    val style =
        remember(snapshot) {
            derivedStateOf { BackgroundChoices.styleOf(snapshot.value[Settings.BACKGROUND]) }
        }
    return style.value
}
