package io.github.magalz.borealis.diagnostics.audit

import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.rules.ExternalResource

/**
 * A pool of real threads that a test hands to code that works off the caller's thread. Room runs
 * its queries and its invalidation callbacks on threads of its own, and these tests wait for the
 * result in real time, so a test dispatcher with virtual time would not fit. The pool is closed
 * when the test ends.
 */
class WorkerDispatcherRule(threads: Int = 4) : ExternalResource() {
    val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newFixedThreadPool(threads).asCoroutineDispatcher()

    override fun after() = dispatcher.close()
}
