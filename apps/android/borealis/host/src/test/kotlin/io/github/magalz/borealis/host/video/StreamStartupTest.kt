package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test

/** The plumbing Game hands to [StreamStartup.create], replaced here by fakes. */
class StreamStartupTest {
    private val scheduler = FakeWatchdogScheduler()
    private val terminated = CountDownLatch(1)
    private val terminationThread = AtomicReference<Thread>()

    private val guard =
        StreamStartup.create(
            Runnable {
                terminationThread.set(Thread.currentThread())
                terminated.countDown()
            },
            scheduler,
        )

    @Test
    fun `the first frame stops the watchdog`() {
        guard.onConnectionStarted()

        guard.onFirstFrame()
        scheduler.advanceBy(60_000)

        assertThat(scheduler.pendingCount).isEqualTo(0)
        assertThat(terminated.count).isEqualTo(1L)
    }

    @Test
    fun `a cancelled guard never ends the stream`() {
        guard.onConnectionStarted()

        guard.cancel()
        scheduler.advanceBy(60_000)

        assertThat(scheduler.pendingCount).isEqualTo(0)
        assertThat(terminated.count).isEqualTo(1L)
    }

    @Test
    fun `the timeout ends the stream off the calling thread`() {
        guard.onConnectionStarted()

        scheduler.advanceBy(45_000)

        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(terminationThread.get()).isNotSameInstanceAs(Thread.currentThread())
    }
}
