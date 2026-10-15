package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

class HdrRestartReuseTest {
    private val reuse = HdrRestartReuse()

    private val vps = mutableListOf(byteArrayOf(1, 2))
    private val sps = mutableListOf(byteArrayOf(3), byteArrayOf(4, 5))
    private val pps = mutableListOf(byteArrayOf(6))

    /** What `configureAndStartDecoder` does to the parameter sets of the restarted decoder. */
    private fun configure() {
        vps.clear()
        sps.clear()
        pps.clear()
    }

    private fun restore() = reuse.restoreForRetry(vps, sps, pps)

    @Test
    fun `after a request the parameter sets are restored once`() {
        reuse.requested()
        reuse.snapshot(vps, sps, pps)
        val expected = Triple(vps.toList(), sps.toList(), pps.toList())
        configure()

        assertThat(restore()).isTrue()

        assertThat(vps).containsExactlyElementsIn(expected.first).inOrder()
        assertThat(sps).containsExactlyElementsIn(expected.second).inOrder()
        assertThat(pps).containsExactlyElementsIn(expected.third).inOrder()
    }

    @Test
    fun `the restored parameter sets keep their bytes`() {
        reuse.requested()
        reuse.snapshot(vps, sps, pps)
        configure()

        restore()

        assertThat(vps.single()).isEqualTo(byteArrayOf(1, 2))
        assertThat(sps.map { it.toList() }).containsExactly(listOf<Byte>(3), listOf<Byte>(4, 5))
        assertThat(pps.single()).isEqualTo(byteArrayOf(6))
    }

    @Test
    fun `without a request nothing is kept and nothing is restored`() {
        reuse.snapshot(vps, sps, pps)
        configure()

        assertThat(restore()).isFalse()

        assertThat(vps).isEmpty()
        assertThat(sps).isEmpty()
        assertThat(pps).isEmpty()
    }

    @Test
    fun `a request without a snapshot restores nothing`() {
        reuse.requested()
        configure()

        assertThat(restore()).isFalse()

        assertThat(vps).isEmpty()
    }

    @Test
    fun `a request made after the snapshot was taken restores nothing`() {
        reuse.snapshot(vps, sps, pps)
        reuse.requested()
        configure()

        assertThat(restore()).isFalse()

        assertThat(sps).isEmpty()
    }

    @Test
    fun `restoring uses the request up`() {
        reuse.requested()
        reuse.snapshot(vps, sps, pps)
        configure()

        assertThat(restore()).isTrue()
        configure()

        assertThat(restore()).isFalse()
        assertThat(vps).isEmpty()
    }

    @Test
    fun `a failed restore leaves the lists alone`() {
        assertThat(restore()).isFalse()

        assertThat(vps).hasSize(1)
        assertThat(sps).hasSize(2)
        assertThat(pps).hasSize(1)
    }

    @Test
    fun `a new request with a new snapshot allows one more restore`() {
        reuse.requested()
        reuse.snapshot(vps, sps, pps)
        configure()
        restore()
        reuse.requested()
        reuse.snapshot(vps, sps, pps)
        configure()

        assertThat(restore()).isTrue()
        assertThat(sps).hasSize(2)
    }

    @Test
    fun `a snapshot is not changed by later changes to the lists`() {
        reuse.requested()
        reuse.snapshot(vps, sps, pps)
        sps.add(byteArrayOf(9))
        vps.clear()

        configure()
        restore()

        assertThat(vps).hasSize(1)
        assertThat(sps).hasSize(2)
    }

    @Test
    fun `concurrent callers share one restore per request`() {
        val granted = AtomicInteger()
        val pool = Executors.newFixedThreadPool(4)
        try {
            repeat(200) {
                reuse.requested()
                reuse.snapshot(vps, sps, pps)
                val start = CountDownLatch(1)
                val done = CountDownLatch(4)
                repeat(4) {
                    pool.execute {
                        start.await()
                        val v = mutableListOf<ByteArray>()
                        val s = mutableListOf<ByteArray>()
                        val p = mutableListOf<ByteArray>()
                        if (reuse.restoreForRetry(v, s, p)) granted.incrementAndGet()
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
        reuse.snapshot(vps, sps, pps)
        configure()

        assertThat(restore()).isTrue()
    }
}
