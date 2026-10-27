package io.github.magalz.borealis.stream.game.session

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.SensorManager
import android.view.View
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.virtual_controller.VirtualController
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.game.DisplayMode
import io.github.magalz.borealis.stream.game.GameTestBase
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/**
 * How the stream activity follows the display and its window while it streams: the orientation when
 * the display changes, the on-screen controller, the system bars and multi-window mode, and the
 * picture-in-picture requests of the platform.
 */
@Suppress("DEPRECATION") // The system bars are hidden with the system UI flags.
internal class GameWindowSessionTest : GameTestBase() {
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

    private val controller: VirtualController
        get() = checkNotNull(rig.field<VirtualController?>("virtualController"))

    private fun idle(millis: Long) = ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)

    private fun rotate() = rig.game.onConfigurationChanged(Configuration())

    /**
     * Shows the system bars the way the system does, without telling the listener of the window.
     */
    private fun barsShown(flags: Int) {
        ReflectionHelpers.setField(decorView, "mSystemUiVisibility", flags)
    }

    // The orientation follows the display

    @Test
    fun `a display that turns square-ish unlocks every orientation`() {
        rig.create()
        assertThat(rig.game.requestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE)

        rig.displayModes(DisplayMode(0, 1080, 1000, 60f))
        rotate()

        assertThat(rig.game.requestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_FULL_USER)
    }

    @Test
    fun `a display that stops being square-ish is locked to landscape again`() {
        rig.displayModes(DisplayMode(0, 1080, 1000, 60f))
        rig.create()
        assertThat(rig.game.requestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_FULL_USER)

        rig.displayModes(DisplayMode(0, 1920, 1080, 60f))
        rotate()

        assertThat(rig.game.requestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE)
    }

    @Test
    fun `a native portrait stream locks the new square-ish display to portrait`() {
        rig.preference("list_resolution", "1000x1080")
        rig.create()

        rig.displayModes(DisplayMode(0, 1080, 1000, 60f))
        rotate()

        assertThat(rig.game.requestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT)
    }

    @Test
    fun `the on-screen controller locks the new square-ish display to landscape`() {
        rig.preference("checkbox_show_onscreen_controls", true)
        rig.create()

        rig.displayModes(DisplayMode(0, 1080, 1000, 60f))
        rotate()

        assertThat(rig.game.requestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE)
    }

    // The on-screen controller follows the screen

    @Test
    fun `a screen that changes has the on-screen controller laid out again`() {
        rig.preference("checkbox_show_onscreen_controls", true)
        rig.create()
        val before = controller.elements.toList()
        assertThat(before).isNotEmpty()

        rotate()

        val after = controller.elements
        assertThat(after).hasSize(before.size)
        assertThat(after).containsNoneIn(before)
    }

    @Test
    fun `a screen that changes needs no on-screen controller`() {
        rig.create()

        rotate()

        assertThat(rig.field<VirtualController?>("virtualController")).isNull()
    }

    // The system bars

    @Test
    fun `a request to hide the bars again starts the time over`() {
        rig.streaming()
        idle(HIDE_DELAY_MS)
        barsShown(systemBarsShown)
        rig.game.onSystemUiVisibilityChange(systemBarsShown)
        idle(BARS_BACK_DELAY_MS - 500)

        rig.game.onSystemUiVisibilityChange(systemBarsShown)
        idle(BARS_BACK_DELAY_MS - 1)

        // The first request would have hidden the bars by now; the second one waits its own time.
        assertThat(decorView.systemUiVisibility).isEqualTo(systemBarsShown)
        idle(1)
        assertThat(decorView.systemUiVisibility).isEqualTo(immersive)
    }

    @Test
    fun `in multi-window mode the bars are hidden without the layout flags of the full screen`() {
        rig.streaming()
        idle(HIDE_DELAY_MS)
        shadowOf(rig.game).setInMultiWindowMode(true)

        rig.game.onMultiWindowModeChanged(true)
        idle(MULTI_WINDOW_DELAY_MS)

        assertThat(decorView.systemUiVisibility).isEqualTo(systemBarsShown)
    }

    @Test
    fun `a window that leaves multi-window mode is full screen again, and the bars are hidden as before`() {
        rig.streaming()
        idle(HIDE_DELAY_MS)
        shadowOf(rig.game).setInMultiWindowMode(true)
        rig.game.onMultiWindowModeChanged(true)
        idle(MULTI_WINDOW_DELAY_MS)
        assertThat(decorView.systemUiVisibility).isEqualTo(systemBarsShown)

        shadowOf(rig.game).setInMultiWindowMode(false)
        rig.game.onMultiWindowModeChanged(false)
        // The request to hide the bars is the one that the window asked for; it reads the mode
        // when it runs.
        rig.field<Runnable>("hideSystemUi").run()

        assertThat(decorView.systemUiVisibility).isEqualTo(immersive)
        assertThat(rig.game.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN)
            .isNotEqualTo(0)
    }

    // Picture in picture

    @Test
    fun `entering picture in picture tells the system that the game can be interrupted`() {
        rig.streaming()
        val modes = GameModeRecorder().also { it.install(rig.game) }

        rig.game.enterPictureInPictureMode()
        rotate()

        assertThat(modes.calls).containsExactly(GameModes.INTERRUPTIBLE)
    }

    @Test
    fun `leaving picture in picture tells the system that the game runs`() {
        rig.streaming()
        rig.game.enterPictureInPictureMode()
        rotate()
        val modes = GameModeRecorder().also { it.install(rig.game) }

        ReflectionHelpers.setField(shadowOf(rig.game), "isInPictureInPictureMode", false)
        rotate()

        assertThat(modes.calls).containsExactly(GameModes.UNINTERRUPTIBLE)
    }

    @Test
    fun `entering picture in picture rests the sensors of the pads`() {
        rig.streaming()
        val handler = rig.field<ControllerHandler>("controllerHandler")
        val pad = KnownPad(handler, 1)
        pad.listenToGyroscope(rig.application.getSystemService(SensorManager::class.java))
        ShadowRecordingBridge.calls.clear()

        rig.game.enterPictureInPictureMode()
        rotate()

        val rest = ShadowRecordingBridge.named("sendControllerMotionEvent").single()
        assertThat(rest.args)
            .containsExactly(1.toByte(), MoonBridge.LI_MOTION_TYPE_GYRO, 0f, 0f, 0f)
            .inOrder()
        assertThat(pad.listensToGyroscope).isFalse()
    }

    @Test
    fun `leaving picture in picture lets the sensors of the pads come back after a second`() {
        rig.streaming()
        val pad = KnownPad(rig.field("controllerHandler"), 1)
        rig.game.enterPictureInPictureMode()
        rotate()
        assertThat(pad.sensorsEnabled.count).isEqualTo(1)

        ReflectionHelpers.setField(shadowOf(rig.game), "isInPictureInPictureMode", false)
        rotate()
        idle(SENSORS_BACK_DELAY_MS)

        assertThat(pad.awaitSensors()).isTrue()
    }

    @Test
    fun `the platform asking the activity to leave does not make it enter picture in picture`() {
        rig.preference("checkbox_enable_pip", true)
        rig.streaming()

        rig.game.onUserLeaveHint()

        assertThat(rig.game.isInPictureInPictureMode).isFalse()
    }

    @Test
    fun `a request to enter picture in picture is answered yes even when the setting is off`() {
        rig.streaming()

        assertThat(rig.game.onPictureInPictureRequested()).isTrue()
        assertThat(rig.game.isInPictureInPictureMode).isFalse()
    }

    private companion object {
        const val HIDE_DELAY_MS = 1000L
        const val BARS_BACK_DELAY_MS = 2000L
        const val MULTI_WINDOW_DELAY_MS = 50L
        const val SENSORS_BACK_DELAY_MS = 1000L
    }
}
