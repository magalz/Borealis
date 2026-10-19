package io.github.magalz.borealis.host.video

import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.IntConsumer
import org.junit.Test

/** The plumbing Game hands to [StreamStartup.create], replaced here by fakes. */
class StreamStartupTest {
    private val scheduler = FakeWatchdogScheduler()
    private val uiQueue = mutableListOf<Runnable>()
    private val texts = mutableListOf<Int>()
    private var requested = View.GONE
    private val applied = mutableListOf<Int>()
    private var hiding = false
    private val terminated = CountDownLatch(1)
    private val terminationThread = AtomicReference<Thread>()

    private val guard =
        StreamStartup.create(
            Consumer { uiQueue += it },
            IntConsumer { texts += it },
            IntConsumer { requested = it },
            IntConsumer { applied += it },
            BooleanSupplier { hiding },
            Runnable {
                terminationThread.set(Thread.currentThread())
                terminated.countDown()
            },
            scheduler,
        )

    @Test
    fun `the preparing message sets its own text and shows the overlay`() {
        guard.onConnectionStarted()

        assertThat(texts).containsExactly(R.string.stream_preparing_display)
        assertThat(requested).isEqualTo(View.VISIBLE)
        assertThat(applied).containsExactly(View.VISIBLE)
    }

    @Test
    fun `while overlays are hidden the view stays untouched but the request is recorded`() {
        hiding = true

        guard.onConnectionStarted()

        assertThat(requested).isEqualTo(View.VISIBLE)
        assertThat(applied).isEmpty()
    }

    @Test
    fun `the first frame clears the request on the UI thread`() {
        guard.onConnectionStarted()

        guard.onFirstFrame()
        assertThat(requested).isEqualTo(View.VISIBLE)
        uiQueue.forEach { it.run() }

        assertThat(requested).isEqualTo(View.GONE)
        assertThat(applied).containsExactly(View.VISIBLE, View.GONE).inOrder()
    }

    @Test
    fun `clearing while overlays are hidden records GONE without touching the view`() {
        guard.onConnectionStarted()
        hiding = true

        guard.cancel()

        assertThat(requested).isEqualTo(View.GONE)
        assertThat(applied).containsExactly(View.VISIBLE)
    }

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
        assertThat(requested).isEqualTo(View.GONE)
    }
}
