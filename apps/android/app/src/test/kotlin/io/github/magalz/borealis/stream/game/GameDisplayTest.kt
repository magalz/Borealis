package io.github.magalz.borealis.stream.game

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.ui.StreamView
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/** How `Game.onCreate` fits the display to the stream: its mode, its orientation and its layout. */
internal class GameDisplayTest : GameTestBase() {
    private val current4k = DisplayMode(0, 3840, 2160, 60f)

    @Test
    fun `a display mode that fits the stream better is asked for when its resolution differs`() {
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "120")
        rig.displayModes(current4k, DisplayMode(2, 1920, 1080, 120f))

        rig.create()

        assertThat(rig.game.window.attributes.preferredDisplayModeId).isEqualTo(2)
        assertThat(rig.field<Float>("desiredRefreshRate")).isEqualTo(120f)
    }

    @Test
    fun `a mode with the same resolution is left to the surface frame rate on Android 12 and later`() {
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "120")
        rig.displayModes(DisplayMode(0, 1920, 1080, 60f), DisplayMode(2, 1920, 1080, 120f))

        rig.create()

        assertThat(rig.game.window.attributes.preferredDisplayModeId).isEqualTo(0)
        assertThat(rig.field<Float>("desiredRefreshRate")).isEqualTo(120f)
    }

    @Test
    fun `a stream under 4K at 60 frames or less keeps the resolution of the display`() {
        rig.displayModes(current4k, DisplayMode(2, 1920, 1080, 60f))

        rig.create()

        assertThat(rig.game.window.attributes.preferredDisplayModeId).isEqualTo(0)
        assertThat(rig.field<Float>("desiredRefreshRate")).isEqualTo(60f)
    }

    @Test
    fun `a phone reports the refresh rate that it shows, whatever the picked mode`() {
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "120")
        rig.displayModes(current4k, DisplayMode(2, 1920, 1080, 120f))

        rig.create()

        assertThat(rig.streamConfiguration.clientRefreshRateX100).isEqualTo(6000)
    }

    @Test
    fun `a television reports the refresh rate of the mode that it will switch to`() {
        rig.feature(PackageManager.FEATURE_LEANBACK)
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "120")
        rig.displayModes(current4k, DisplayMode(2, 1920, 1080, 120f))

        rig.create()

        assertThat(rig.streamConfiguration.clientRefreshRateX100).isEqualTo(12000)
    }

    @Test
    fun `the stream view keeps the aspect ratio of the stream`() {
        rig.preference("list_resolution", "1920x1080")

        rig.create()

        val view = rig.game.findViewById<StreamView>(R.id.surfaceView)
        assertThat(ReflectionHelpers.getField<Double>(view, "desiredAspectRatio"))
            .isEqualTo(1920.0 / 1080.0)
    }

    @Test
    fun `the stretch setting fills the screen instead and lets the window lay out under a notch`() {
        rig.preference("checkbox_stretch_video", true)

        rig.create()

        val view = rig.game.findViewById<StreamView>(R.id.surfaceView)
        assertThat(ReflectionHelpers.getField<Double>(view, "desiredAspectRatio")).isEqualTo(0.0)
        assertThat(rig.game.window.attributes.layoutInDisplayCutoutMode)
            .isEqualTo(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS)
    }

    @Test
    fun `a regular display is locked to landscape`() {
        rig.create()

        assertThat(rig.game.requestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE)
    }

    @Test
    fun `a square-ish display allows every orientation`() {
        rig.displayModes(DisplayMode(0, 1080, 1000, 60f))

        rig.create()

        assertThat(rig.game.requestedOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_FULL_USER)
    }

    @Test
    fun `a square-ish display is locked to landscape for the on-screen controller`() {
        rig.preference("checkbox_show_onscreen_controls", true)
        rig.displayModes(DisplayMode(0, 1080, 1000, 60f))

        rig.create()

        assertThat(rig.game.requestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE)
    }
}
