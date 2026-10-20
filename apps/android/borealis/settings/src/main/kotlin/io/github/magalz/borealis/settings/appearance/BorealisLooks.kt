package io.github.magalz.borealis.settings.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import io.github.magalz.borealis.settings.repository.SettingsRepository
import io.github.magalz.borealis.settings.repository.SettingsSnapshot

/** A stored id and the value of the look that it stands for. */
internal class LookChoice<T>(val id: String, val value: T)

/**
 * How the Appearance and Accessibility settings store their choices: by a stable string id, never
 * by the ordinal of an enum, so that reordering or adding a value cannot change what a user chose.
 * A stored id that no choice has stands for the default of the setting.
 */
internal object LookChoices {
    val REDUCE_MOTION =
        listOf(
            LookChoice(Settings.REDUCE_MOTION_FOLLOW_SYSTEM, ReduceMotion.FollowSystem),
            LookChoice(Settings.REDUCE_MOTION_ON, ReduceMotion.On),
            LookChoice(Settings.REDUCE_MOTION_OFF, ReduceMotion.Off),
        )

    val TRANSPARENCY =
        listOf(
            LookChoice(Settings.TRANSPARENCY_STANDARD, Transparency.Standard),
            LookChoice(Settings.TRANSPARENCY_REDUCED, Transparency.Reduced),
            LookChoice(Settings.TRANSPARENCY_OFF, Transparency.Off),
        )

    val TEXT_SIZE =
        listOf(
            LookChoice(Settings.TEXT_SIZE_DEFAULT, TextSize.Default),
            LookChoice(Settings.TEXT_SIZE_LARGER, TextSize.Larger),
            LookChoice(Settings.TEXT_SIZE_LARGEST, TextSize.Largest),
        )

    val COLOUR_VISION =
        listOf(
            LookChoice(Settings.COLOUR_VISION_OFF, ColourVision.Off),
            LookChoice(Settings.COLOUR_VISION_RED_GREEN, ColourVision.RedGreen),
            LookChoice(Settings.COLOUR_VISION_BLUE_YELLOW, ColourVision.BlueYellow),
        )

    /** The glass tints in percent, from the most see-through to the most opaque. */
    val GLASS_TINT = listOf(30, 40, 50, 60, 70, 85).map { LookChoice(it.toString(), it) }

    /** The glass blur radii in dp. */
    val GLASS_BLUR = listOf(8, 16, 24, 32, 48).map { LookChoice(it.toString(), it) }

    private val lists: Map<Setting<*>, List<LookChoice<*>>> =
        mapOf(
            Settings.REDUCE_MOTION to REDUCE_MOTION,
            Settings.TRANSPARENCY to TRANSPARENCY,
            Settings.TEXT_SIZE to TEXT_SIZE,
            Settings.COLOUR_VISION to COLOUR_VISION,
            Settings.GLASS_TINT to GLASS_TINT,
            Settings.GLASS_BLUR to GLASS_BLUR,
        )

    /**
     * The id that the list of [setting] shows as chosen when [stored] is in the store: [stored]
     * when a choice has that id, otherwise the default of the setting. It is `null` for a setting
     * that is not a list of the look.
     */
    fun shownId(setting: Setting<*>, stored: String): String? =
        lists[setting]?.let { choices ->
            if (choices.any { it.id == stored }) stored else setting.default as String
        }

    /** The value that [snapshot] stores for [setting], or the one of its default. */
    fun <T> List<LookChoice<T>>.valueIn(setting: StringSetting, snapshot: SettingsSnapshot): T {
        val id = snapshot[setting]
        return (firstOrNull { it.id == id } ?: first { it.id == setting.default }).value
    }
}

/** The look that [snapshot] stores: the defaults of [BorealisLook] for what is not stored. */
fun borealisLook(snapshot: SettingsSnapshot): BorealisLook =
    with(LookChoices) {
        BorealisLook(
            transparency = TRANSPARENCY.valueIn(Settings.TRANSPARENCY, snapshot),
            glassTintPercent = GLASS_TINT.valueIn(Settings.GLASS_TINT, snapshot),
            glassBlurDp = GLASS_BLUR.valueIn(Settings.GLASS_BLUR, snapshot),
            reduceMotion = REDUCE_MOTION.valueIn(Settings.REDUCE_MOTION, snapshot),
            thickFocus = snapshot[Settings.THICK_FOCUS],
            textSize = TEXT_SIZE.valueIn(Settings.TEXT_SIZE, snapshot),
            highContrast = snapshot[Settings.HIGH_CONTRAST],
            colourVision = COLOUR_VISION.valueIn(Settings.COLOUR_VISION, snapshot),
        )
    }

/**
 * The look that the screens of the Library draw with: the one that [settings] stores now, and the
 * next one as soon as the user changes a setting of it.
 */
@Composable
fun rememberBorealisLook(settings: SettingsRepository): BorealisLook {
    val snapshot = settings.snapshot.collectAsStateWithLifecycle()
    // A change of a setting that is not in the look makes a new snapshot but not a new look: it
    // recomposes nothing.
    val look =
        remember(snapshot) {
            derivedStateOf(structuralEqualityPolicy()) { borealisLook(snapshot.value) }
        }
    return look.value
}
