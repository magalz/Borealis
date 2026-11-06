package io.github.magalz.borealis.input.ch5

import android.hardware.Sensor
import android.util.SparseArray
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.feedback.DeviceFeatures
import io.github.magalz.borealis.input.feedback.FeedbackRig
import io.github.magalz.borealis.input.feedback.Hardware
import io.github.magalz.borealis.input.feedback.RecordingUsbController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * The rumble and the motion request come from the native connection thread, while the main thread
 * changes the lists of pads. Like the other host callbacks (see `DeviceListThreadsTest`), they walk
 * the list as it was when they began and take no lock: a pad that leaves meanwhile leaves no hole
 * in their loop, and the packet send, which holds the input lock, does not hold them up.
 */
class HostCallbackLoopsTest : FeedbackCharacterization() {
    /**
     * A host callback on a thread of its own, held when it begins the step number [holdAtStep] of
     * its loops over the pads: the step has checked how many pads there are and has not read the
     * next one yet.
     */
    private class HeldReader(rig: FeedbackRig, private val holdAtStep: Int, read: () -> Unit) {
        private val held = CountDownLatch(1)
        private val letGo = CountDownLatch(1)
        private val steps = AtomicInteger()
        @Volatile private var failure: Throwable? = null
        private val thread =
            Thread(
                {
                    try {
                        read()
                    } catch (e: Throwable) {
                        failure = e
                    }
                },
                "host-callbacks",
            )

        init {
            rig.handler.deviceListStep = Runnable {
                if (Thread.currentThread() === thread && steps.incrementAndGet() == holdAtStep) {
                    held.countDown()
                    check(letGo.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        "The reader was not let go"
                    }
                }
            }
        }

        fun hold() {
            thread.start()
            check(held.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                "The reader did not reach step $holdAtStep"
            }
        }

        /** Lets the reader go on, waits for its end, and returns what it threw, if anything. */
        fun finish(): Throwable? {
            letGo.countDown()
            thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
            check(!thread.isAlive) { "The reader did not end" }
            return failure
        }
    }

    private fun FeedbackRig.padWith(id: Int, features: DeviceFeatures) =
        wake(connectPad(id = id, features = features))

    private fun usbPad(rig: FeedbackRig, id: Int): RecordingUsbController =
        RecordingUsbController(id, rig.handler).also {
            rig.handler.deviceAdded(it)
            it.report(ControllerPacket.A_FLAG)
        }

    /** The field [name] of the context of the pad with [id]. */
    private fun fieldOf(rig: FeedbackRig, id: Int, name: String): Any? {
        @Suppress("UNCHECKED_CAST") // The field holds the handler's contexts by construction.
        val contexts =
            ControllerHandler::class
                .java
                .getDeclaredField("inputDeviceContexts")
                .apply { isAccessible = true }
                .get(rig.handler) as SparseArray<Any>
        val context = contexts.get(id)
        return context.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(context)
    }

    @Test
    fun `a rumble partway through the pads still reaches the ones that stay when the first leaves`() {
        val rig = rig()
        val vibrators = (5..7).map { Hardware.vibrator() }
        vibrators.forEachIndexed { i, vibrator ->
            rig.padWith(5 + i, DeviceFeatures(vibrator = vibrator))
        }
        val reader = HeldReader(rig, holdAtStep = 2) { rig.rumble(0x6400, 0x6400) }
        reader.hold()

        rig.handler.onInputDeviceRemoved(5)

        assertThat(reader.finish()).isNull()
        assertThat(Hardware.calls(vibrators[1])).hasSize(1)
        assertThat(Hardware.calls(vibrators[2])).hasSize(1)
    }

    @Test
    fun `a rumble partway through the USB pads still reaches the ones that stay when the first leaves`() {
        val rig = rig()
        val pads = (1..3).map { usbPad(rig, it) }
        val reader = HeldReader(rig, holdAtStep = 2) { rig.rumble(0x4000, 0x2000) }
        reader.hold()

        rig.handler.deviceRemoved(pads[0])

        assertThat(reader.finish()).isNull()
        assertThat(pads[1].rumbles).containsExactly(0x4000 to 0x2000)
        assertThat(pads[2].rumbles).containsExactly(0x4000 to 0x2000)
    }

    @Test
    fun `a motion request partway through the pads still reaches the ones that stay when the first leaves`() {
        val rig = rig { gamepadMotionSensors = true }
        (5..7).forEach {
            val sensors = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
            rig.padWith(it, DeviceFeatures(sensorManager = sensors))
        }
        // The first step is the one of the search for the pad that reports, which stops at the
        // first pad with sensors of its own; the loop over the pads begins after it
        val reader = HeldReader(rig, holdAtStep = 3) { rig.motionState(GYRO, 100) }
        reader.hold()

        rig.handler.onInputDeviceRemoved(5)

        assertThat(reader.finish()).isNull()
        assertThat(fieldOf(rig, 6, "gyroReportRateHz")).isEqualTo(100.toShort())
        assertThat(fieldOf(rig, 7, "gyroReportRateHz")).isEqualTo(100.toShort())
    }

    @Test
    fun `a rumble and a motion request do not wait for the lock of the packet send`() {
        val rig = rig { gamepadMotionSensors = true }
        val vibrator = Hardware.vibrator()
        val sensors = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
        rig.padWith(5, DeviceFeatures(vibrator = vibrator, sensorManager = sensors))
        val inputLock = ReflectionHelpers.getField<Any>(rig.handler, "inputLock")
        val reader =
            Thread(
                {
                    rig.rumble(0x6400, 0x6400)
                    rig.motionState(GYRO, 100)
                },
                "host-callbacks",
            )

        synchronized(inputLock) {
            reader.start()
            reader.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
            assertThat(reader.isAlive).isFalse()
        }
        assertThat(Hardware.calls(vibrator)).hasSize(1)
        assertThat(Hardware.sensors(sensors).log).containsExactly("register 4 10000")
    }

    private companion object {
        const val GYRO = 2
        const val WAIT_SECONDS = 10L
    }
}
