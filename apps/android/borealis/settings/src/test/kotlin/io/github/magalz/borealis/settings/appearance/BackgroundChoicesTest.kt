package io.github.magalz.borealis.settings.appearance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.ui.FixedChoices
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The stored form of the Appearance setting: stable string ids, never the ordinals of
 * [BackgroundStyle], so that reordering or adding a style cannot change what a user chose.
 */
@RunWith(RobolectricTestRunner::class)
class BackgroundChoicesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The style that a store with [id] for the Background setting stands for. */
    private fun styleOf(id: String): BackgroundStyle =
        BackgroundChoices.all.valueIn(SettingsSnapshot.fromStored(mapOf(Settings.BACKGROUND to id)))

    @Test
    fun `the background is a string in DataStore that starts as the Aurora curtains`() {
        assertThat(Settings.BACKGROUND.key).isEqualTo("borealis_background")
        assertThat(Settings.BACKGROUND.default).isEqualTo("aurora_curtains")
        assertThat(Settings.BACKGROUND.store).isEqualTo(SettingStore.DATASTORE)
        assertThat(Settings.all).contains(Settings.BACKGROUND)
        assertThat(styleOf(Settings.BACKGROUND.default)).isEqualTo(BackgroundStyle.Default)
    }

    @Test
    fun `the styles are stored under stable ids, in the order of the row`() {
        assertThat(BackgroundChoices.all.choices.map { it.id to it.value })
            .containsExactly(
                "aurora_curtains" to BackgroundStyle.AuroraCurtains,
                "soft_glow" to BackgroundStyle.SoftGlow,
                "northern_lights" to BackgroundStyle.NorthernLights,
                "light_mist" to BackgroundStyle.LightMist,
                "flowing_light" to BackgroundStyle.FlowingLight,
                "starfield" to BackgroundStyle.Starfield,
                "off" to BackgroundStyle.Off,
            )
            .inOrder()
    }

    @Test
    fun `every style of the design system has exactly one id`() {
        assertThat(BackgroundChoices.all.choices.map { it.value })
            .containsExactlyElementsIn(BackgroundStyle.entries)
        assertThat(BackgroundChoices.all.choices.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `an id is read as its style and shown as chosen`() {
        for (choice in BackgroundChoices.all.choices) {
            assertThat(styleOf(choice.id)).isEqualTo(choice.value)
            assertThat(BackgroundChoices.all.shownId(choice.id)).isEqualTo(choice.id)
        }
    }

    @Test
    fun `an unknown stored value is read as the default style, and shown as chosen`() {
        for (unknown in listOf("", "bogus", "Aurora curtains", "AuroraCurtains", "2", "OFF")) {
            assertThat(styleOf(unknown)).isEqualTo(BackgroundStyle.Default)
            assertThat(BackgroundChoices.all.shownId(unknown))
                .isEqualTo(Settings.BACKGROUND.default)
        }
    }

    @Test
    fun `the Background list offers the ids and the names of the seven styles`() {
        val names = FixedChoices.BACKGROUND.map { context.getString(it.labelRes) }

        assertThat(FixedChoices.BACKGROUND.map { it.key })
            .containsExactlyElementsIn(BackgroundChoices.all.choices.map { it.id })
            .inOrder()
        assertThat(names)
            .containsExactly(
                "Aurora curtains",
                "Soft glow",
                "Northern lights",
                "Light mist",
                "Flowing light",
                "Starfield",
                "Off",
            )
            .inOrder()
    }
}
