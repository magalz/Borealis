package io.github.magalz.borealis.settings.appearance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
 * The option of the Appearance group that the stream reads (ADR 0061, decision 1): the style of the
 * menu in the stream, Blurred or Solid. It is stored as a stable string id, like the Background,
 * and starts as Blurred. The option for the background before and after the stream does not exist:
 * those screens follow the Background of the Library.
 */
@RunWith(RobolectricTestRunner::class)
class StreamAppearanceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val row: SettingsItem
        get() {
            val row = SettingsItem.entries.singleOrNull { it.setting == Settings.STREAM_MENU_GLASS }
            assertWithMessage("the row of the stream menu style").that(row).isNotNull()
            return row!!
        }

    @Test
    fun `the stream menu style is a string in DataStore that starts as Blurred`() {
        val setting = Settings.STREAM_MENU_GLASS

        assertThat(setting).isInstanceOf(StringSetting::class.java)
        assertThat(setting.store).isEqualTo(SettingStore.DATASTORE)
        assertThat(Settings.all).contains(setting)
        assertThat(setting.key).isEqualTo("borealis_stream_menu_glass")
        assertThat(setting.default).isEqualTo("blur")
    }

    @Test
    fun `it is a row of the Appearance group that opens a list`() {
        assertThat(row.group).isEqualTo(SettingsGroupId.APPEARANCE)
        assertThat(row.kind).isEqualTo(SettingsItemKind.CHOICE)
    }

    @Test
    fun `the Appearance group has eight rows, with the stream menu style after the intensity`() {
        val appearance = SettingsItem.entries.filter { it.group == SettingsGroupId.APPEARANCE }

        assertThat(appearance.map { it.setting })
            .containsExactly(
                Settings.FONT,
                Settings.ACCENT,
                Settings.BACKGROUND,
                Settings.BACKGROUND_INTENSITY,
                Settings.STREAM_MENU_GLASS,
                Settings.CARD_DENSITY,
                Settings.GLASS_TINT,
                Settings.GLASS_BLUR,
            )
            .inOrder()
    }

    @Test
    fun `the stream menu style offers Blurred and Solid under the stable ids blur and solid`() {
        assertThat(context.getString(row.titleRes)).isEqualTo("Stream menu style")
        assertThat(row.input.fixedChoices.map { it.key }).containsExactly("blur", "solid").inOrder()
        assertThat(row.input.fixedChoices.map { context.getString(it.labelRes) })
            .containsExactly("Blurred", "Solid")
            .inOrder()
    }

    @Test
    fun `the background before and after the stream is no longer a setting or a row`() {
        val dropped = "borealis_stream_background"

        assertThat(Settings.all.map { it.key }).doesNotContain(dropped)
        assertThat(SettingsItem.entries.mapNotNull { it.setting?.key }).doesNotContain(dropped)
    }

    @Test
    fun `the summary is one short sentence, so that a row stays at two lines on a phone`() {
        val summary = context.getString(checkNotNull(row.summaryRes))

        assertWithMessage("the summary is \"$summary\"")
            .that(summary.length)
            .isAtMost(MAX_SUMMARY_LENGTH)
        assertThat(summary.trimEnd('.')).doesNotContain(". ")
    }

    @Test
    fun `the summary says what each of its two choices does`() {
        val summary = context.getString(checkNotNull(row.summaryRes))

        assertThat(summary).contains("Blurred")
        assertThat(summary).contains("Solid")
    }

    @Test
    fun `a stored value that no choice has shows the default as the chosen one`() {
        for (unknown in listOf("bogus", "Blur", "Blurred")) {
            val stored = SettingsSnapshot.fromStored(mapOf(Settings.STREAM_MENU_GLASS to unknown))

            assertThat(row.selectedChoiceKey(stored)).isEqualTo("blur")
        }
    }

    @Test
    fun `a stored choice is the chosen one`() {
        val stored = SettingsSnapshot.fromStored(mapOf(Settings.STREAM_MENU_GLASS to "solid"))

        assertThat(row.selectedChoiceKey(stored)).isEqualTo("solid")
    }

    private companion object {
        /** The summary of Glass tint, the longest one of the group that fits two lines, is 62. */
        const val MAX_SUMMARY_LENGTH = 70
    }
}
