package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

class FirstFrameSignalTest {
    private val signal = FirstFrameSignal()
    private val calls = AtomicInteger()

    @Test
    fun `the first rendered frame notifies the listener`() {
        signal.listener = Runnable { calls.incrementAndGet() }

        signal.frameRendered()

        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `later frames do not notify again`() {
        signal.listener = Runnable { calls.incrementAndGet() }

        repeat(1_000) { signal.frameRendered() }

        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `a frame without a listener is harmless`() {
        signal.frameRendered()
        signal.frameRendered()

        assertThat(calls.get()).isEqualTo(0)
    }

    @Test
    fun `a frame without a listener does not use up the signal`() {
        signal.frameRendered()

        signal.listener = Runnable { calls.incrementAndGet() }
        signal.frameRendered()
        signal.frameRendered()

        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `frames from several threads notify once`() {
        signal.listener = Runnable { calls.incrementAndGet() }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val start = CountDownLatch(1)
            val done = CountDownLatch(4)
            repeat(4) {
                pool.execute {
                    start.await()
                    repeat(500) { signal.frameRendered() }
                    done.countDown()
                }
            }
            start.countDown()
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()
        } finally {
            pool.shutdownNow()
        }

        assertThat(calls.get()).isEqualTo(1)
    }
}
