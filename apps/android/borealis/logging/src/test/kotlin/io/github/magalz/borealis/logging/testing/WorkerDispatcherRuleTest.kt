package io.github.magalz.borealis.logging.testing

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

class WorkerDispatcherRuleTest {
    @get:Rule val workers = WorkerDispatcherRule()

    @Test
    fun `the pool has four threads by default`() {
        assertThat(threadsUsedBy(workers)).isEqualTo(4)
    }

    @Test
    fun `the pool has as many threads as it is asked for`() {
        val two = WorkerDispatcherRule(threads = 2)

        try {
            assertThat(threadsUsedBy(two)).isEqualTo(2)
        } finally {
            two.dispatcher.close()
        }
    }

    @Test
    fun `the pool is closed when the test ends`() {
        val rule = WorkerDispatcherRule()
        val body =
            object : Statement() {
                override fun evaluate() = Unit
            }

        rule.apply(body, Description.EMPTY).evaluate()

        assertThat((rule.dispatcher.executor as ExecutorService).isShutdown).isTrue()
    }

    /**
     * A fixed pool starts a new thread for each of its first tasks, so many short tasks end up on
     * exactly as many threads as the pool has.
     */
    private fun threadsUsedBy(rule: WorkerDispatcherRule): Int {
        val threads = ConcurrentHashMap.newKeySet<Thread>()
        runBlocking {
            List(TASKS) { launch(rule.dispatcher) { threads += Thread.currentThread() } }.joinAll()
        }
        return threads.size
    }

    private companion object {
        const val TASKS = 40
    }
}
