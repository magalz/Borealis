package io.github.magalz.borealis.stream.game

import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.binding.input.virtual_controller.VirtualController
import com.limelight.nvstream.jni.MoonBridge
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/** How the stream activity follows its window: system bars, focus, multi-window and PiP mode. */
internal class GameWindowTest : GameTestBase() {
    private val immersive =
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

    private val systemBarsShown = View.SYSTEM_UI_FLAG_LAYOUT_STABLE

    private val decorView: View
        get() = rig.game.window.decorView

    private fun idle(millis: Long) = ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)

    /** Shows the system bars the way the system does, without telling the listener of the window. */
    private fun barsShown(flags: Int) {
        ReflectionHelpers.setField(decorView, "mSystemUiVisibility", flags)
    }

    @Test
    fun `the window starts under the system bars and in the layout of the whole screen`() {
        rig.create()

        val flags = decorView.systemUiVisibility
        assertThat(flags and View.SYSTEM_UI_FLAG_LAYOUT_STABLE).isNotEqualTo(0)
        assertThat(flags and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION).isNotEqualTo(0)
        assertThat(flags and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN).isNotEqualTo(0)
        assertThat(flags and View.SYSTEM_UI_FLAG_FULLSCREEN).isEqualTo(0)
    }

    @Test
    fun `the system bars go away a second after the stream connected`() {
        rig.streaming()
        assertThat(decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN).isEqualTo(0)

        idle(HIDE_DELAY_MS)

        assertThat(decorView.systemUiVisibility).isEqualTo(immersive)
    }

    @Test
    fun `system bars that come back during the stream are hidden again after two seconds`() {
        rig.streaming()
        idle(HIDE_DELAY_MS)
        barsShown(systemBarsShown)

        rig.game.onSystemUiVisibilityChange(systemBarsShown)
        idle(BARS_BACK_DELAY_MS - 1)
        assertThat(decorView.systemUiVisibility).isEqualTo(systemBarsShown)
        idle(1)

        assertThat(decorView.systemUiVisibility).isEqualTo(immersive)
    }

    @Test
    fun `a navigation bar that comes back alone is hidden again too`() {
        rig.streaming()
        idle(HIDE_DELAY_MS)
        val onlyFullScreen = immersive and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
        barsShown(onlyFullScreen)

        rig.game.onSystemUiVisibilityChange(onlyFullScreen)
        idle(BARS_BACK_DELAY_MS)

        assertThat(decorView.systemUiVisibility).isEqualTo(immersive)
    }

    @Test
    fun `hidden system bars are left alone`() {
        rig.streaming()
        idle(HIDE_DELAY_MS)
        barsShown(systemBarsShown)

        rig.game.onSystemUiVisibilityChange(immersive)
        idle(BARS_BACK_DELAY_MS)

        assertThat(decorView.systemUiVisibility).isEqualTo(systemBarsShown)
    }

    @Test
    fun `before the stream connected the system bars are left alone`() {
        rig.create()
        barsShown(systemBarsShown)

        rig.game.onSystemUiVisibilityChange(systemBarsShown)
        idle(BARS_BACK_DELAY_MS)

        assertThat(decorView.systemUiVisibility).isEqualTo(systemBarsShown)
    }

    @Test
    fun `focus that changes forgets the modifier keys that were held`() {
        rig.create()
        ReflectionHelpers.setField(rig.game, "modifierFlags", MODIFIERS_HELD)

        rig.game.onWindowFocusChanged(false)

        assertThat(rig.field<Int>("modifierFlags")).isEqualTo(0)
    }

    @Test
    fun `a window in multi-window mode is no longer full screen and hides the bars again`() {
        rig.streaming()
        idle(HIDE_DELAY_MS)
        barsShown(systemBarsShown)

        rig.game.onMultiWindowModeChanged(true)
        assertThat(rig.game.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN)
            .isEqualTo(0)
        idle(MULTI_WINDOW_DELAY_MS)

        assertThat(decorView.systemUiVisibility).isEqualTo(immersive)
    }

    @Test
    fun `a window that leaves multi-window mode is full screen again`() {
        rig.create()
        rig.game.onMultiWindowModeChanged(true)

        rig.game.onMultiWindowModeChanged(false)

        assertThat(rig.game.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN)
            .isNotEqualTo(0)
    }

    @Test
    fun `in picture in picture the overlays are hidden`() {
        rig.preference("checkbox_show_onscreen_controls", true)
        rig.streaming()
        val configure =
            ReflectionHelpers.getField<View>(
                rig.field<VirtualController>("virtualController"),
                "buttonConfigure",
            )
        val notifications = rig.game.findViewById<View>(R.id.notificationOverlay)
        notifications.visibility = View.VISIBLE

        rig.game.enterPictureInPictureMode()
        rig.game.onConfigurationChanged(Configuration())

        assertThat(rig.field<Boolean>("isHidingOverlays")).isTrue()
        assertThat(configure.visibility).isEqualTo(View.INVISIBLE)
        assertThat(notifications.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `out of picture in picture the overlays come back as they were asked for`() {
        rig.preference("checkbox_show_onscreen_controls", true)
        rig.streaming()
        val configure =
            ReflectionHelpers.getField<View>(
                rig.field<VirtualController>("virtualController"),
                "buttonConfigure",
            )
        rig.game.connectionStatusUpdate(MoonBridge.CONN_STATUS_POOR)
        rig.game.enterPictureInPictureMode()
        rig.game.onConfigurationChanged(Configuration())
        assertThat(configure.visibility).isEqualTo(View.INVISIBLE)

        // The activity left picture in picture: the platform tells it with a configuration change.
        ReflectionHelpers.setField(shadowOf(rig.game), "isInPictureInPictureMode", false)
        rig.game.onConfigurationChanged(Configuration())

        assertThat(rig.field<Boolean>("isHidingOverlays")).isFalse()
        assertThat(configure.visibility).isEqualTo(View.VISIBLE)
        assertThat(rig.game.findViewById<View>(R.id.notificationOverlay).visibility)
            .isEqualTo(View.VISIBLE)
    }

    private companion object {
        const val HIDE_DELAY_MS = 1000L
        const val BARS_BACK_DELAY_MS = 2000L
        const val MULTI_WINDOW_DELAY_MS = 50L
        const val MODIFIERS_HELD = 7
    }
}
