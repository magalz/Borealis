package io.github.magalz.borealis.stream

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.store.SettingsSnapshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * What the stream draws besides the video is what the settings store when the stream starts (ADR
 * 0061, decisions 1 and 3): the glass of the menu and the style of the background outside the game,
 * which is the Background of the Library. The session reads them once and keeps them.
 */
@RunWith(RobolectricTestRunner::class)
class StreamSessionFactoryVisualsTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun visualsOf(vararg stored: Pair<Setting<*>, Any?>): StreamVisuals =
        StreamSessionFactory.visuals(SettingsSnapshot.fromStored(stored.toMap()))

    private fun newSession(): StreamSession =
        StreamSessionFactory.create(
            activity,
            PreferenceConfiguration(),
            "Desktop",
            "Den PC",
            RecordingStreamActions(),
        ) {
            false
        }

    @Test
    fun `nothing stored gives Blur and the default style`() {
        assertThat(visualsOf())
            .isEqualTo(StreamVisuals(MenuGlass.BLUR, BackgroundStyle.AuroraCurtains))
    }

    @Test
    fun `the stored choices are the visuals of the stream`() {
        val visuals =
            visualsOf(
                Settings.STREAM_MENU_GLASS to "solid",
                Settings.BACKGROUND to "northern_lights",
            )

        assertThat(visuals)
            .isEqualTo(StreamVisuals(MenuGlass.SOLID, BackgroundStyle.NorthernLights))
    }

    @Test
    fun `each choice of the menu glass is read`() {
        assertThat(visualsOf(Settings.STREAM_MENU_GLASS to "blur").menuGlass)
            .isEqualTo(MenuGlass.BLUR)
        assertThat(visualsOf(Settings.STREAM_MENU_GLASS to "solid").menuGlass)
            .isEqualTo(MenuGlass.SOLID)
    }

    @Test
    fun `a stored Background before and after the stream is not read, the style rules`() {
        for (stored in listOf("still", "off", "moving", "paused")) {
            assertThat(visualsOf(Settings.STREAM_BACKGROUND to stored)).isEqualTo(visualsOf())
        }
        assertThat(
                visualsOf(
                    Settings.STREAM_BACKGROUND to "off",
                    Settings.BACKGROUND to "starfield",
                )
            )
            .isEqualTo(visualsOf(Settings.BACKGROUND to "starfield"))
    }

    @Test
    fun `the background style is the one of the Background setting, off included`() {
        assertThat(visualsOf(Settings.BACKGROUND to "starfield").backgroundStyle)
            .isEqualTo(BackgroundStyle.Starfield)
        assertThat(visualsOf(Settings.BACKGROUND to "off").backgroundStyle)
            .isEqualTo(BackgroundStyle.Off)
    }

    @Test
    fun `a stored id that nothing has gives the default`() {
        val visuals =
            visualsOf(
                Settings.STREAM_MENU_GLASS to "tint",
                Settings.BACKGROUND to "bogus",
            )

        assertThat(visuals).isEqualTo(StreamVisuals())
    }

    @Test
    fun `a session starts with what the settings store at that moment`() {
        val settings = SettingsSnapshots.repository(activity)
        settings.setNow(Settings.STREAM_MENU_GLASS, "solid")
        settings.setNow(Settings.BACKGROUND, "light_mist")

        assertThat(newSession().state.value.visuals)
            .isEqualTo(StreamVisuals(MenuGlass.SOLID, BackgroundStyle.LightMist))
    }

    @Test
    fun `a session reads them once, so a change shows in the next stream`() {
        val settings = SettingsSnapshots.repository(activity)
        settings.setNow(Settings.STREAM_MENU_GLASS, "solid")
        val session = newSession()

        settings.setNow(Settings.STREAM_MENU_GLASS, "blur")
        settings.setNow(Settings.BACKGROUND, "starfield")

        assertThat(session.state.value.visuals).isEqualTo(StreamVisuals(MenuGlass.SOLID))
        assertThat(newSession().state.value.visuals)
            .isEqualTo(StreamVisuals(MenuGlass.BLUR, BackgroundStyle.Starfield))
    }
}
