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
 * the position about 30 times a second whatever the refresh rate, and sleeps between two frames. On
 * a TV with glass the loop is asked for 24 a second, and keeps that on every one of the three
 * displays, though a 24th of a second is not a whole number of their frames. The display is
 * simulated in the virtual time of a test.
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

    // The TV with glass: 24 a second, though a frame of 24 Hz falls between the frames of the
    // display.

    @Test
    fun `at 60 Hz the position moves about 24 times a second when 24 are asked for`() = runTest {
        assertPace(60, framesPerSecond = TV_FRAMES_PER_SECOND)
    }

    @Test
    fun `at 90 Hz the position moves about 24 times a second when 24 are asked for`() = runTest {
        assertPace(90, framesPerSecond = TV_FRAMES_PER_SECOND)
    }

    @Test
    fun `at 120 Hz the position moves about 24 times a second when 24 are asked for`() = runTest {
        assertPace(120, framesPerSecond = TV_FRAMES_PER_SECOND)
    }

    @Test
    fun `at 60 Hz with wake-ups 3 ms late 24 are still about 24 a second`() = runTest {
        assertPace(60, wakeLateMillis = LATE_WAKE_MILLIS, framesPerSecond = TV_FRAMES_PER_SECOND)
    }

    @Test
    fun `at 90 Hz with wake-ups 3 ms late 24 are still about 24 a second`() = runTest {
        assertPace(90, wakeLateMillis = LATE_WAKE_MILLIS, framesPerSecond = TV_FRAMES_PER_SECOND)
    }

    @Test
    fun `a pace of 24 never moves the position faster than 30 does`() = runTest {
        val slow = runFirstSecond(hertz = 120, framesPerSecond = TV_FRAMES_PER_SECOND)
        val fast = runFirstSecond(hertz = 120)

        assertThat(slow.updates).isLessThan(fast.updates)
    }

    @Test
    fun `the loop catches up on frames that did not come by waiting, not by a burst`() = runTest {
        // The display stops for two seconds, as it does when the app is not on screen, and comes
        // back: the loop does not draw a frame at each vsync to make up for the time lost.
        val display = Display(testScheduler, hertz = 60, wakeLateMillis = 0L, stallAt = 500L)
        var updates = 0
        val loop =
            launch(display) {
                runCycleLoop(
                    framesPerSecond = TV_FRAMES_PER_SECOND,
                    nanoTime = { testScheduler.currentTime * NANOS_PER_MILLI },
                ) {
                    updates++
                }
            }
        advanceTimeBy(500L + STALL_MILLIS + ONE_SECOND_MILLIS)
        runCurrent()
        loop.cancel()

        // Half a second before the stall and a second after it: 36 or so, never one per vsync.
        assertThat(updates).isIn(Range.closed(33, 39))
    }

    private fun TestScope.assertPace(
        hertz: Int,
        wakeLateMillis: Long = 0L,
        framesPerSecond: Int = FRAMES_PER_SECOND,
    ) {
        val shown = runFirstSecond(hertz, wakeLateMillis, framesPerSecond)

        assertThat(shown.updates)
            .isIn(Range.closed(framesPerSecond - TOLERANCE, framesPerSecond + TOLERANCE))
        // The thread sleeps: it asks for a frame to draw one, not to skip it.
        assertThat(shown.frameRequests).isEqualTo(shown.updates)
    }

    /**
     * Runs the loop for one second on a display of [hertz], and says what it did. The thread of the
     * loop is [wakeLateMillis] late in asking for each frame, as a wake-up that is late is.
     */
    private fun TestScope.runFirstSecond(
        hertz: Int,
        wakeLateMillis: Long = 0L,
        framesPerSecond: Int = FRAMES_PER_SECOND,
    ): Shown {
        val display = Display(testScheduler, hertz, wakeLateMillis)
        var updates = 0
        var lastCycle = 0f
        val loop =
            launch(display) {
                runCycleLoop(
                    framesPerSecond = framesPerSecond,
                    nanoTime = { testScheduler.currentTime * NANOS_PER_MILLI },
                ) {
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
     * A display with a [stallAt] stops for [STALL_MILLIS] when a frame is asked for at that time or
     * after it, once, as one does when the app is off the screen.
     */
    private class Display(
        private val scheduler: TestCoroutineScheduler,
        private val hertz: Int,
        private val wakeLateMillis: Long,
        private val stallAt: Long = Long.MAX_VALUE,
    ) : MonotonicFrameClock {
        var frameRequests = 0
            private set

        private var stalled = false

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frameRequests++
            delay(wakeLateMillis)
            if (!stalled && scheduler.currentTime >= stallAt) {
                stalled = true
                delay(STALL_MILLIS)
            }
            val now = scheduler.currentTime
            val vsync = (now * hertz / ONE_SECOND_MILLIS + 1) * ONE_SECOND_MILLIS.toDouble() / hertz
            delay(ceil(vsync).toLong() - now)
            val result = onFrame(scheduler.currentTime * NANOS_PER_MILLI)
            delay(FRAME_WORK_MILLIS)
            return result
        }
    }

    private companion object {
        const val FRAMES_PER_SECOND = 30
        const val TV_FRAMES_PER_SECOND = 24
        const val TOLERANCE = 2
        const val STALL_MILLIS = 2_000L
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
