package io.github.magalz.borealis.settings.testing

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/**
 * Polls [condition] until it holds, or fails after [timeoutMillis]. A Robolectric test sets
 * [idleMainLooper], so that the messages that background threads post to the main looper run.
 */
fun eventually(
    timeoutMillis: Long = 5_000,
    idleMainLooper: Boolean = false,
    condition: () -> Boolean,
) {
    val end = System.nanoTime() + timeoutMillis * NANOS_PER_MILLI
    while (!condition()) {
        if (System.nanoTime() > end) throw AssertionError("Not true within $timeoutMillis ms")
        if (idleMainLooper) shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(POLL_MILLIS)
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val POLL_MILLIS = 10L
