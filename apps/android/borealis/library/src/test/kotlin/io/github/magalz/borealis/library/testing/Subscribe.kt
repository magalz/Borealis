package io.github.magalz.borealis.library.testing

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent

/**
 * Collects [flow] until the test ends, as a screen does, and runs the work that this starts.
 * Cancelling the returned job is the screen leaving.
 */
fun TestScope.subscribe(flow: Flow<*>): Job =
    backgroundScope
        .launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
        .also { runCurrent() }
