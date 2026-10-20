package io.github.magalz.borealis.designsystem.background

import androidx.compose.runtime.MonotonicFrameClock
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlin.math.ceil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The pace of the frame loop of the Compose background on displays of 60, 90 and 120 Hz: it moves
 * the position about 30 times a second whatever the refresh rate, and sleeps between two frames.
 * The display is simulated in the virtual time of a test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundPaceTest {
    @Test fun `at 60 Hz the position moves about 30 times a second`() = runTest { assertPace(60) }

    @Test fun `at 90 Hz the position moves about 30 times a second`() = runTest { assertPace(90) }

    @Test fun `at 120 Hz the position moves about 30 times a second`() = runTest { assertPace(120) }

    // The sleep ends a few milliseconds before the vsync, so a wake-up that is late still makes it.

    @Test
    fun `at 60 Hz with wake-ups 3 ms late the position still moves about 30 times a second`() =
        runTest {
            assertPace(60, wakeLateMillis = LATE_WAKE_MILLIS)
        }

    @Test
    fun `at 90 Hz with wake-ups 3 ms late the position still moves about 30 times a second`() =
        runTest {
            assertPace(90, wakeLateMillis = LATE_WAKE_MILLIS)
        }

    @Test
    fun `at 120 Hz with wake-ups 3 ms late the position still moves about 30 times a second`() =
        runTest {
            assertPace(120, wakeLateMillis = LATE_WAKE_MILLIS)
        }

    @Test
    fun `the position is the time of the frame in the 20 second loop`() = runTest {
        val shown = runFirstSecond(hertz = 60)

        // The last frame comes a frame or two before the second ends: 1 s is 0.05 of 20 s.
        assertThat(shown.lastCycle).isWithin(0.002f).of(0.049f)
    }

    private fun TestScope.assertPace(hertz: Int, wakeLateMillis: Long = 0L) {
        val shown = runFirstSecond(hertz, wakeLateMillis)

        assertThat(shown.updates).isIn(Range.closed(28, 32))
        // The thread sleeps: it asks for a frame to draw one, not to skip it.
        assertThat(shown.frameRequests).isEqualTo(shown.updates)
    }

    /**
     * Runs the loop for one second on a display of [hertz], and says what it did. The thread of the
     * loop is [wakeLateMillis] late in asking for each frame, as a wake-up that is late is.
     */
    private fun TestScope.runFirstSecond(hertz: Int, wakeLateMillis: Long = 0L): Shown {
        val display = Display(testScheduler, hertz, wakeLateMillis)
        var updates = 0
        var lastCycle = 0f
        val loop =
            launch(display) {
                runCycleLoop(nanoTime = { testScheduler.currentTime * NANOS_PER_MILLI }) {
                    updates++
                    lastCycle = it
                }
            }
        advanceTimeBy(ONE_SECOND_MILLIS)
        runCurrent()
        loop.cancel()
        return Shown(updates, display.frameRequests, lastCycle)
    }

    private class Shown(val updates: Int, val frameRequests: Int, val lastCycle: Float)

    /**
     * A display that refreshes [hertz] times a second. A frame that is asked for comes at the next
     * vsync, with the time of the test as its time, and the coroutine that asked goes on when the
     * work of the frame is done. The thread asks for the frame [wakeLateMillis] after it is woken.
     */
    private class Display(
        private val scheduler: TestCoroutineScheduler,
        private val hertz: Int,
        private val wakeLateMillis: Long,
    ) : MonotonicFrameClock {
        var frameRequests = 0
            private set

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frameRequests++
            delay(wakeLateMillis)
            val now = scheduler.currentTime
            val vsync = (now * hertz / ONE_SECOND_MILLIS + 1) * ONE_SECOND_MILLIS.toDouble() / hertz
            delay(ceil(vsync).toLong() - now)
            val result = onFrame(scheduler.currentTime * NANOS_PER_MILLI)
            delay(FRAME_WORK_MILLIS)
            return result
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val ONE_SECOND_MILLIS = 1_000L

        /**
         * What a frame costs, in milliseconds: its draw runs on the thread before the coroutine
         * that asked for the frame goes on. It is what a frame of the curtains took on the Google
         * TV Streamer.
         */
        const val FRAME_WORK_MILLIS = 11L

        /** How late a wake-up is: less than the 4 ms that the sleep ends before the vsync. */
        const val LATE_WAKE_MILLIS = 3L
    }
}
