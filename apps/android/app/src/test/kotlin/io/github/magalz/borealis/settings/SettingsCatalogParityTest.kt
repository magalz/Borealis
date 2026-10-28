package io.github.magalz.borealis.settings

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import org.junit.Test

/**
 * The shape of the Kotlin settings catalog (ADR 0055): unique keys, the keys that were not ported,
 * the overlay choice, the background choice and the DataStore settings. The defaults are compared
 * with what the Java readers use in [SettingsDefaultsParityTest].
 */
class SettingsCatalogParityTest {
    private fun settingByKey(key: String): Setting<*>? = Settings.all.firstOrNull { it.key == key }

    @Test
    fun `the dropped keys are not in the catalog`() {
        for (key in DROPPED_KEYS) {
            assertThat(settingByKey(key)).isNull()
        }
    }

    @Test
    fun `the catalog has no duplicate key`() {
        val keys = Settings.all.map { it.key }

        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `the new performance overlay choice is a String that is off by default`() {
        assertThat(Settings.PERF_OVERLAY.key).isEqualTo("borealis_perf_overlay")
        assertThat(Settings.PERF_OVERLAY).isInstanceOf(StringSetting::class.java)
        assertThat(Settings.PERF_OVERLAY.default).isEqualTo("off")
        assertThat(Settings.PERF_OVERLAY.store).isEqualTo(SettingStore.PREFS)
        assertThat(
                listOf(
                    Settings.PERF_OVERLAY_OFF,
                    Settings.PERF_OVERLAY_COMPACT,
                    Settings.PERF_OVERLAY_DETAILED,
                )
            )
            .containsExactly("off", "compact", "detailed")
            .inOrder()
    }

    @Test
    fun `the background is a String in DataStore that starts as the Aurora curtains`() {
        assertThat(Settings.BACKGROUND.key).isEqualTo("borealis_background")
        assertThat(Settings.BACKGROUND).isInstanceOf(StringSetting::class.java)
        assertThat(Settings.BACKGROUND.default).isEqualTo("aurora_curtains")
        assertThat(Settings.BACKGROUND.store).isEqualTo(SettingStore.DATASTORE)
    }

    @Test
    fun `only the logging switches, the background and the look settings are stored in DataStore`() {
        val datastore = Settings.all.filter { it.store == SettingStore.DATASTORE }.map { it.key }

        assertThat(datastore)
            .containsExactly(
                Settings.VERBOSE_LOGGING.key,
                Settings.MASK_ADDRESSES.key,
                Settings.BACKGROUND.key,
                Settings.GLASS_TINT.key,
                Settings.GLASS_BLUR.key,
                Settings.REDUCE_MOTION.key,
                Settings.TRANSPARENCY.key,
                Settings.THICK_FOCUS.key,
                Settings.TEXT_SIZE.key,
                Settings.HIGH_CONTRAST.key,
                Settings.COLOUR_VISION.key,
                Settings.FONT.key,
                Settings.ACCENT.key,
                Settings.BACKGROUND_INTENSITY.key,
                Settings.CARD_DENSITY.key,
                Settings.TEXT_SPACING.key,
            )
    }

    @Test
    fun `the font, accent, intensity, density and spacing are Strings in DataStore`() {
        val keys =
            mapOf(
                Settings.FONT to "borealis_font",
                Settings.ACCENT to "borealis_accent",
                Settings.BACKGROUND_INTENSITY to "borealis_background_intensity",
                Settings.CARD_DENSITY to "borealis_card_density",
                Settings.TEXT_SPACING to "borealis_text_spacing",
            )

        for ((setting, key) in keys) {
            assertThat(setting.key).isEqualTo(key)
            assertThat(setting).isInstanceOf(StringSetting::class.java)
            assertThat(setting.store).isEqualTo(SettingStore.DATASTORE)
            assertThat(Settings.all).contains(setting)
        }
    }

    private companion object {
        /** The language list and the small icon mode are not ported (ADR 0055, decision 6). */
        val DROPPED_KEYS = setOf("list_languages", "checkbox_small_icon_mode")
    }
}
