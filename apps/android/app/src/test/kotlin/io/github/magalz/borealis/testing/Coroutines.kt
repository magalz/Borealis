package io.github.magalz.borealis.testing

import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.rules.ExternalResource

/**
 * A scope whose coroutines run at once, on the thread that starts or resumes them, as the ones of
 * `Dispatchers.Unconfined` do. For the code under test that takes a scope and nothing else of the
 * dispatcher: a test hands it this one instead of naming the dispatcher itself. The dispatcher runs
 * on virtual time, so a `delay()` or a `withTimeout()` under it never fires unless the test
 * advances the scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun unconfinedScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())

/**
 * A pool of real threads that a test hands to code that works off the caller's thread and blocks
 * there: these tests wait for the result in real time, so a test dispatcher with virtual time would
 * not fit. The pool is closed when the test ends.
 */
class WorkerDispatcherRule : ExternalResource() {
    val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newFixedThreadPool(THREADS).asCoroutineDispatcher()

    override fun after() = dispatcher.close()

    private companion object {
        const val THREADS = 4
    }
}
