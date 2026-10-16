package io.github.magalz.borealis.stream

import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.touch.AbsoluteTouchContext
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.nvstream.NvConnection
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TouchContextsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val connection = NvConnection(context, null, 0, "test", null, null, null)
    private val view = View(context)

    private fun prefs(trackpad: Boolean) =
        PreferenceConfiguration().apply { touchscreenTrackpad = trackpad }

    @Test
    fun `the trackpad is relative, one context per touch`() {
        val contexts = TouchContexts.build(TouchMode.TRACKPAD, connection, view, prefs(false))

        assertThat(contexts).isNotNull()
        assertThat(contexts!!.map { it::class.java })
            .containsExactly(RelativeTouchContext::class.java, RelativeTouchContext::class.java)
        assertThat(contexts.map { it.actionIndex }).containsExactly(0, 1).inOrder()
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
    fun `a switch off has no contexts, the activity ignores the touchscreen`() {
        assertThat(TouchContexts.build(TouchMode.OFF, connection, view, prefs(true))).isNull()
    }

    @Test
    fun `the settings start with the trackpad or with direct touch`() {
        assertThat(TouchContexts.initialMode(prefs(true))).isEqualTo(TouchMode.TRACKPAD)
        assertThat(TouchContexts.initialMode(prefs(false))).isEqualTo(TouchMode.DIRECT)
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
                false,
                RecordingStreamActions(),
            )

        assertThat(TouchContexts.mode(starting, prefs(true))).isEqualTo(TouchMode.TRACKPAD)
        assertThat(TouchContexts.mode(starting, prefs(false))).isEqualTo(TouchMode.DIRECT)
    }

    @Test
    fun `a device without a touchscreen takes the mode of the settings`() {
        val noTouch =
            StreamSession(
                "App",
                "PC",
                StreamDefaults(OverlayMode.OFF, null, false, false),
                false,
                RecordingStreamActions(),
            )
        noTouch.onConnectionStarted(SessionInfo(1, 1, 1, "H.264", false, null, 1))
        noTouch.onFirstFrame()

        assertThat(TouchContexts.mode(noTouch, prefs(true))).isEqualTo(TouchMode.TRACKPAD)
    }
}
