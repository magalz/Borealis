package io.github.magalz.borealis.settings.appearance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.ui.SettingsGroupId
import io.github.magalz.borealis.settings.ui.SettingsItem
import io.github.magalz.borealis.settings.ui.SettingsItemKind
import io.github.magalz.borealis.settings.ui.selectedChoiceKey
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two options of the Appearance group that the stream reads (ADR 0061, decision 1): the glass
 * of the menu in the stream, and the background on the screens outside the game. Both are stored as
 * stable string ids, like the Background, and start as Blur and Moving.
 */
@RunWith(RobolectricTestRunner::class)
class StreamAppearanceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun rowOf(setting: Setting<*>): SettingsItem {
        val row = SettingsItem.entries.singleOrNull { it.setting == setting }
        assertWithMessage("the row of ${setting.key}").that(row).isNotNull()
        return row!!
    }

    private fun labelsOf(row: SettingsItem) =
        row.input.fixedChoices.map { context.getString(it.labelRes) }

    @Test
    fun `the two options are strings in DataStore that start as Blur and Moving`() {
        for (setting in listOf(Settings.STREAM_MENU_GLASS, Settings.STREAM_BACKGROUND)) {
            assertThat(setting).isInstanceOf(StringSetting::class.java)
            assertThat(setting.store).isEqualTo(SettingStore.DATASTORE)
            assertThat(Settings.all).contains(setting)
        }
        assertThat(Settings.STREAM_MENU_GLASS.key).isEqualTo("borealis_stream_menu_glass")
        assertThat(Settings.STREAM_MENU_GLASS.default).isEqualTo("blur")
        assertThat(Settings.STREAM_BACKGROUND.key).isEqualTo("borealis_stream_background")
        assertThat(Settings.STREAM_BACKGROUND.default).isEqualTo("moving")
    }

    @Test
    fun `both are rows of the Appearance group that open a list`() {
        for (setting in listOf(Settings.STREAM_MENU_GLASS, Settings.STREAM_BACKGROUND)) {
            val row = rowOf(setting)

            assertThat(row.group).isEqualTo(SettingsGroupId.APPEARANCE)
            assertThat(row.kind).isEqualTo(SettingsItemKind.CHOICE)
        }
    }

    @Test
    fun `the Appearance group has them after the intensity of the background`() {
        val appearance = SettingsItem.entries.filter { it.group == SettingsGroupId.APPEARANCE }

        assertThat(appearance.map { it.setting })
            .containsExactly(
                Settings.FONT,
                Settings.ACCENT,
                Settings.BACKGROUND,
                Settings.BACKGROUND_INTENSITY,
                Settings.STREAM_BACKGROUND,
                Settings.STREAM_MENU_GLASS,
                Settings.CARD_DENSITY,
                Settings.GLASS_TINT,
                Settings.GLASS_BLUR,
            )
            .inOrder()
    }

    @Test
    fun `the menu glass offers Blur and Solid under their stable ids`() {
        val row = rowOf(Settings.STREAM_MENU_GLASS)

        assertThat(context.getString(row.titleRes)).isEqualTo("Menu glass in the stream")
        assertThat(row.input.fixedChoices.map { it.key }).containsExactly("blur", "solid").inOrder()
        assertThat(labelsOf(row)).containsExactly("Blur", "Solid").inOrder()
    }

    @Test
    fun `the background offers Moving, Still and Off under their stable ids`() {
        val row = rowOf(Settings.STREAM_BACKGROUND)

        assertThat(context.getString(row.titleRes))
            .isEqualTo("Background before and after the stream")
        assertThat(row.input.fixedChoices.map { it.key })
            .containsExactly("moving", "still", "off")
            .inOrder()
        assertThat(labelsOf(row)).containsExactly("Moving", "Still", "Off").inOrder()
    }

    private fun summaryOf(setting: Setting<*>): String =
        context.getString(checkNotNull(rowOf(setting).summaryRes))

    @Test
    fun `each summary is one short sentence, so that a row stays at two lines on a phone`() {
        for (setting in listOf(Settings.STREAM_MENU_GLASS, Settings.STREAM_BACKGROUND)) {
            val summary = summaryOf(setting)

            assertWithMessage("the summary of ${setting.key} is \"$summary\"")
                .that(summary.length)
                .isAtMost(MAX_SUMMARY_LENGTH)
            assertThat(summary.trimEnd('.')).doesNotContain(". ")
        }
    }

    @Test
    fun `the summary of the background says what each of its three choices does`() {
        val summary = summaryOf(Settings.STREAM_BACKGROUND)

        assertThat(summary).contains("Moving")
        assertThat(summary).contains("Still")
        assertThat(summary).contains("Off")
    }

    @Test
    fun `a stored value that no choice has shows the default as the chosen one`() {
        for (setting in listOf(Settings.STREAM_MENU_GLASS, Settings.STREAM_BACKGROUND)) {
            val unknown = SettingsSnapshot.fromStored(mapOf(setting to "bogus"))

            assertThat(rowOf(setting).selectedChoiceKey(unknown)).isEqualTo(setting.default)
        }
    }

    @Test
    fun `a stored choice is the chosen one`() {
        val stored =
            SettingsSnapshot.fromStored(
                mapOf(
                    Settings.STREAM_MENU_GLASS to "solid",
                    Settings.STREAM_BACKGROUND to "off",
                )
            )

        assertThat(rowOf(Settings.STREAM_MENU_GLASS).selectedChoiceKey(stored)).isEqualTo("solid")
        assertThat(rowOf(Settings.STREAM_BACKGROUND).selectedChoiceKey(stored)).isEqualTo("off")
    }

    private companion object {
        /** The summary of Glass tint, the longest one of the group that fits two lines, is 62. */
        const val MAX_SUMMARY_LENGTH = 70
    }
}
