package io.github.magalz.borealis.stream

import android.app.Application
import android.preference.PreferenceManager
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.touch.AbsoluteTouchContext
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.T0
import com.limelight.binding.input.touch.TouchRig
import com.limelight.binding.input.touch.TwoFingerTap
import com.limelight.binding.input.touch.liftFirstOfTwoFingers
import com.limelight.binding.input.touch.setPointerCount
import com.limelight.binding.input.touch.start
import com.limelight.nvstream.NvConnection
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.settings.catalog.Settings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class TouchContextsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val connection = NvConnection(context, null, 0, "test", null, null, null)
    private val view = View(context)

    private fun prefs(trackpad: Boolean, nativeTouch: Boolean = false) =
        PreferenceConfiguration().apply {
            touchscreenTrackpad = trackpad
            touchscreenNative = nativeTouch
        }

    @Test
    fun `the trackpad is relative, one context per touch`() {
        val contexts = TouchContexts.build(TouchMode.TRACKPAD, connection, view, prefs(false))

        assertThat(contexts).isNotNull()
        assertThat(contexts!!.map { it::class.java })
            .containsExactly(RelativeTouchContext::class.java, RelativeTouchContext::class.java)
        assertThat(contexts.map { it.actionIndex }).containsExactly(0, 1).inOrder()
    }

    @Test
    @Config(shadows = [ShadowRecordingBridge::class])
    fun `the trackpad contexts share the two-finger tap, so a late second finger that lifts last clicks`() {
        val rig = TouchRig()
        val (first, second) =
            TouchContexts.build(TouchMode.TRACKPAD, rig.connection, rig.view, rig.prefs)!!.map {
                it as RelativeTouchContext
            }
        first.start(100, 100)
        setPointerCount(2, first, second)
        assertThat(second.touchDownEvent(300, 100, T0 + 150, true)).isTrue()

        liftFirstOfTwoFingers(first, second, T0 + 270)
        first.touchUpEvent(300, 100, T0 + 280)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `the trackpad contexts hold one two-finger tap, so the movement of either finger reaches both`() {
        val contexts = TouchContexts.build(TouchMode.TRACKPAD, connection, view, prefs(false))!!

        // Seen through a gesture, an unshared tap never clicks at all (the test above); the
        // movement that it holds for both fingers only shows in which instance each context has.
        val taps = contexts.map { ReflectionHelpers.getField<TwoFingerTap>(it, "twoFingerTap") }

        assertThat(taps).hasSize(2)
        assertThat(taps[1]).isSameInstanceAs(taps[0])
    }

    @Test
    fun `direct touch is absolute, one context per touch`() {
        val contexts = TouchContexts.build(TouchMode.DIRECT, connection, view, prefs(true))

        assertThat(contexts).isNotNull()
        assertThat(contexts!!.map { it::class.java })
            .containsExactly(AbsoluteTouchContext::class.java, AbsoluteTouchContext::class.java)
        assertThat(contexts.map { it.actionIndex }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `native touch has the direct contexts, which a host without touch support falls back to`() {
        val contexts = TouchContexts.build(TouchMode.NATIVE, connection, view, prefs(true))

        assertThat(contexts).isNotNull()
        assertThat(contexts!!.map { it::class.java })
            .containsExactly(AbsoluteTouchContext::class.java, AbsoluteTouchContext::class.java)
        assertThat(contexts.map { it.actionIndex }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `a switch off has no contexts, the activity ignores the touchscreen`() {
        assertThat(TouchContexts.build(TouchMode.OFF, connection, view, prefs(true))).isNull()
    }

    @Test
    fun `without Native the settings start with the trackpad or with direct touch`() {
        assertThat(TouchContexts.initialMode(prefs(true))).isEqualTo(TouchMode.TRACKPAD)
        assertThat(TouchContexts.initialMode(prefs(false))).isEqualTo(TouchMode.DIRECT)
    }

    @Test
    fun `the settings start with Native when it is on, whichever way the trackpad switch is`() {
        assertThat(TouchContexts.initialMode(prefs(true, nativeTouch = true)))
            .isEqualTo(TouchMode.NATIVE)
        assertThat(TouchContexts.initialMode(prefs(false, nativeTouch = true)))
            .isEqualTo(TouchMode.NATIVE)
    }

    @Test
    @Config(shadows = [ShadowRecordingBridge::class])
    @Suppress("DEPRECATION")
    fun `an install that only stores the old touch key starts as before, and the new key wins`() {
        val stored = PreferenceManager.getDefaultSharedPreferences(context)
        stored.edit().clear().commit()
        fun startMode() =
            TouchContexts.initialMode(PreferenceConfiguration.readPreferences(context))

        assertThat(startMode()).isEqualTo(TouchMode.TRACKPAD)

        stored.edit().putBoolean(Settings.TOUCHSCREEN_TRACKPAD.key, false).commit()
        assertThat(startMode()).isEqualTo(TouchMode.DIRECT)

        stored.edit().putBoolean(Settings.TOUCHSCREEN_TRACKPAD.key, true).commit()
        assertThat(startMode()).isEqualTo(TouchMode.TRACKPAD)

        stored.edit().putBoolean(Settings.TOUCHSCREEN_NATIVE.key, true).commit()
        assertThat(startMode()).isEqualTo(TouchMode.NATIVE)
    }

    @Test
    fun `the mode in force is the one of the streaming session`() {
        val session = streamingSession()

        assertThat(TouchContexts.mode(session, prefs(false))).isEqualTo(TouchMode.TRACKPAD)

        session.setTouchMode(TouchMode.OFF)
        assertThat(TouchContexts.mode(session, prefs(false))).isEqualTo(TouchMode.OFF)

        session.setTouchMode(TouchMode.DIRECT)
        assertThat(TouchContexts.mode(session, prefs(true))).isEqualTo(TouchMode.DIRECT)
    }

    @Test
    fun `before the stream runs the mode is the one of the settings`() {
        val starting =
            StreamSession(
                "App",
                "PC",
                StreamDefaults(OverlayMode.OFF, null, null, false),
                { false },
                RecordingStreamActions(),
            )

        assertThat(TouchContexts.mode(starting, prefs(true))).isEqualTo(TouchMode.TRACKPAD)
        assertThat(TouchContexts.mode(starting, prefs(false))).isEqualTo(TouchMode.DIRECT)
        assertThat(TouchContexts.mode(starting, prefs(true, nativeTouch = true)))
            .isEqualTo(TouchMode.NATIVE)
    }

    @Test
    fun `a device without a touchscreen takes the mode of the settings`() {
        val noTouch =
            StreamSession(
                "App",
                "PC",
                StreamDefaults(OverlayMode.OFF, null, false, false),
                { false },
                RecordingStreamActions(),
            )
        noTouch.onConnectionStarted(SessionInfo(1, 1, 1, "H.264", false, null, 1))
        noTouch.onFirstFrame()

        assertThat(TouchContexts.mode(noTouch, prefs(true))).isEqualTo(TouchMode.TRACKPAD)
    }
}
