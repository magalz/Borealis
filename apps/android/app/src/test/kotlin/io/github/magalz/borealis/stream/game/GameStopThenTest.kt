package io.github.magalz.borealis.stream.game

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Test

/**
 * What a Disconnect waits for: `stopConnectionThen` stops the connection, which talks to the host
 * on a thread of its own, and runs its callback from that thread once the stop is done.
 */
internal class GameStopThenTest : GameTestBase() {
    @Test
    fun `the callback runs after the connection stopped, on the thread that stopped it`() {
        rig.streaming()
        val seen = CopyOnWriteArrayList<List<String>>()
        val threads = CopyOnWriteArrayList<Thread>()

        rig.game.stopConnectionThen {
            threads += Thread.currentThread()
            seen += ShadowRecordingBridge.names
        }
        rig.awaitStop()

        assertThat(threads.single()).isNotSameInstanceAs(Thread.currentThread())
        assertThat(seen.single())
            .containsAtLeast("interruptConnection", "stopConnection", "cleanupBridge")
            .inOrder()
    }

    @Test
    fun `the callback runs at once when no connection runs`() {
        rig.create()
        var ran = false

        rig.game.stopConnectionThen { ran = true }

        assertThat(ran).isTrue()
        assertThat(ShadowRecordingBridge.named("stopConnection")).isEmpty()
    }
}
