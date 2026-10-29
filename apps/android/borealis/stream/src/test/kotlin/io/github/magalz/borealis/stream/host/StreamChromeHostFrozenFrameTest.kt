package io.github.magalz.borealis.stream.host

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.ui.ERROR_PANEL_TAG
import io.github.magalz.borealis.stream.ui.background.OUTSIDE_GAME_BACKGROUND_TAG
import io.github.magalz.borealis.stream.ui.glass.ValidSurfaceView
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The frozen last frame behind the error popup (the owner's decision of 2026-10-29): the activity
 * asks the host for one copy of the stream view when the stream fails, from the thread that reports
 * it, and the popup stands on that copy; the host lets go of the copy when the activity ends.
 */
@RunWith(AndroidJUnit4::class)
class StreamChromeHostFrozenFrameTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val session = aSession()
    private lateinit var stream: ValidSurfaceView
    private lateinit var host: StreamChromeHost

    @Before
    fun `the chrome is over a stream view that has a surface`() {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            stream = ValidSurfaceView(activity)
            activity.addContentView(stream, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            host = StreamChromeHost(activity, session, stream, {}, {})
            activity.addContentView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        assertThat(stream.width).isGreaterThan(0)
    }

    private fun fail() {
        composeRule.runOnUiThread { session.onFailed(aFailure) }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
    }

    @Test
    fun `a failure after the copy was asked for stands on the frame, not on the background`() {
        composeRule.runOnUiThread { host.freezeFrame() }
        composeRule.waitForIdle()

        fail()

        composeRule.onNodeWithTag(ERROR_PANEL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertDoesNotExist()
    }

    @Test
    fun `the copy may be asked for from the thread that reports the end of the stream`() {
        Thread { host.freezeFrame() }.apply { start() }.join()
        composeRule.waitForIdle()

        fail()

        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertDoesNotExist()
        assertThat(host.frozenFrame.requested).isTrue()
    }

    @Test
    fun `the copy is made of the stream view`() {
        val before = stream.locationQueries

        composeRule.runOnUiThread { host.freezeFrame() }

        assertThat(stream.locationQueries).isGreaterThan(before)
    }

    @Test
    fun `a failure with no copy stands on the background`() {
        fail()

        composeRule.onNodeWithText("Try again").assertIsDisplayed()
        composeRule.onNodeWithTag(OUTSIDE_GAME_BACKGROUND_TAG).assertIsDisplayed()
        assertThat(host.frozenFrame.requested).isFalse()
    }

    @Test
    fun `the copy is let go of when the activity is destroyed`() {
        composeRule.runOnUiThread { host.freezeFrame() }
        composeRule.waitForIdle()
        assertThat(host.frozenFrame.frame).isNotNull()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)

        assertThat(host.frozenFrame.frame).isNull()
    }

    @Test
    fun `the copy stays while the activity only stops`() {
        composeRule.runOnUiThread { host.freezeFrame() }
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        assertThat(host.frozenFrame.frame).isNotNull()
    }
}
