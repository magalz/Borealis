package io.github.magalz.borealis.settings.ui

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeSettingsOptions
import io.github.magalz.borealis.settings.testing.deviceFacts
import org.junit.Test

/**
 * A row depends only on its own value. Every pane stays composed, so a row that read the whole
 * state would recompose in every pane whenever any setting changed.
 */
class SettingsRowValueTest {
    private val builder = SettingsStateBuilder(FakeSettingsOptions())

    private fun state(vararg values: Pair<Setting<*>, Any?>): SettingsUiState =
        builder.build(SettingsSnapshot.fromStored(values.toMap()), deviceFacts())

    @Test
    fun `the value of a row does not change when another setting does`() {
        val before = state(Settings.FULL_RANGE to false)
        val after = state(Settings.FULL_RANGE to true)

        val others =
            SettingsItem.entries.filter { it != SettingsItem.FULL_RANGE && it.setting != null }

        for (item in others) {
            assertThat(after.rowValue(item)).isEqualTo(before.rowValue(item))
        }
    }

    @Test
    fun `the value of a row changes with its own setting`() {
        val changes =
            mapOf(
                SettingsItem.HDR to (Settings.ENABLE_HDR to true),
                SettingsItem.CODEC to (Settings.VIDEO_FORMAT to "h265"),
                SettingsItem.FRAME_PACING to (Settings.FRAME_PACING to "smoothness"),
                SettingsItem.DEADZONE to (Settings.DEADZONE to 11),
                SettingsItem.TOUCH to
                    (Settings.TOUCHSCREEN_TRACKPAD to !Settings.TOUCHSCREEN_TRACKPAD.default),
                SettingsItem.RESOLUTION to (Settings.RESOLUTION to "1920x1080"),
                SettingsItem.FRAME_RATE to (Settings.FRAME_RATE to "30"),
                SettingsItem.BITRATE to (Settings.BITRATE_KBPS to 15_000),
            )
        val before = state()

        for ((item, change) in changes) {
            assertThat(state(change).rowValue(item)).isNotEqualTo(before.rowValue(item))
        }
    }

    @Test
    fun `the bitrate row follows the automatic bitrate`() {
        val automatic = state()
        val stored = state(Settings.BITRATE_KBPS to automatic.bitrateKbps)

        assertThat(stored.rowValue(SettingsItem.BITRATE))
            .isNotEqualTo(automatic.rowValue(SettingsItem.BITRATE))
    }
}
