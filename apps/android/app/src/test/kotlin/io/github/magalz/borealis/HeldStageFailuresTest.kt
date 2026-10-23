package io.github.magalz.borealis

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnectionListener
import io.github.magalz.borealis.host.launch.ResumeCollisions
import org.junit.Test

class HeldStageFailuresTest {
    /** Records what reaches the listener Game would implement; every other method is a no-op. */
    private class RecordingListener : NvConnectionListener {
        class Failure(val stage: String, val portFlags: Int, val errorCode: Int)

        val failures = mutableListOf<Failure>()
        val messages = mutableListOf<String>()

        override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
            failures += Failure(stage, portFlags, errorCode)
        }

        override fun displayMessage(message: String) {
            messages += message
        }

        override fun stageStarting(stage: String?) = Unit

        override fun stageComplete(stage: String?) = Unit

        override fun connectionStarted() = Unit

        override fun connectionTerminated(errorCode: Int) = Unit

        override fun connectionStatusUpdate(connectionStatus: Int) = Unit

        override fun displayTransientMessage(message: String) = Unit

        override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) =
            Unit

        override fun rumbleTriggers(
            controllerNumber: Short,
            leftTrigger: Short,
            rightTrigger: Short,
        ) = Unit

        override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) = Unit

        override fun setMotionEventState(
            controllerNumber: Short,
            motionType: Byte,
            reportRateHz: Short,
        ) = Unit

        override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) = Unit
    }

    private val inner = RecordingListener()
    private val collisions = ResumeCollisions({ false }, { _, _, _, _ -> }, { null })
    private val held = HeldStageFailures(inner, collisions) { RTSP }

    /** A start whose `/resume` was just accepted, so an RTSP error -1 is a collision. */
    private fun resumed() = collisions.resumeAccepted(false)

    @Test
    fun `the first RTSP failure after a resume is swallowed`() {
        resumed()

        held.stageFailed(RTSP, PORTS, -1)

        assertThat(inner.failures).isEmpty()
    }

    @Test
    fun `the failure after the restart is forwarded without port flags and with its error code`() {
        resumed()
        held.stageFailed(RTSP, PORTS, -1)
        assertThat(collisions.restartAfterFailure()).isTrue()
        resumed()

        held.stageFailed(RTSP, PORTS, -1)

        val failure = inner.failures.single()
        assertThat(failure.stage).isEqualTo(RTSP)
        assertThat(failure.portFlags).isEqualTo(0)
        assertThat(failure.errorCode).isEqualTo(-1)
    }

    @Test
    fun `a failure that is not a collision is forwarded unchanged`() {
        held.stageFailed(RTSP, PORTS, -1)

        val failure = inner.failures.single()
        assertThat(failure.stage).isEqualTo(RTSP)
        assertThat(failure.portFlags).isEqualTo(PORTS)
        assertThat(failure.errorCode).isEqualTo(-1)
    }

    @Test
    fun `a stage other than RTSP is not treated as an RTSP failure`() {
        resumed()

        held.stageFailed("audio stream initialization", PORTS, -1)

        val failure = inner.failures.single()
        assertThat(failure.portFlags).isEqualTo(PORTS)
        assertThat(failure.errorCode).isEqualTo(-1)
    }

    @Test
    fun `every other listener call goes to the inner listener`() {
        held.displayMessage("hello")

        assertThat(inner.messages).containsExactly("hello")
    }

    private companion object {
        const val RTSP = "RTSP handshake"
        const val PORTS = 0x2
    }
}
