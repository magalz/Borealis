package io.github.magalz.borealis.settings.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BackgroundIntensity
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import io.github.magalz.borealis.settings.repository.SettingsRepository
import io.github.magalz.borealis.settings.repository.SettingsSnapshot

/** A stored id and the value that it stands for. */
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

/** Every list of the look, and the list of backgrounds. */
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

    val FONT =
        LookList(
            Settings.FONT,
            listOf(
                LookChoice(Settings.FONT_PLEX, FontChoice.Plex),
                LookChoice(Settings.FONT_ATKINSON_NEXT, FontChoice.AtkinsonNext),
                LookChoice(Settings.FONT_LUCIOLE, FontChoice.Luciole),
                LookChoice(Settings.FONT_LEXEND, FontChoice.Lexend),
                LookChoice(Settings.FONT_ANDIKA, FontChoice.Andika),
                LookChoice(Settings.FONT_B612, FontChoice.B612),
                LookChoice(Settings.FONT_SYSTEM, FontChoice.System),
            ),
        )

    val ACCENT =
        LookList(
            Settings.ACCENT,
            listOf(
                LookChoice(Settings.ACCENT_VIOLET, AccentChoice.Violet),
                LookChoice(Settings.ACCENT_TEAL, AccentChoice.Teal),
                LookChoice(Settings.ACCENT_WHITE, AccentChoice.White),
            ),
        )

    /**
     * The intensities of the background: the id is the percent, from the strongest to the least.
     */
    val BACKGROUND_INTENSITY =
        LookList(
            Settings.BACKGROUND_INTENSITY,
            listOf(
                LookChoice("100", BackgroundIntensity.Full),
                LookChoice("75", BackgroundIntensity.ThreeQuarters),
                LookChoice("50", BackgroundIntensity.Half),
                LookChoice("25", BackgroundIntensity.Quarter),
            ),
        )

    /**
     * The Stream menu style, the stream's own option: the stream maps the stored id to what it
     * draws, so each choice is its own value. It is here so that the row shows the default for an
     * id that no choice has.
     */
    val STREAM_MENU_GLASS =
        LookList(
            Settings.STREAM_MENU_GLASS,
            listOf(Settings.STREAM_MENU_GLASS_BLUR, Settings.STREAM_MENU_GLASS_SOLID).map {
                LookChoice(it, it)
            },
        )

    val CARD_DENSITY =
        LookList(
            Settings.CARD_DENSITY,
            listOf(
                LookChoice(Settings.CARD_DENSITY_COMFORTABLE, CardDensity.Comfortable),
                LookChoice(Settings.CARD_DENSITY_COMPACT, CardDensity.Compact),
                LookChoice(Settings.CARD_DENSITY_DENSE, CardDensity.Dense),
            ),
        )

    val TEXT_SPACING =
        LookList(
            Settings.TEXT_SPACING,
            listOf(
                LookChoice(Settings.TEXT_SPACING_NORMAL, TextSpacing.Normal),
                LookChoice(Settings.TEXT_SPACING_WIDE, TextSpacing.Wide),
                LookChoice(Settings.TEXT_SPACING_WIDER, TextSpacing.Wider),
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
        listOf(
            BackgroundChoices.all,
            REDUCE_MOTION,
            TRANSPARENCY,
            TEXT_SIZE,
            COLOUR_VISION,
            FONT,
            ACCENT,
            BACKGROUND_INTENSITY,
            STREAM_MENU_GLASS,
            CARD_DENSITY,
            TEXT_SPACING,
            GLASS_TINT,
            GLASS_BLUR,
        )

    /** The list that [setting] stores a choice of, or `null` when it is not one of these lists. */
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
        font = LookLists.FONT.valueIn(snapshot),
        accent = LookLists.ACCENT.valueIn(snapshot),
        backgroundIntensity = LookLists.BACKGROUND_INTENSITY.valueIn(snapshot),
        cardDensity = LookLists.CARD_DENSITY.valueIn(snapshot),
        textSpacing = LookLists.TEXT_SPACING.valueIn(snapshot),
    )

/** The background style that [snapshot] stores: the default style for what is not stored. */
fun backgroundStyle(snapshot: SettingsSnapshot): BackgroundStyle =
    BackgroundChoices.all.valueIn(snapshot)

/**
 * What the screens of the Library draw with: the [look] of their text, colours and glass, and the
 * [background] behind them.
 */
@Immutable data class Looks(val look: BorealisLook, val background: BackgroundStyle)

/** The looks that [snapshot] stores: the defaults for what is not stored. */
private fun looksOf(snapshot: SettingsSnapshot): Looks =
    Looks(borealisLook(snapshot), backgroundStyle(snapshot))

/**
 * The looks that the screens of the Library draw with: the ones that [settings] stores now, and the
 * next ones as soon as the user changes a setting of them. The snapshot is collected once for both.
 */
@Composable
fun rememberLooks(settings: SettingsRepository): Looks {
    val snapshot = settings.snapshot.collectAsStateWithLifecycle()
    // A change of a setting that is not in the looks makes a new snapshot but not new looks: it
    // recomposes nothing.
    val looks =
        remember(snapshot) {
            derivedStateOf(structuralEqualityPolicy()) { looksOf(snapshot.value) }
        }
    return looks.value
}
