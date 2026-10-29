package io.github.magalz.borealis.stream.host

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import io.github.magalz.borealis.stream.ui.glass.ValidSurfaceView
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The host gives the chrome the stream view to copy (ADR 0061, decision 1), and the copies follow
 * the session: they run while the menu is open, and stop in picture-in-picture and when the phase
 * leaves Streaming. The stream view counts how often the glass asks where it is, which it does on
 * each tick of the copies.
 */
@RunWith(AndroidJUnit4::class)
class StreamChromeHostGlassTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val session = aSession()
    private lateinit var stream: ValidSurfaceView

    @Before
    fun `the chrome is over a stream view that has a surface`() {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            stream = ValidSurfaceView(activity)
            activity.addContentView(stream, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            val host = StreamChromeHost(activity, session, stream, {}, {})
            activity.addContentView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        assertThat(stream.width).isGreaterThan(0)
    }

    private fun openMenu() {
        composeRule.runOnUiThread {
            session.onConnectionStarted(aSessionInfo)
            session.onFirstFrame()
            session.openMenu()
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
    }

    private fun queriesDuring(millis: Long): Int {
        val before = stream.locationQueries
        composeRule.mainClock.advanceTimeBy(millis)
        return stream.locationQueries - before
    }

    @Test
    fun `the glass copies the stream view while the menu is open`() {
        openMenu()

        assertThat(queriesDuring(500)).isAtLeast(10)
    }

    @Test
    fun `nothing is copied before the stream has a menu to show`() {
        assertThat(queriesDuring(500)).isEqualTo(0)
    }

    @Test
    fun `picture-in-picture stops the copies, and leaving it starts them again`() {
        openMenu()
        assertThat(queriesDuring(200)).isGreaterThan(0)

        composeRule.runOnUiThread { session.onPip(true) }
        composeRule.mainClock.advanceTimeBy(100)
        assertThat(queriesDuring(500)).isEqualTo(0)

        composeRule.runOnUiThread { session.onPip(false) }
        composeRule.mainClock.advanceTimeBy(100)
        assertThat(queriesDuring(500)).isGreaterThan(0)
    }

    @Test
    fun `leaving the Streaming phase stops the copies`() {
        openMenu()
        assertThat(queriesDuring(200)).isGreaterThan(0)

        composeRule.runOnUiThread { session.disconnect() }
        composeRule.mainClock.advanceTimeBy(100)

        assertThat(queriesDuring(500)).isEqualTo(0)
    }
}
