package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test

/** The connection thread, the decoder and the main thread all report to the same session. */
class StreamSessionThreadsTest {
    /** Runs [work] with its index on [THREADS] threads that start together. */
    private fun onManyThreads(work: (Int) -> Unit) {
        val pool = Executors.newFixedThreadPool(THREADS)
        val gate = CountDownLatch(1)
        try {
            val results =
                (0 until THREADS).map { index ->
                    pool.submit {
                        gate.await()
                        work(index)
                    }
                }
            gate.countDown()
            results.forEach { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `starting events from eight threads end in one streaming state`() {
        val session = aSession()

        onManyThreads { index ->
            repeat(ROUNDS) {
                session.onStage(StartStage.entries[index % StartStage.entries.size])
                session.onConnectionStarted(aSessionInfo)
                session.onFirstFrame()
                session.onPip(index % 2 == 0)
            }
        }

        val phase = session.state.value.phase
        assertThat(phase).isInstanceOf(StreamPhase.Streaming::class.java)
        assertThat((phase as StreamPhase.Streaming).info).isEqualTo(aSessionInfo)
        assertThat(phase.panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `eight threads failing keep the first failure and never leave it`() {
        val session = aSession()
        val failures =
            List(THREADS) { StreamFailure(FailureKind.GENERIC, "failure $it", portHint = null) }
        val seen = AtomicReference<StreamPhase.Failed?>(null)
        val changed = AtomicReference<String?>(null)

        onManyThreads { index ->
            repeat(ROUNDS) {
                session.onStage(StartStage.STARTING_APP)
                session.onFailed(failures[index])
                session.onFirstFrame()
                val phase = session.state.value.phase
                if (phase is StreamPhase.Failed) {
                    seen.compareAndSet(null, phase)
                    if (seen.get() != phase) changed.set("$phase after ${seen.get()}")
                }
            }
        }

        assertThat(changed.get()).isNull()
        val phase = session.state.value.phase
        assertThat(phase).isInstanceOf(StreamPhase.Failed::class.java)
        assertThat((phase as StreamPhase.Failed).failure).isIn(failures)
    }

    @Test
    fun `perf snapshots from eight threads leave the last one written`() {
        val session = aSession()
        val snapshots = List(THREADS) { aPerf(totalFps = it.toFloat()) }

        onManyThreads { index -> repeat(ROUNDS) { session.onPerf(snapshots[index]) } }

        assertThat(session.perf.value).isIn(snapshots)
    }

    private companion object {
        const val THREADS = 8
        const val ROUNDS = 500
        const val TIMEOUT_SECONDS = 30L
    }
}
