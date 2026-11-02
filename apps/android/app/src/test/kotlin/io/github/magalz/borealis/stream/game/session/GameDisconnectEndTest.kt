package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.recordSession
import io.github.magalz.borealis.stream.game.session
import org.junit.Test

/**
 * The end of a stream that the user leaves with a Disconnect is no failure, whichever turn of the
 * main thread the end of the connection reaches. The phase of the session is Disconnecting from the
 * tap, in the turn that also asks the activity to stop the connection (the actions of the app run
 * their coroutine at once on the main thread), so the order is the end after both, and, for a stop
 * that came late, the end between the phase and the stop. The session ignores a failure while it is
 * Disconnecting.
 */
internal class GameDisconnectEndTest : GameTestBase() {
    @Test
    fun `an end that reaches the main thread after a Disconnect shows no failure and finishes`() {
        rig.streaming()
        rig.recordSession()
        rig.session.disconnect()
        rig.game.stopConnectionThen {}
        rig.awaitStop()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)
        rig.awaitStop()

        assertThat(rig.phase).isEqualTo(StreamPhase.Disconnecting)
        assertThat(rig.game.isFinishing).isTrue()
    }

    @Test
    fun `an end that reaches the main thread between the phase and the stop shows no failure`() {
        rig.streaming()
        rig.recordSession()
        rig.session.disconnect()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)
        rig.awaitStop()

        assertThat(rig.phase).isEqualTo(StreamPhase.Disconnecting)
    }
}
