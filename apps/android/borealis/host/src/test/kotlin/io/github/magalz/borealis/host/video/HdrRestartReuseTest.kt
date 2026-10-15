package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

class HdrRestartReuseTest {
    private val reuse = HdrRestartReuse()

    @Test
    fun `an IDR after an HDR restart request is retried`() {
        reuse.requested()

        assertThat(reuse.retryAfterRecovery(isIdr = true)).isTrue()
    }

    @Test
    fun `the retry happens once per request`() {
        reuse.requested()

        assertThat(reuse.retryAfterRecovery(isIdr = true)).isTrue()
        assertThat(reuse.retryAfterRecovery(isIdr = true)).isFalse()
    }

    @Test
    fun `a new request allows one more retry`() {
        reuse.requested()
        reuse.retryAfterRecovery(isIdr = true)
        reuse.requested()

        assertThat(reuse.retryAfterRecovery(isIdr = true)).isTrue()
    }

    @Test
    fun `a recovery nobody asked for is not retried`() {
        assertThat(reuse.retryAfterRecovery(isIdr = true)).isFalse()
    }

    @Test
    fun `a non-IDR frame is not retried`() {
        reuse.requested()

        assertThat(reuse.retryAfterRecovery(isIdr = false)).isFalse()
    }

    @Test
    fun `a non-IDR frame uses up the request`() {
        // The decoder is already restarted when a non-IDR frame fails to fetch; the next IDR
        // lands on it normally, so a later unrelated recovery must not be treated as the HDR one.
        reuse.requested()
        reuse.retryAfterRecovery(isIdr = false)

        assertThat(reuse.retryAfterRecovery(isIdr = true)).isFalse()
    }

    @Test
    fun `concurrent callers share one retry per request`() {
        val granted = AtomicInteger()
        val pool = Executors.newFixedThreadPool(4)
        try {
            repeat(200) {
                reuse.requested()
                val start = CountDownLatch(1)
                val done = CountDownLatch(4)
                repeat(4) {
                    pool.execute {
                        start.await()
                        if (reuse.retryAfterRecovery(isIdr = true)) granted.incrementAndGet()
                        done.countDown()
                    }
                }
                start.countDown()
                assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()
            }
        } finally {
            pool.shutdownNow()
        }

        assertThat(granted.get()).isEqualTo(200)
    }

    @Test
    fun `a request made on another thread is seen by the decoder thread`() {
        val pool = Executors.newSingleThreadExecutor()
        try {
            pool.submit { reuse.requested() }.get(5, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        assertThat(reuse.retryAfterRecovery(isIdr = true)).isTrue()
    }
}
