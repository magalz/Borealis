package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.stream.game.GameTestBase
import org.junit.Rule
import org.junit.Test

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
    fun `a connection that is not running has no stop to log`() {
        rig.create()

        rig.stop()

        assertThat(log.records.map { it.message }).doesNotContain("connection stop done")
    }
}
