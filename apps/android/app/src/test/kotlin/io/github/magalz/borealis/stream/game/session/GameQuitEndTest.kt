package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.stream.RecordingStreamActions
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.streamingSession
import io.github.magalz.borealis.testing.LimeLogCapture
import java.util.logging.Level
import org.junit.Rule
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * The end of a stream that the host causes when it gets the user's own Quit: the host closes the
 * stream before it answers the cancel, and the activity sees it as an ordinary termination. It is
 * the quit's end, so it is not tested for a blocked network and it is not logged as an error.
 */
internal class GameQuitEndTest : GameTestBase() {
    @get:Rule val limeLog = LimeLogCapture()
    @get:Rule val borealisLog = RecordingLog()

    /**
     * A stream that runs and whose session has a quit under way. The real actions of the activity
     * would send the cancel to a host through Hilt, which the rig does not have, so the session
     * that the activity reports to is one with actions that only record.
     */
    private fun streamWithQuitUnderWay(): StreamSession {
        rig.streaming()
        val session = streamingSession(actions = RecordingStreamActions())
        session.quitApp()
        ReflectionHelpers.setField(rig.game, "streamSession", session)
        return session
    }

    private fun connectivityTests() = ShadowRecordingBridge.named("testClientConnectivity")

    @Test
    fun `the end that the quit caused is not tested for a blocked network`() {
        streamWithQuitUnderWay()

        rig.game.connectionTerminated(MoonBridgeCodes.ENDED)
        rig.awaitStop()

        assertThat(connectivityTests()).isEmpty()
    }

    @Test
    fun `the end that the quit caused is logged at info as the quit's, not as an error`() {
        streamWithQuitUnderWay()

        rig.game.connectionTerminated(MoonBridgeCodes.ENDED)
        rig.awaitStop()

        val ended = borealisLog.records.single { it.message == "quit stream ended" }
        assertThat(ended.level).isEqualTo(LogLevel.INFO)
        assertThat(ended.fields.map { it.first }).contains("code")
        assertThat(limeLog.entries.filter { it.first == Level.SEVERE }).isEmpty()
        assertThat(limeLog.messages.filter { "Connection terminated" in it }).isEmpty()
    }

    @Test
    fun `the end that the quit caused keeps the screen on Quitting and stops the connection`() {
        streamWithQuitUnderWay()

        rig.game.connectionTerminated(MoonBridgeCodes.ENDED)
        rig.awaitStop()

        val quitting = rig.phase as StreamPhase.Quitting
        assertThat(quitting.ended).isNotNull()
        assertThat(ShadowRecordingBridge.names).contains("stopConnection")
    }

    @Test
    fun `an end that no quit caused is tested for a blocked network and logged as an error`() {
        rig.streaming()

        rig.game.connectionTerminated(MoonBridgeCodes.ENDED)
        rig.awaitStop()

        assertThat(connectivityTests()).hasSize(1)
        assertThat(limeLog.entries)
            .contains(Level.SEVERE to "Connection terminated: ${MoonBridgeCodes.ENDED}")
        assertThat(borealisLog.records.map { it.message }).doesNotContain("quit stream ended")
        assertThat(rig.phase).isInstanceOf(StreamPhase.Failed::class.java)
    }

    private object MoonBridgeCodes {
        const val ENDED = -1
    }
}
