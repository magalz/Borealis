package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.concurrent.thread
import org.junit.Test

/** The log of the renderer rig: who wrote a line, and which waits point at the race of BORE-121. */
class ThreadedLogTest {
    private val log = ThreadedLog()

    private fun writtenBy(threadName: String, message: String) {
        thread(name = threadName) { log.publish(LogRecord(Level.INFO, message)) }.join()
    }

    private fun waiting(threadName: String, flags: Int) =
        writtenBy(threadName, "Waiting to quiesce decoder threads: $flags")

    @Test
    fun `a line keeps the thread that wrote it`() {
        writtenBy("somebody", "a line")

        assertThat(log.lines.single().toString()).isEqualTo("[somebody] a line")
    }

    @Test
    fun `a thread that waits without its own bit in the flags is the race`() {
        waiting(RENDERER_THREAD_NAME, INPUT_THREAD or CHOREOGRAPHER)
        waiting(INPUT_CALL_THREAD, RENDERER_THREAD or CHOREOGRAPHER)

        assertThat(log.waitsWithoutOwnBit().map { it.thread })
            .containsExactly(RENDERER_THREAD_NAME, INPUT_CALL_THREAD)
            .inOrder()
    }

    @Test
    fun `a thread that waits with its own bit is not the race`() {
        waiting(RENDERER_THREAD_NAME, RENDERER_THREAD or CHOREOGRAPHER)
        waiting(INPUT_CALL_THREAD, INPUT_THREAD or CHOREOGRAPHER)
        writtenBy(RENDERER_THREAD_NAME, "Codec recovery attempt: 1")

        assertThat(log.waitsWithoutOwnBit()).isEmpty()
    }

    @Test
    fun `a wait of a thread that the log does not know is never the race`() {
        waiting("another thread", INPUT_THREAD)

        assertThat(log.waitsWithoutOwnBit()).isEmpty()
    }

    @Test
    fun `awaiting a wait skips the waits with other flags`() {
        waiting(INPUT_CALL_THREAD, INPUT_THREAD)
        waiting(INPUT_CALL_THREAD, INPUT_THREAD or CHOREOGRAPHER)

        log.awaitWaiting(INPUT_THREAD or CHOREOGRAPHER)
    }
}
