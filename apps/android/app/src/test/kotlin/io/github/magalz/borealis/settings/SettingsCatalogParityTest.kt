package io.github.magalz.borealis.settings

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import org.junit.Test

/**
 * The shape of the Kotlin settings catalog (ADR 0055): unique keys, the keys that were not ported,
 * the overlay choice and the two DataStore settings. The defaults are compared with what the Java
 * readers use in [SettingsDefaultsParityTest].
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
    fun `only the two logging switches are stored in DataStore`() {
        val datastore = Settings.all.filter { it.store == SettingStore.DATASTORE }.map { it.key }

        assertThat(datastore)
            .containsExactly(Settings.VERBOSE_LOGGING.key, Settings.MASK_ADDRESSES.key)
    }

    private companion object {
        /** The language list and the small icon mode are not ported (ADR 0055, decision 6). */
        val DROPPED_KEYS = setOf("list_languages", "checkbox_small_icon_mode")
    }
}
