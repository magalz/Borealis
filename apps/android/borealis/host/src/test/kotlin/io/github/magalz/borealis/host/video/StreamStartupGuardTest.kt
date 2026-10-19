package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Test

class StreamStartupGuardTest {
    /** Queues UI work so a test can tell what runs on the caller and what is posted. */
    private class FakeHost : StreamStartupHost {
        val uiQueue = mutableListOf<Runnable>()
        var visible = false
        var shown = 0
        val terminations = CountDownLatch(1)
        val terminationThread = AtomicReference<Thread>()
        @Volatile var terminationCount = 0

        override fun showPreparing() {
            shown++
            visible = true
        }

        override fun clearPreparing() {
            visible = false
        }

        override fun runOnUi(action: Runnable) {
            uiQueue += action
        }

        override fun terminateNoVideoFrame() {
            terminationCount++
            terminationThread.set(Thread.currentThread())
            terminations.countDown()
        }

        fun drainUi() {
            while (uiQueue.isNotEmpty()) uiQueue.removeAt(0).run()
        }
    }

    private val host = FakeHost()
    private val scheduler = FakeWatchdogScheduler()
    private val guard = StreamStartupGuard(host, scheduler)

    @Test
    fun `starting the connection shows the preparing message`() {
        guard.onConnectionStarted()

        assertThat(host.visible).isTrue()
    }

    @Test
    fun `the first frame hides the message on the UI thread and stops the watchdog`() {
        guard.onConnectionStarted()

        guard.onFirstFrame()
        assertThat(host.visible).isTrue()
        host.drainUi()
        scheduler.advanceBy(60_000)

        assertThat(host.visible).isFalse()
        assertThat(host.terminationCount).isEqualTo(0)
    }

    @Test
    fun `a frame that arrives before the connection started keeps the message away`() {
        guard.onFirstFrame()
        host.drainUi()

        guard.onConnectionStarted()
        host.drainUi()
        scheduler.advanceBy(60_000)

        assertThat(host.shown).isEqualTo(0)
        assertThat(host.terminationCount).isEqualTo(0)
    }

    @Test
    fun `the timeout hides the message and ends the stream off the calling thread`() {
        guard.onConnectionStarted()

        scheduler.advanceBy(45_000)

        assertThat(host.terminations.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(host.terminationThread.get()).isNotSameInstanceAs(Thread.currentThread())
        assertThat(host.visible).isFalse()
    }

    @Test
    fun `the stream is ended only once however often the timeout and events repeat`() {
        guard.onConnectionStarted()
        scheduler.advanceBy(45_000)
        assertThat(host.terminations.await(5, TimeUnit.SECONDS)).isTrue()

        guard.onConnectionStarted()
        guard.onFirstFrame()
        guard.cancel()
        scheduler.advanceBy(60_000)

        assertThat(host.terminationCount).isEqualTo(1)
    }

    @Test
    fun `cancel hides the message and silences the watchdog`() {
        guard.onConnectionStarted()

        guard.cancel()
        scheduler.advanceBy(60_000)

        assertThat(host.visible).isFalse()
        assertThat(host.terminationCount).isEqualTo(0)
    }

    @Test
    fun `a cancelled guard does not show the message later`() {
        guard.cancel()

        guard.onConnectionStarted()

        assertThat(host.shown).isEqualTo(0)
    }

    @Test
    fun `a warning replaces the message and the first frame leaves the warning up`() {
        guard.onConnectionStarted()
        guard.onWarning()

        guard.onFirstFrame()
        host.drainUi()

        assertThat(host.visible).isTrue()
    }

    @Test
    fun `clearing a warning during startup brings the message back`() {
        guard.onConnectionStarted()
        guard.onWarning()
        host.visible = false

        guard.onWarningCleared()

        assertThat(host.visible).isTrue()
    }

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
        assertThat(host.terminationCount).isEqualTo(0)
    }

    @Test
    fun `a first frame reported from the render thread stops the watchdog`() {
        guard.onConnectionStarted()

        thread(name = "render") { guard.onFirstFrame() }.join()
        scheduler.advanceBy(60_000)

        assertThat(host.terminationCount).isEqualTo(0)
    }

    @Test
    fun `a cancelled guard does not arm the watchdog when the connection starts later`() {
        guard.cancel()

        guard.onConnectionStarted()
        scheduler.advanceBy(60_000)

        assertThat(scheduler.scheduledCount).isEqualTo(0)
        assertThat(host.terminationCount).isEqualTo(0)
    }

    @Test
    fun `a custom timeout reaches the watchdog`() {
        val quick = StreamStartupGuard(host, scheduler, timeoutMillis = 1_000)
        quick.onConnectionStarted()

        scheduler.advanceBy(1_000)

        assertThat(host.terminations.await(5, TimeUnit.SECONDS)).isTrue()
    }
}
