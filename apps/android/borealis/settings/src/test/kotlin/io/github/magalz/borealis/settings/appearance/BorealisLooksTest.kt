package io.github.magalz.borealis.settings.appearance

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.ui.FixedChoice
import io.github.magalz.borealis.settings.ui.FixedChoices
import org.junit.Test

/**
 * The look that the settings store (ADR 0057): stable string ids, never the ordinals of the enums
 * of the look, and the defaults of [BorealisLook] for nothing stored or an id that nothing has.
 */
class BorealisLooksTest {
    private fun look(vararg values: Pair<Setting<*>, Any?>): BorealisLook =
        borealisLook(SettingsSnapshot.fromStored(values.toMap()))

    private val unknownIds =
        listOf("", "bogus", "ON", "Standard", "FOLLOW_SYSTEM", "2", " on", "Plex", "PLEX", "plex ")

    @Test
    fun `nothing stored gives the look of the app with nothing changed`() {
        assertThat(borealisLook(SettingsSnapshot.DEFAULTS)).isEqualTo(BorealisLook())
    }

    @Test
    fun `the settings are in DataStore under stable keys and start as the default look`() {
        val strings =
            mapOf(
                Settings.REDUCE_MOTION to ("borealis_reduce_motion" to "follow_system"),
                Settings.TRANSPARENCY to ("borealis_transparency" to "standard"),
                Settings.TEXT_SIZE to ("borealis_text_size" to "default"),
                Settings.COLOUR_VISION to ("borealis_colour_vision" to "off"),
                Settings.GLASS_TINT to ("borealis_glass_tint" to "40"),
                Settings.GLASS_BLUR to ("borealis_glass_blur" to "8"),
                Settings.FONT to ("borealis_font" to "plex"),
                Settings.ACCENT to ("borealis_accent" to "violet"),
                Settings.BACKGROUND_INTENSITY to ("borealis_background_intensity" to "100"),
                Settings.CARD_DENSITY to ("borealis_card_density" to "comfortable"),
                Settings.TEXT_SPACING to ("borealis_text_spacing" to "normal"),
            )
        for ((setting, expected) in strings) {
            assertThat(setting).isInstanceOf(StringSetting::class.java)
            assertThat(setting.key).isEqualTo(expected.first)
            assertThat(setting.default).isEqualTo(expected.second)
        }
        val switches =
            mapOf(
                Settings.THICK_FOCUS to "borealis_thick_focus",
                Settings.HIGH_CONTRAST to "borealis_high_contrast",
            )
        for ((setting, key) in switches) {
            assertThat(setting).isInstanceOf(BooleanSetting::class.java)
            assertThat(setting.key).isEqualTo(key)
            assertThat(setting.default).isFalse()
        }
        for (setting in strings.keys + switches.keys) {
            assertThat(setting.store).isEqualTo(SettingStore.DATASTORE)
            assertThat(Settings.all).contains(setting)
        }
    }

    @Test
    fun `the defaults of the settings are the defaults of the look`() {
        val defaults = BorealisLook()

        assertThat(Settings.GLASS_TINT.default).isEqualTo(defaults.glassTintPercent.toString())
        assertThat(Settings.GLASS_BLUR.default).isEqualTo(defaults.glassBlurDp.toString())
        assertThat(Settings.THICK_FOCUS.default).isEqualTo(defaults.thickFocus)
        assertThat(Settings.HIGH_CONTRAST.default).isEqualTo(defaults.highContrast)
        assertThat(defaults.font).isEqualTo(FontChoice.Plex)
        assertThat(defaults.accent).isEqualTo(AccentChoice.Violet)
        assertThat(defaults.backgroundIntensity).isEqualTo(BackgroundIntensity.Full)
        assertThat(defaults.cardDensity).isEqualTo(CardDensity.Comfortable)
        assertThat(defaults.textSpacing).isEqualTo(TextSpacing.Normal)
    }

    @Test
    fun `Reduce motion is stored as follow_system, on or off`() {
        assertThat(look(Settings.REDUCE_MOTION to "follow_system").reduceMotion)
            .isEqualTo(ReduceMotion.FollowSystem)
        assertThat(look(Settings.REDUCE_MOTION to "on").reduceMotion).isEqualTo(ReduceMotion.On)
        assertThat(look(Settings.REDUCE_MOTION to "off").reduceMotion).isEqualTo(ReduceMotion.Off)
    }

    @Test
    fun `Transparency is stored as standard, reduced or off`() {
        assertThat(look(Settings.TRANSPARENCY to "standard").transparency)
            .isEqualTo(Transparency.Standard)
        assertThat(look(Settings.TRANSPARENCY to "reduced").transparency)
            .isEqualTo(Transparency.Reduced)
        assertThat(look(Settings.TRANSPARENCY to "off").transparency).isEqualTo(Transparency.Off)
    }

    @Test
    fun `Text size is stored as default, larger or largest`() {
        assertThat(look(Settings.TEXT_SIZE to "default").textSize).isEqualTo(TextSize.Default)
        assertThat(look(Settings.TEXT_SIZE to "larger").textSize).isEqualTo(TextSize.Larger)
        assertThat(look(Settings.TEXT_SIZE to "largest").textSize).isEqualTo(TextSize.Largest)
    }

    @Test
    fun `The colour-blind palette is stored as off, red_green or blue_yellow`() {
        assertThat(look(Settings.COLOUR_VISION to "off").colourVision).isEqualTo(ColourVision.Off)
        assertThat(look(Settings.COLOUR_VISION to "red_green").colourVision)
            .isEqualTo(ColourVision.RedGreen)
        assertThat(look(Settings.COLOUR_VISION to "blue_yellow").colourVision)
            .isEqualTo(ColourVision.BlueYellow)
    }

    @Test
    fun `Font is stored as plex, atkinson_next, luciole, lexend, andika, b612 or system`() {
        val stored =
            mapOf(
                "plex" to FontChoice.Plex,
                "atkinson_next" to FontChoice.AtkinsonNext,
                "luciole" to FontChoice.Luciole,
                "lexend" to FontChoice.Lexend,
                "andika" to FontChoice.Andika,
                "b612" to FontChoice.B612,
                "system" to FontChoice.System,
            )

        for ((id, font) in stored) {
            assertWithMessage("the id \"$id\"").that(look(Settings.FONT to id).font).isEqualTo(font)
        }
    }

    @Test
    fun `Accent colour is stored as violet, teal or white`() {
        assertThat(look(Settings.ACCENT to "violet").accent).isEqualTo(AccentChoice.Violet)
        assertThat(look(Settings.ACCENT to "teal").accent).isEqualTo(AccentChoice.Teal)
        assertThat(look(Settings.ACCENT to "white").accent).isEqualTo(AccentChoice.White)
    }

    @Test
    fun `Background intensity is stored as its percent and stands for a veil that darkens`() {
        val veils = mapOf("100" to 0f, "75" to 0.25f, "50" to 0.5f, "25" to 0.75f)

        for ((percent, veil) in veils) {
            val intensity = look(Settings.BACKGROUND_INTENSITY to percent).backgroundIntensity
            assertWithMessage("the id \"$percent\"").that(intensity.veil).isEqualTo(veil)
        }
        assertThat(look(Settings.BACKGROUND_INTENSITY to "100").backgroundIntensity)
            .isEqualTo(BackgroundIntensity.Full)
        assertThat(BackgroundIntensity.entries.map { it.veil }).isInStrictOrder()
    }

    @Test
    fun `Card density is stored as comfortable, compact or dense`() {
        assertThat(look(Settings.CARD_DENSITY to "comfortable").cardDensity)
            .isEqualTo(CardDensity.Comfortable)
        assertThat(look(Settings.CARD_DENSITY to "compact").cardDensity)
            .isEqualTo(CardDensity.Compact)
        assertThat(look(Settings.CARD_DENSITY to "dense").cardDensity).isEqualTo(CardDensity.Dense)
    }

    @Test
    fun `Text spacing is stored as normal, wide or wider`() {
        assertThat(look(Settings.TEXT_SPACING to "normal").textSpacing)
            .isEqualTo(TextSpacing.Normal)
        assertThat(look(Settings.TEXT_SPACING to "wide").textSpacing).isEqualTo(TextSpacing.Wide)
        assertThat(look(Settings.TEXT_SPACING to "wider").textSpacing).isEqualTo(TextSpacing.Wider)
    }

    @Test
    fun `the glass tint is stored as its percent`() {
        for (percent in listOf(30, 40, 50, 60, 70, 85)) {
            assertThat(look(Settings.GLASS_TINT to "$percent").glassTintPercent).isEqualTo(percent)
        }
    }

    @Test
    fun `the glass blur is stored as its dp`() {
        for (dp in listOf(8, 16, 24, 32, 48)) {
            assertThat(look(Settings.GLASS_BLUR to "$dp").glassBlurDp).isEqualTo(dp)
        }
    }

    @Test
    fun `the two switches are stored as booleans`() {
        val on =
            borealisLook(
                SettingsSnapshot.DEFAULTS.with(Settings.THICK_FOCUS, true)
                    .with(Settings.HIGH_CONTRAST, true)
            )

        assertThat(on.thickFocus).isTrue()
        assertThat(on.highContrast).isTrue()
        assertThat(on).isEqualTo(BorealisLook(thickFocus = true, highContrast = true))
    }

    @Test
    fun `an id that no choice has stands for the default`() {
        for (id in unknownIds) {
            val unknown =
                look(
                    Settings.REDUCE_MOTION to id,
                    Settings.TRANSPARENCY to id,
                    Settings.TEXT_SIZE to id,
                    Settings.COLOUR_VISION to id,
                    Settings.FONT to id,
                    Settings.ACCENT to id,
                    Settings.CARD_DENSITY to id,
                    Settings.TEXT_SPACING to id,
                )
            assertWithMessage("the id \"$id\"").that(unknown).isEqualTo(BorealisLook())
        }
    }

    @Test
    fun `a glass tint or blur that is not offered stands for the default`() {
        val notOffered = listOf("", "bogus", "0", "35", "100", "-40", "40.0", " 40", "8dp")

        for (id in notOffered) {
            val unknown = look(Settings.GLASS_TINT to id, Settings.GLASS_BLUR to id)
            assertWithMessage("the id \"$id\"").that(unknown).isEqualTo(BorealisLook())
        }
        assertThat(look(Settings.GLASS_BLUR to "30").glassBlurDp).isEqualTo(8)
        assertThat(look(Settings.GLASS_TINT to "16").glassTintPercent).isEqualTo(40)
    }

    @Test
    fun `a background intensity that is not offered stands for the default`() {
        val notOffered = listOf("", "bogus", "0", "10", "90", "125", "-50", "50.0", " 50", "50 %")

        for (id in notOffered) {
            val unknown = look(Settings.BACKGROUND_INTENSITY to id)
            assertWithMessage("the id \"$id\"").that(unknown).isEqualTo(BorealisLook())
        }
        assertThat(look(Settings.BACKGROUND_INTENSITY to "60").backgroundIntensity)
            .isEqualTo(BackgroundIntensity.Full)
    }

    private fun <T> assertOneEntryPerValue(
        setting: StringSetting,
        entries: List<FixedChoice>,
        values: List<T>,
        read: (BorealisLook) -> T,
    ) {
        val seen = entries.map { read(look(setting to it.key)) }

        assertThat(entries.map { it.key }).containsNoDuplicates()
        assertThat(seen).containsExactlyElementsIn(values).inOrder()
        assertThat(entries.map { it.key }).contains(setting.default)
    }

    @Test
    fun `every entry of a list is an id that maps to its own value, and every value has an entry`() {
        assertOneEntryPerValue(
            Settings.REDUCE_MOTION,
            FixedChoices.REDUCE_MOTION,
            ReduceMotion.entries,
        ) {
            it.reduceMotion
        }
        assertOneEntryPerValue(
            Settings.TRANSPARENCY,
            FixedChoices.TRANSPARENCY,
            Transparency.entries,
        ) {
            it.transparency
        }
        assertOneEntryPerValue(Settings.TEXT_SIZE, FixedChoices.TEXT_SIZE, TextSize.entries) {
            it.textSize
        }
        assertOneEntryPerValue(
            Settings.COLOUR_VISION,
            FixedChoices.COLOUR_VISION,
            ColourVision.entries,
        ) {
            it.colourVision
        }
        assertOneEntryPerValue(
            Settings.GLASS_TINT,
            FixedChoices.GLASS_TINT,
            listOf(30, 40, 50, 60, 70, 85),
        ) {
            it.glassTintPercent
        }
        assertOneEntryPerValue(
            Settings.GLASS_BLUR,
            FixedChoices.GLASS_BLUR,
            listOf(8, 16, 24, 32, 48),
        ) {
            it.glassBlurDp
        }
    }

    @Test
    fun `every entry of a list of Sprint 11 is an id that maps to its own value`() {
        assertOneEntryPerValue(Settings.FONT, FixedChoices.FONT, FontChoice.entries) { it.font }
        assertOneEntryPerValue(Settings.ACCENT, FixedChoices.ACCENT, AccentChoice.entries) {
            it.accent
        }
        assertOneEntryPerValue(
            Settings.BACKGROUND_INTENSITY,
            FixedChoices.BACKGROUND_INTENSITY,
            BackgroundIntensity.entries,
        ) {
            it.backgroundIntensity
        }
        assertOneEntryPerValue(
            Settings.CARD_DENSITY,
            FixedChoices.CARD_DENSITY,
            CardDensity.entries,
        ) {
            it.cardDensity
        }
        assertOneEntryPerValue(
            Settings.TEXT_SPACING,
            FixedChoices.TEXT_SPACING,
            TextSpacing.entries,
        ) {
            it.textSpacing
        }
    }

    @Test
    fun `each setting changes its own field and nothing else`() {
        val look =
            look(
                Settings.REDUCE_MOTION to "on",
                Settings.TRANSPARENCY to "reduced",
                Settings.TEXT_SIZE to "largest",
                Settings.COLOUR_VISION to "blue_yellow",
                Settings.GLASS_TINT to "70",
                Settings.GLASS_BLUR to "32",
                Settings.FONT to "lexend",
                Settings.ACCENT to "teal",
                Settings.BACKGROUND_INTENSITY to "50",
                Settings.CARD_DENSITY to "dense",
                Settings.TEXT_SPACING to "wider",
            )

        assertThat(look)
            .isEqualTo(
                BorealisLook(
                    transparency = Transparency.Reduced,
                    glassTintPercent = 70,
                    glassBlurDp = 32,
                    reduceMotion = ReduceMotion.On,
                    thickFocus = false,
                    textSize = TextSize.Largest,
                    highContrast = false,
                    colourVision = ColourVision.BlueYellow,
                    font = FontChoice.Lexend,
                    accent = AccentChoice.Teal,
                    backgroundIntensity = BackgroundIntensity.Half,
                    cardDensity = CardDensity.Dense,
                    textSpacing = TextSpacing.Wider,
                )
            )
    }
}
