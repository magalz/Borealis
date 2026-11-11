package io.github.magalz.borealis.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * A scope whose coroutines run at once, on the thread that starts or resumes them, as the ones of
 * `Dispatchers.Unconfined` do. For the code under test that takes a scope and nothing else of the
 * dispatcher: a test hands it this one instead of naming the dispatcher itself. The dispatcher runs
 * on virtual time, so a `delay()` or a `withTimeout()` under it never fires unless the test
 * advances the scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun unconfinedScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())
