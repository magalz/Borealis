package io.github.magalz.borealis.input.threading

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.ControllerHandlerRig
import io.github.magalz.borealis.input.devices.ContextView
import io.github.magalz.borealis.input.feedback.RecordingUsbController
import io.github.magalz.borealis.testing.addInputDeviceContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

/**
 * The handler's lists of pads are changed by the main thread (a pad leaves, an event of a new pad
 * arrives) and by the USB driver's threads, and read by others: the host's callbacks (rumble, LED,
 * motion) run on the native connection thread, and the battery poll on the handler's own thread. A
 * reader that is partway through the pads when one leaves must still see every pad that stayed, and
 * no hole where a pad was: the list it walks does not change under it.
 *
 * A reader is held at the second step of its loop, which is the moment that decides it: the loop
 * has checked how many pads there are and has not read the next one yet. The main thread changes
 * the list there and reads it once, which is when an `android.util.SparseArray` moves its entries
 * in place, and then the reader goes on.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceListThreadsTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val handler = rig.handler

    /** A pad of Android's input devices with the controller number 0, as its first input gives. */
    private fun systemPad(id: Int): Any =
        handler.addInputDeviceContext(id, mapOf("assignedControllerNumber" to true))

    /** A pad of the USB driver that has sent its first input, which gives it a number. */
    private fun usbPad(id: Int): RecordingUsbController =
        RecordingUsbController(id, handler).also {
            handler.deviceAdded(it)
            it.report(ControllerPacket.A_FLAG)
        }

    /**
     * A host callback that runs on a thread of its own, as the native connection thread runs them.
     * The thread is held when it starts its second step through the pads.
     */
    private inner class HeldReader(private val read: () -> Unit) {
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
            handler.deviceListStep = Runnable {
                if (Thread.currentThread() === thread && steps.incrementAndGet() == 2) {
                    held.countDown()
                    check(letGo.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        "The reader was not let go"
                    }
                }
            }
        }

        fun holdInSecondStep() {
            thread.start()
            check(held.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                "The reader did not reach its second step"
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

    private fun leftTriggerOf(pad: Any) = ContextView(pad).short("leftTriggerMotor")

    @Test
    fun `a host callback partway through the pads still reaches the ones that stay when the first leaves`() {
        val pads = (1..3).map(::systemPad)
        val reader = HeldReader { handler.handleRumbleTriggers(0, LEFT, RIGHT) }
        reader.holdInSecondStep()

        handler.onInputDeviceRemoved(1)
        assertThat(handler.isMouseEmulationOnAnyPad).isFalse()

        assertThat(reader.finish()).isNull()
        assertThat(pads.drop(1).map(::leftTriggerOf)).containsExactly(LEFT.toInt(), LEFT.toInt())
    }

    @Test
    fun `a host callback partway through the pads finds no hole when the pads after it leave`() {
        (1..3).forEach(::systemPad)
        val reader = HeldReader { handler.handleRumbleTriggers(0, LEFT, RIGHT) }
        reader.holdInSecondStep()

        handler.onInputDeviceRemoved(2)
        handler.onInputDeviceRemoved(3)
        assertThat(handler.isMouseEmulationOnAnyPad).isFalse()

        assertThat(reader.finish()).isNull()
    }

    @Test
    fun `a host callback partway through the USB pads still reaches the ones that stay when the first leaves`() {
        val pads = (1..3).map(::usbPad)
        val reader = HeldReader { handler.handleRumbleTriggers(0, LEFT, RIGHT) }
        reader.holdInSecondStep()

        handler.deviceRemoved(pads[0])
        assertThat(handler.hasUsbPad()).isTrue()

        assertThat(reader.finish()).isNull()
        assertThat(pads[1].triggerRumbles).containsExactly(LEFT.toInt() to RIGHT.toInt())
        assertThat(pads[2].triggerRumbles).containsExactly(LEFT.toInt() to RIGHT.toInt())
    }

    @Test
    fun `a pad that the USB driver adds waits for the lock of the packet send`() {
        // USB pads are added from the driver's threads, one for each pad, and from the main
        // thread: the writes of the list are one at a time under the lock the packet send holds
        val inputLock = ReflectionHelpers.getField<Any>(handler, "inputLock")
        val added = RecordingUsbController(1, handler)
        val driver = Thread({ handler.deviceAdded(added) }, "usb-input")

        val state =
            synchronized(inputLock) {
                driver.start()
                settledStateOf(driver)
            }
        driver.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))

        assertThat(state).isEqualTo(Thread.State.BLOCKED)
        assertThat(handler.hasUsbPad()).isTrue()
    }

    @Test
    fun `a host callback does not wait for the lock of the packet send`() {
        // Readers take no lock: the packet send holds it while it talks to the connection
        (1..3).forEach(::systemPad)
        val inputLock = ReflectionHelpers.getField<Any>(handler, "inputLock")
        val reader = Thread({ handler.handleRumbleTriggers(0, LEFT, RIGHT) }, "host-callbacks")

        synchronized(inputLock) {
            reader.start()
            reader.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
            assertThat(reader.isAlive).isFalse()
        }
    }

    /** The state [thread] is in once it blocks or ends, or the one it is in when the wait is up. */
    private fun settledStateOf(thread: Thread): Thread.State {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (System.nanoTime() < deadline) {
            val state = thread.state
            if (state == Thread.State.BLOCKED || state == Thread.State.TERMINATED) {
                return state
            }
            Thread.sleep(POLL_MS)
        }
        return thread.state
    }

    private companion object {
        const val WAIT_SECONDS = 10L
        const val POLL_MS = 5L
        const val LEFT: Short = 0x1111
        const val RIGHT: Short = 0x2222
    }
}
