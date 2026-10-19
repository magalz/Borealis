package io.github.magalz.borealis.settings.appearance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
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

    @Test
    fun `the background is a string in DataStore that starts as the Aurora curtains`() {
        assertThat(Settings.BACKGROUND.key).isEqualTo("borealis_background")
        assertThat(Settings.BACKGROUND.default).isEqualTo("aurora_curtains")
        assertThat(Settings.BACKGROUND.store).isEqualTo(SettingStore.DATASTORE)
        assertThat(Settings.all).contains(Settings.BACKGROUND)
        assertThat(BackgroundChoices.styleOf(Settings.BACKGROUND.default))
            .isEqualTo(BackgroundStyle.Default)
    }

    @Test
    fun `the styles are stored under stable ids, in the order of the row`() {
        assertThat(BackgroundChoices.all.map { it.id to it.style })
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
        assertThat(BackgroundChoices.all.map { it.style })
            .containsExactlyElementsIn(BackgroundStyle.entries)
        assertThat(BackgroundChoices.all.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `an id is read as its style and a style is stored as its id`() {
        for (choice in BackgroundChoices.all) {
            assertThat(BackgroundChoices.styleOf(choice.id)).isEqualTo(choice.style)
            assertThat(BackgroundChoices.idOf(choice.style)).isEqualTo(choice.id)
        }
    }

    @Test
    fun `an unknown stored value is read as the default style`() {
        for (unknown in listOf("", "bogus", "Aurora curtains", "AuroraCurtains", "2", "OFF")) {
            assertThat(BackgroundChoices.styleOf(unknown)).isEqualTo(BackgroundStyle.Default)
        }
    }

    @Test
    fun `the Background list offers the ids and the names of the seven styles`() {
        val names = FixedChoices.BACKGROUND.map { context.getString(it.labelRes) }

        assertThat(FixedChoices.BACKGROUND.map { it.key })
            .containsExactlyElementsIn(BackgroundChoices.all.map { it.id })
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
