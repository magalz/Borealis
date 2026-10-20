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
 * The [choices] of a list in the Appearance and Accessibility groups, and the [setting] that stores
 * the id of one of them: by a stable string id, never by the ordinal of an enum, so that reordering
 * or adding a value cannot change what a user chose. A stored id that no choice has stands for the
 * default of the setting.
 */
internal class LookList<T>(val setting: StringSetting, val choices: List<LookChoice<T>>) {
    /**
     * The id to show as chosen when [stored] is in the store: itself, or the default if unknown.
     */
    fun shownId(stored: String): String =
        if (choices.any { it.id == stored }) stored else setting.default

    /** The value that [snapshot] stores for the setting, or the one of its default. */
    fun valueIn(snapshot: SettingsSnapshot): T {
        val id = shownId(snapshot[setting])
        return choices.first { it.id == id }.value
    }
}

/** Every list of the look. */
internal object LookLists {
    val REDUCE_MOTION =
        LookList(
            Settings.REDUCE_MOTION,
            listOf(
                LookChoice(Settings.REDUCE_MOTION_FOLLOW_SYSTEM, ReduceMotion.FollowSystem),
                LookChoice(Settings.REDUCE_MOTION_ON, ReduceMotion.On),
                LookChoice(Settings.REDUCE_MOTION_OFF, ReduceMotion.Off),
            ),
        )

    val TRANSPARENCY =
        LookList(
            Settings.TRANSPARENCY,
            listOf(
                LookChoice(Settings.TRANSPARENCY_STANDARD, Transparency.Standard),
                LookChoice(Settings.TRANSPARENCY_REDUCED, Transparency.Reduced),
                LookChoice(Settings.TRANSPARENCY_OFF, Transparency.Off),
            ),
        )

    val TEXT_SIZE =
        LookList(
            Settings.TEXT_SIZE,
            listOf(
                LookChoice(Settings.TEXT_SIZE_DEFAULT, TextSize.Default),
                LookChoice(Settings.TEXT_SIZE_LARGER, TextSize.Larger),
                LookChoice(Settings.TEXT_SIZE_LARGEST, TextSize.Largest),
            ),
        )

    val COLOUR_VISION =
        LookList(
            Settings.COLOUR_VISION,
            listOf(
                LookChoice(Settings.COLOUR_VISION_OFF, ColourVision.Off),
                LookChoice(Settings.COLOUR_VISION_RED_GREEN, ColourVision.RedGreen),
                LookChoice(Settings.COLOUR_VISION_BLUE_YELLOW, ColourVision.BlueYellow),
            ),
        )

    /** The glass tints in percent, from the most see-through to the most opaque. */
    val GLASS_TINT =
        LookList(
            Settings.GLASS_TINT,
            listOf(30, 40, 50, 60, 70, 85).map { LookChoice(it.toString(), it) },
        )

    /** The glass blur radii in dp. */
    val GLASS_BLUR =
        LookList(
            Settings.GLASS_BLUR,
            listOf(8, 16, 24, 32, 48).map { LookChoice(it.toString(), it) },
        )

    private val all: List<LookList<*>> =
        listOf(REDUCE_MOTION, TRANSPARENCY, TEXT_SIZE, COLOUR_VISION, GLASS_TINT, GLASS_BLUR)

    /** The list that [setting] stores a choice of, or `null` when it is not a list of the look. */
    fun of(setting: Setting<*>): LookList<*>? = all.firstOrNull { it.setting == setting }
}

/** The look that [snapshot] stores: the defaults of [BorealisLook] for what is not stored. */
fun borealisLook(snapshot: SettingsSnapshot): BorealisLook =
    BorealisLook(
        transparency = LookLists.TRANSPARENCY.valueIn(snapshot),
        glassTintPercent = LookLists.GLASS_TINT.valueIn(snapshot),
        glassBlurDp = LookLists.GLASS_BLUR.valueIn(snapshot),
        reduceMotion = LookLists.REDUCE_MOTION.valueIn(snapshot),
        thickFocus = snapshot[Settings.THICK_FOCUS],
        textSize = LookLists.TEXT_SIZE.valueIn(snapshot),
        highContrast = snapshot[Settings.HIGH_CONTRAST],
        colourVision = LookLists.COLOUR_VISION.valueIn(snapshot),
    )

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
