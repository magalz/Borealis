package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.connection
import org.junit.Rule
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/** Where the time of leaving a stream goes: the stop of the connection, which talks to the host. */
internal class GameStopLogTest : GameTestBase() {
    @get:Rule val log = RecordingLog()

    @Test
    fun `the stop of the connection says it is done and how many milliseconds it took`() {
        rig.streaming()

        rig.stop()

        val done = log.records.single { it.message == "connection stop done" }
        assertThat(done.level).isEqualTo(LogLevel.INFO)
        assertThat(done.fields.map { it.first }).containsExactly("ms")
        assertThat(done.fields.single().second.toLongOrNull()).isAtLeast(0L)
    }

    @Test
    fun `the stop of a connection that never connected is logged too`() {
        rig.create()

        rig.stop()

        assertThat(log.records.map { it.message }).contains("connection stop done")
        val done = log.records.single { it.message == "connection stop done" }
        assertThat(done.fields.map { it.first }).containsExactly("ms")
    }

    @Test
    fun `a second stop of the connection says nothing and stops nothing again`() {
        rig.streaming()
        rig.game.stopConnectionThen {}
        rig.awaitStop()
        // A second stop that reached the connection would find its bridge set up again.
        ReflectionHelpers.setField(rig.connection, "bridgeSetUp", true)

        rig.stop()

        assertThat(log.records.count { it.message == "connection stop done" }).isEqualTo(1)
        assertThat(ShadowRecordingBridge.named("stopConnection")).hasSize(1)
    }
}
