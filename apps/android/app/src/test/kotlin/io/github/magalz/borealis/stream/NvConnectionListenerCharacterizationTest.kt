package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnectionListener
import io.github.magalz.borealis.HeldStageFailures
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.host.launch.ResumeCollisions
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test

/**
 * The callbacks of [NvConnectionListener]: which ones a listener must implement, which ones have
 * a default, and what the defaults do. `Game` (Java) implements the interface, `MoonBridge` and
 * `NvConnection` (Java) call it, and `HeldStageFailures` (Kotlin) wraps it.
 *
 * Characterization: these tests pass on the Java interface and on its Kotlin conversion.
 */
class NvConnectionListenerCharacterizationTest {
    /** Implements only what the interface requires: the three launch callbacks keep their defaults. */
    private class RequiredOnly : NvConnectionListener {
        override fun stageStarting(stage: String?) = Unit

        override fun stageComplete(stage: String?) = Unit

        override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) = Unit

        override fun connectionStarted() = Unit

        override fun connectionTerminated(errorCode: Int) = Unit

        override fun connectionStatusUpdate(connectionStatus: Int) = Unit

        override fun displayMessage(message: String) = Unit

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

    /** Overrides the three launch callbacks and records them. */
    private class LaunchRecorder : NvConnectionListener by RequiredOnly() {
        val calls = mutableListOf<String>()

        override fun launchRetrying(category: Int, attempt: Int, maxAttempts: Int, delayMs: Int) {
            calls += "launchRetrying $category $attempt $maxAttempts $delayMs"
        }

        override fun launchStarting(resume: Boolean) {
            calls += "launchStarting $resume"
        }

        override fun launchConflict(
            runningAppId: Int,
            runningAppName: String?,
            requestedAppId: Int,
            requestedAppName: String?,
            answer: ConflictAnswer,
        ) {
            calls += "launchConflict $runningAppId $runningAppName $requestedAppId $requestedAppName"
        }
    }

    /** The choice that won: [ConflictAnswer.choose] only says whether the caller's choice won. */
    private fun ConflictAnswer.chosen(): Any? =
        (ConflictAnswer::class.java.getDeclaredField("chosen").also { it.isAccessible = true }.get(this)
                as AtomicReference<*>)
            .get()

    private fun signature(method: Method): String =
        "${method.name}(${method.parameterTypes.joinToString(",") { it.simpleName }})" +
            method.returnType.simpleName

    private val listenerMethods: List<Method> =
        NvConnectionListener::class.java.methods.filterNot { it.isSynthetic }

    @Test
    fun `a listener has to implement these callbacks`() {
        val required = listenerMethods.filter { !it.isDefault }.map(::signature)

        assertThat(required)
            .containsExactly(
                "stageStarting(String)void",
                "stageComplete(String)void",
                "stageFailed(String,int,int)void",
                "connectionStarted()void",
                "connectionTerminated(int)void",
                "connectionStatusUpdate(int)void",
                "displayMessage(String)void",
                "displayTransientMessage(String)void",
                "rumble(short,short,short)void",
                "rumbleTriggers(short,short,short)void",
                "setHdrMode(boolean,byte[])void",
                "setMotionEventState(short,byte,short)void",
                "setControllerLED(short,byte,byte,byte)void",
            )
    }

    @Test
    fun `the launch callbacks have a default, so Java listeners need not implement them`() {
        val defaults = listenerMethods.filter { it.isDefault }.map(::signature)

        assertThat(defaults)
            .containsExactly(
                "launchRetrying(int,int,int,int)void",
                "launchStarting(boolean)void",
                "launchConflict(int,String,int,String,ConflictAnswer)void",
            )
    }

    @Test
    fun `by default a launch retry and a launch start are ignored`() {
        val listener: NvConnectionListener = RequiredOnly()

        listener.launchRetrying(1, 2, 3, 4)
        listener.launchStarting(true)
    }

    @Test
    fun `by default a launch conflict is answered with Cancel`() {
        val listener: NvConnectionListener = RequiredOnly()
        val answer = ConflictAnswer()

        listener.launchConflict(7, "Steam", 8, "Desktop", answer)

        assertThat(answer.chosen()).isEqualTo(ConflictChoice.CANCEL)
    }

    /**
     * `MoonBridge` is the only caller of a `HeldStageFailures`, and it never calls the launch
     * callbacks, so this path is not reached while streaming. What the wrapper does with them: it
     * keeps the defaults of the interface and does not forward them.
     */
    @Test
    fun `the held stage failures keep the defaults of the launch callbacks`() {
        val inner = LaunchRecorder()
        val held =
            HeldStageFailures(inner, ResumeCollisions({ false }, { _, _, _, _ -> }, { null })) {
                "RTSP handshake"
            }
        val answer = ConflictAnswer()

        held.launchRetrying(1, 2, 3, 4)
        held.launchStarting(false)
        held.launchConflict(7, "Steam", 8, null, answer)

        assertThat(inner.calls).isEmpty()
        assertThat(answer.chosen()).isEqualTo(ConflictChoice.CANCEL)
    }
}
