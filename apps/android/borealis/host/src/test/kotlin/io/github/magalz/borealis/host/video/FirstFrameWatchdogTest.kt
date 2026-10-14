package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.video.FirstFrameWatchdog.State
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

class FirstFrameWatchdogTest {
    private val scheduler = FakeWatchdogScheduler()
    private val timeouts = AtomicInteger()
    private val watchdog = FirstFrameWatchdog(scheduler, onTimeout = { timeouts.incrementAndGet() })

    @Test
    fun `the default timeout is 45 seconds`() {
        assertThat(FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS).isEqualTo(45_000L)
        watchdog.arm()

        scheduler.advanceBy(44_999)
        assertThat(timeouts.get()).isEqualTo(0)
        scheduler.advanceBy(1)
        assertThat(timeouts.get()).isEqualTo(1)
    }

    @Test
    fun `a new watchdog is idle and schedules nothing`() {
        assertThat(watchdog.state).isEqualTo(State.IDLE)
        assertThat(scheduler.scheduledCount).isEqualTo(0)
    }

    @Test
    fun `arm moves to armed and schedules one timeout`() {
        watchdog.arm()

        assertThat(watchdog.state).isEqualTo(State.ARMED)
        assertThat(scheduler.pendingCount).isEqualTo(1)
    }

    @Test
    fun `a custom timeout is honoured`() {
        val short =
            FirstFrameWatchdog(scheduler, timeoutMillis = 1_000, onTimeout = { timeouts.incrementAndGet() })
        short.arm()

        scheduler.advanceBy(1_000)

        assertThat(short.state).isEqualTo(State.FIRED)
        assertThat(timeouts.get()).isEqualTo(1)
    }

    @Test
    fun `the timeout fires once after the deadline`() {
        watchdog.arm()

        scheduler.advanceBy(45_000)
        scheduler.advanceBy(45_000)

        assertThat(watchdog.state).isEqualTo(State.FIRED)
        assertThat(timeouts.get()).isEqualTo(1)
    }

    @Test
    fun `a frame while armed satisfies the watchdog and cancels the timeout`() {
        watchdog.arm()

        watchdog.onFirstFrame()
        scheduler.advanceBy(60_000)

        assertThat(watchdog.state).isEqualTo(State.SATISFIED)
        assertThat(scheduler.pendingCount).isEqualTo(0)
        assertThat(timeouts.get()).isEqualTo(0)
    }

    @Test
    fun `a frame before arm leads to satisfied and a later arm is a no-op`() {
        watchdog.onFirstFrame()
        assertThat(watchdog.state).isEqualTo(State.SATISFIED)

        watchdog.arm()
        scheduler.advanceBy(60_000)

        assertThat(watchdog.state).isEqualTo(State.SATISFIED)
        assertThat(scheduler.scheduledCount).isEqualTo(0)
        assertThat(timeouts.get()).isEqualTo(0)
    }

    @Test
    fun `a frame after the timeout fired changes nothing`() {
        watchdog.arm()
        scheduler.advanceBy(45_000)

        watchdog.onFirstFrame()

        assertThat(watchdog.state).isEqualTo(State.FIRED)
        assertThat(timeouts.get()).isEqualTo(1)
    }

    @Test
    fun `a repeated frame is a no-op`() {
        watchdog.arm()
        watchdog.onFirstFrame()

        watchdog.onFirstFrame()

        assertThat(watchdog.state).isEqualTo(State.SATISFIED)
    }

    @Test
    fun `cancel while armed cancels the timeout`() {
        watchdog.arm()

        watchdog.cancel()
        scheduler.advanceBy(60_000)

        assertThat(watchdog.state).isEqualTo(State.CANCELLED)
        assertThat(scheduler.pendingCount).isEqualTo(0)
        assertThat(timeouts.get()).isEqualTo(0)
    }

    @Test
    fun `cancel before arm is final and a later arm is a no-op`() {
        watchdog.cancel()
        assertThat(watchdog.state).isEqualTo(State.CANCELLED)

        watchdog.arm()
        scheduler.advanceBy(60_000)

        assertThat(watchdog.state).isEqualTo(State.CANCELLED)
        assertThat(scheduler.scheduledCount).isEqualTo(0)
        assertThat(timeouts.get()).isEqualTo(0)
    }

    @Test
    fun `cancel after a terminal state is a no-op`() {
        val satisfied = FirstFrameWatchdog(scheduler, onTimeout = { timeouts.incrementAndGet() })
        satisfied.arm()
        satisfied.onFirstFrame()
        satisfied.cancel()
        assertThat(satisfied.state).isEqualTo(State.SATISFIED)

        watchdog.arm()
        scheduler.advanceBy(45_000)
        watchdog.cancel()
        assertThat(watchdog.state).isEqualTo(State.FIRED)

        watchdog.cancel()
        assertThat(watchdog.state).isEqualTo(State.FIRED)
    }

    @Test
    fun `a double arm schedules a single timeout`() {
        watchdog.arm()
        watchdog.arm()
        scheduler.advanceBy(45_000)

        assertThat(scheduler.scheduledCount).isEqualTo(1)
        assertThat(timeouts.get()).isEqualTo(1)
    }

    @Test
    fun `arm after the timeout fired does not rearm`() {
        watchdog.arm()
        scheduler.advanceBy(45_000)

        watchdog.arm()
        scheduler.advanceBy(45_000)

        assertThat(scheduler.scheduledCount).isEqualTo(1)
        assertThat(timeouts.get()).isEqualTo(1)
    }

    @Test
    fun `racing frame cancel and timeout reach exactly one terminal state`() {
        val pool = Executors.newFixedThreadPool(3)
        try {
            repeat(200) {
                val sched = FakeWatchdogScheduler()
                val fired = AtomicInteger()
                val dog = FirstFrameWatchdog(sched, onTimeout = { fired.incrementAndGet() })
                dog.arm()
                val start = CountDownLatch(1)
                val done = CountDownLatch(3)
                val events: List<() -> Unit> =
                    listOf({ dog.onFirstFrame() }, { dog.cancel() }, { sched.advanceBy(45_000) })
                for (event in events) {
                    pool.execute {
                        start.await()
                        event()
                        done.countDown()
                    }
                }
                start.countDown()
                assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()

                assertThat(dog.state).isAnyOf(State.SATISFIED, State.CANCELLED, State.FIRED)
                assertThat(fired.get()).isEqualTo(if (dog.state == State.FIRED) 1 else 0)
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
