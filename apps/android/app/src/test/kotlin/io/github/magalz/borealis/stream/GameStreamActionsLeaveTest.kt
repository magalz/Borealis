package io.github.magalz.borealis.stream

import android.app.Activity
import android.content.Intent
import android.view.View
import com.google.common.truth.Truth.assertThat
import com.limelight.Game
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.library.shell.LeaveNotice
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.stream.host.HeldKeys
import io.github.magalz.borealis.stream.input.GamepadChrome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** An activity that counts the requests to finish it, which the system answers twice. */
class CountingActivity : Activity() {
    var finishRequests = 0
        private set

    override fun finish() {
        finishRequests++
        super.finish()
    }
}

private object NoGamepad : GamepadChrome {
    override fun setCaptured(captured: Boolean) = Unit

    override fun setMouseEmulation(enabled: Boolean) = Unit

    override fun setPointerSpeed(factor: Float) = Unit
}

/**
 * Leaving the stream through the actions: a Disconnect keeps its screen for a second, waits for the
 * connection to stop for at most five, leaves the Library its notice and finishes the activity
 * once; a Quit finishes it once too. Time is the test dispatcher's, so nothing here sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GameStreamActionsLeaveTest {
    @get:Rule val log = RecordingLog()

    private val dispatcher = UnconfinedTestDispatcher()
    private val entryPoint = FakeEntryPoint()
    private val stops = mutableListOf<Runnable>()
    private lateinit var activity: CountingActivity
    private lateinit var actions: GameStreamActions

    @Before
    fun setUp() {
        val intent = Intent().putExtra(Game.EXTRA_PC_UUID, "host-1")
        activity = Robolectric.buildActivity(CountingActivity::class.java, intent).setup().get()
        actions =
            GameStreamActions(
                activity,
                RecordingInput(),
                StreamControls(
                    controller = NoGamepad,
                    touchContexts = arrayOfNulls(2),
                    streamView = View(activity),
                    closePanel = {},
                    buildTouch = { null },
                    stopConnection = { stops += it },
                ),
                HeldKeys(),
                { entryPoint },
                CoroutineScope(dispatcher),
            )
        entryPoint.notice.attach()
    }

    private fun advance(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        dispatcher.scheduler.runCurrent()
    }

    private fun stopped() = stops.forEach { it.run() }

    private fun noticeOrNull(): LeaveNotice? {
        var notice: LeaveNotice? = null
        runTest { notice = withTimeoutOrNull(1) { entryPoint.notice.notices.first() } }
        return notice
    }

    @Test
    fun `a disconnect stops the connection and keeps the activity up`() {
        actions.disconnect("Desktop")

        assertThat(stops).hasSize(1)
        assertThat(activity.isFinishing).isFalse()
        assertThat(noticeOrNull()).isNull()
    }

    @Test
    fun `a stop that is quick leaves the screen up for a second`() {
        actions.disconnect("Desktop")
        advance(200)
        stopped()

        advance(799)
        assertThat(activity.isFinishing).isFalse()
        advance(1)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `a stop that is slow ends the screen as soon as it is done`() {
        actions.disconnect("Desktop")
        advance(2_999)
        assertThat(activity.isFinishing).isFalse()

        stopped()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `a stop that never ends does not hold the screen for more than five seconds`() {
        actions.disconnect("Desktop")

        advance(4_999)
        assertThat(activity.isFinishing).isFalse()
        advance(1)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `the Library is left the Disconnected notice with the app name, as the activity finishes`() {
        actions.disconnect("Desktop")
        stopped()
        advance(1_000)

        assertThat(noticeOrNull()).isEqualTo(LeaveNotice.Disconnected("Desktop"))
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `no notice waits for a Library that is not there`() {
        entryPoint.notice.detach()

        actions.disconnect("Desktop")
        stopped()
        advance(1_000)

        entryPoint.notice.attach()
        assertThat(noticeOrNull()).isNull()
    }

    @Test
    fun `a second disconnect while the first is under way does nothing`() {
        actions.disconnect("Desktop")

        actions.disconnect("Desktop")

        assertThat(stops).hasSize(1)
    }

    @Test
    fun `a disconnect finishes the activity once`() {
        actions.disconnect("Desktop")
        stopped()
        advance(1_000)

        assertThat(activity.finishRequests).isEqualTo(1)
    }

    @Test
    fun `a disconnect that finds the activity finishing already does not ask again`() {
        actions.disconnect("Desktop")
        activity.finish()

        stopped()
        advance(1_000)

        assertThat(activity.finishRequests).isEqualTo(1)
    }

    @Test
    fun `a quit that worked finishes the activity once`() {
        actions.quitApp {}

        assertThat(activity.isFinishing).isTrue()
        assertThat(activity.finishRequests).isEqualTo(1)
    }

    @Test
    fun `a quit that worked leaves the Library the quit notice`() {
        actions.quitApp {}

        assertThat(noticeOrNull()).isEqualTo(LeaveNotice.Quit)
    }

    @Test
    fun `the activity finishing is logged at info after a quit`() {
        actions.quitApp {}

        val finished = log.records.single { it.message == "activity finished" }
        assertThat(finished.level).isEqualTo(LogLevel.INFO)
    }

    @Test
    fun `the activity finishing is logged at info after a disconnect`() {
        actions.disconnect("Desktop")
        stopped()
        advance(1_000)

        val finished = log.records.single { it.message == "activity finished" }
        assertThat(finished.level).isEqualTo(LogLevel.INFO)
    }
}
