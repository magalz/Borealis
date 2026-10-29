package io.github.magalz.borealis.stream.game

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * One request to finish per exit. Every exit of the stream finishes the activity, and `onStop`,
 * which follows, used to ask again: the system answered with "Duplicate finish request".
 */
@Config(shadows = [ShadowRecordingBridge::class, FinishCountingShadow::class])
internal class GameFinishTest : GameTestBase() {
    @Test
    fun `a stream that the host ends gracefully finishes the activity once`() {
        rig.streaming()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        rig.stop()

        assertThat(rig.game.finishRequests).isEqualTo(1)
    }

    @Test
    fun `a graceful end after another exit finished the activity is not asked again`() {
        rig.streaming()
        rig.game.finish()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        rig.stop()

        assertThat(rig.game.finishRequests).isEqualTo(1)
    }

    @Test
    fun `an exit that finished the activity first, a Quit or a Disconnect, is not asked again`() {
        rig.streaming()
        rig.game.finish()

        rig.stop()

        assertThat(rig.game.finishRequests).isEqualTo(1)
    }

    @Test
    fun `an activity that the system stops while it goes on is finished once`() {
        rig.streaming()
        assertThat(rig.game.isFinishing).isFalse()

        rig.stop()

        assertThat(rig.game.isFinishing).isTrue()
        assertThat(rig.game.finishRequests).isEqualTo(1)
    }
}
