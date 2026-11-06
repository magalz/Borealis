package io.github.magalz.borealis.input

import com.limelight.binding.input.ControllerHandler
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.feedback.RecordingUsbController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val WAIT_SECONDS = 10L

/**
 * A host callback that runs on a thread of its own, as the native connection thread runs them, held
 * when it begins the step number [holdAtStep] of its loops over the pads: the step has checked how
 * many pads there are and has not read the next one yet.
 */
class HeldReader(handler: ControllerHandler, private val holdAtStep: Int = 2, read: () -> Unit) {
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
            if (Thread.currentThread() === thread && steps.incrementAndGet() == holdAtStep) {
                held.countDown()
                check(letGo.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "The reader was not let go" }
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

/** A pad of the USB driver that has sent its first input, which gives it a number. */
fun ControllerHandler.usbPad(id: Int): RecordingUsbController =
    RecordingUsbController(id, this).also {
        deviceAdded(it)
        it.report(ControllerPacket.A_FLAG)
    }
