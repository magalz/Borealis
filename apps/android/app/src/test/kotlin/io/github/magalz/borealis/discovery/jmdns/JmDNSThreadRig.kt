package io.github.magalz.borealis.discovery.jmdns

import com.google.common.truth.Truth.assertWithMessage

private const val THREAD_NAME = "mDNS Discovery Thread"
private const val POLL_MS = 10L
private const val WAIT_NANOS = 5_000_000_000L

/** The live threads that the jmDNS agents run their discovery loops on. */
internal fun discoveryThreads(): List<Thread> =
    Thread.getAllStackTraces().keys.filter { it.name == THREAD_NAME && it.isAlive }

/** Whether the thread is in the sleep of its loop, and not in a wait for the old thread. */
internal fun Thread.isSleeping() =
    state == Thread.State.TIMED_WAITING && stackTrace.any { it.methodName.startsWith("sleep") }

/** Waits until [condition] holds, and fails if it does not within the wait. */
internal fun awaitUntil(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + WAIT_NANOS
    while (!condition() && System.nanoTime() < deadline) {
        Thread.sleep(POLL_MS)
    }
    assertWithMessage("never: $what").that(condition()).isTrue()
}
