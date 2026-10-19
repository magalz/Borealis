package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Test

class StreamStartupGuardTest {
    private class RecordingTermination : Runnable {
        val terminations = CountDownLatch(1)
        val terminationThread = AtomicReference<Thread>()
        @Volatile var terminationCount = 0

        override fun run() {
            terminationCount++
            terminationThread.set(Thread.currentThread())
            terminations.countDown()
        }
    }

    private val termination = RecordingTermination()
    private val scheduler = FakeWatchdogScheduler()
    private val guard = StreamStartupGuard(termination, scheduler)

    @Test
    fun `starting the connection twice arms the watchdog once`() {
        guard.onConnectionStarted()
        guard.onConnectionStarted()

        assertThat(scheduler.scheduledCount).isEqualTo(1)
    }

    @Test
    fun `a first frame stops the watchdog before it fires`() {
        guard.onConnectionStarted()

        guard.onFirstFrame()

        assertThat(scheduler.pendingCount).isEqualTo(0)
        scheduler.advanceBy(60_000)
        assertThat(termination.terminationCount).isEqualTo(0)
    }

    @Test
    fun `a first frame reported from the render thread stops the watchdog`() {
        guard.onConnectionStarted()

        thread(name = "render") { guard.onFirstFrame() }.join()
        scheduler.advanceBy(60_000)

        assertThat(termination.terminationCount).isEqualTo(0)
    }

    @Test
    fun `a frame that arrives before the connection started keeps the watchdog away`() {
        guard.onFirstFrame()

        guard.onConnectionStarted()
        scheduler.advanceBy(60_000)

        assertThat(scheduler.scheduledCount).isEqualTo(0)
        assertThat(termination.terminationCount).isEqualTo(0)
    }

    @Test
    fun `the timeout ends the stream off the calling thread`() {
        guard.onConnectionStarted()

        scheduler.advanceBy(45_000)

        assertThat(termination.terminations.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(termination.terminationThread.get()).isNotSameInstanceAs(Thread.currentThread())
    }

    @Test
    fun `the stream is ended only once however often the timeout and events repeat`() {
        guard.onConnectionStarted()
        scheduler.advanceBy(45_000)
        assertThat(termination.terminations.await(5, TimeUnit.SECONDS)).isTrue()

        guard.onConnectionStarted()
        guard.onFirstFrame()
        guard.cancel()
        scheduler.advanceBy(60_000)

        assertThat(termination.terminationCount).isEqualTo(1)
    }

    @Test
    fun `cancel silences the watchdog`() {
        guard.onConnectionStarted()

        guard.cancel()
        scheduler.advanceBy(60_000)

        assertThat(scheduler.pendingCount).isEqualTo(0)
        assertThat(termination.terminationCount).isEqualTo(0)
    }

    @Test
    fun `a cancelled guard does not arm the watchdog when the connection starts later`() {
        guard.cancel()

        guard.onConnectionStarted()
        scheduler.advanceBy(60_000)

        assertThat(scheduler.scheduledCount).isEqualTo(0)
        assertThat(termination.terminationCount).isEqualTo(0)
    }

    @Test
    fun `a custom timeout reaches the watchdog`() {
        val quick = StreamStartupGuard(termination, scheduler, timeoutMillis = 1_000)
        quick.onConnectionStarted()

        scheduler.advanceBy(1_000)

        assertThat(termination.terminations.await(5, TimeUnit.SECONDS)).isTrue()
    }
}
