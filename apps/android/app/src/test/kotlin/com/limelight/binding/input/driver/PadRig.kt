package com.limelight.binding.input.driver

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import java.lang.management.ManagementFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.rules.ExternalResource

/** What a driver told its [UsbDriverListener], in order. */
internal sealed interface PadEvent {
    class Added(val controller: AbstractController) : PadEvent

    class Removed(val controller: AbstractController) : PadEvent

    class State(
        val controllerId: Int,
        val buttonFlags: Int,
        val leftStickX: Float,
        val leftStickY: Float,
        val rightStickX: Float,
        val rightStickY: Float,
        val leftTrigger: Float,
        val rightTrigger: Float,
    ) : PadEvent
}

/**
 * A listener that keeps what it hears. Controllers report from their input thread, so the events
 * wait in a queue the test reads with a timeout. The queue is filled with `add`, which does not
 * check the interrupt flag: a controller that stops itself reports its removal interrupted.
 */
internal class RecordingListener : UsbDriverListener {
    private val queue = LinkedBlockingQueue<PadEvent>()

    override fun reportControllerState(
        controllerId: Int,
        buttonFlags: Int,
        leftStickX: Float,
        leftStickY: Float,
        rightStickX: Float,
        rightStickY: Float,
        leftTrigger: Float,
        rightTrigger: Float,
    ) {
        queue.add(
            PadEvent.State(
                controllerId,
                buttonFlags,
                leftStickX,
                leftStickY,
                rightStickX,
                rightStickY,
                leftTrigger,
                rightTrigger,
            )
        )
    }

    override fun deviceRemoved(controller: AbstractController) {
        queue.add(PadEvent.Removed(controller))
    }

    override fun deviceAdded(controller: AbstractController) {
        queue.add(PadEvent.Added(controller))
    }

    /** The next event, which may take the input thread's first second. */
    fun next(): PadEvent =
        queue.poll(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            ?: error("no event in $EVENT_TIMEOUT_SECONDS seconds")

    /** The next [n] events that are reports of the pad, after the arrival. */
    fun states(n: Int): List<PadEvent.State> {
        val added = next()
        check(added is PadEvent.Added) { "the first event is the arrival, not $added" }
        return List(n) {
            val event = next()
            check(event is PadEvent.State) { "expected a report, got $event" }
            event
        }
    }

    /** Every event heard so far, without waiting. */
    fun drain(): List<PadEvent> = generateSequence { queue.poll() }.toList()

    /** True when nothing arrives within [millis]; the caller wants a quiet period. */
    fun quietFor(millis: Long): Boolean = queue.poll(millis, TimeUnit.MILLISECONDS) == null

    private companion object {
        const val EVENT_TIMEOUT_SECONDS = 10L
    }
}

/**
 * The value of a public getter of [AbstractController] as a Java caller (the controller handler)
 * reads it. Kotlin code of this package reads the protected field of the same name first, so a
 * plain `controller.type` would not go through the getter.
 */
internal fun AbstractController.viaGetter(name: String): Any? =
    AbstractController::class.java.getMethod(name).invoke(this)

/**
 * Milliseconds of the JVM's own clock. Robolectric makes System.nanoTime and currentTimeMillis
 * follow the fake looper clock, which does not run while a test waits for a real thread.
 */
internal fun realMillis(): Long = ManagementFactory.getRuntimeMXBean().uptime

/** A controller on a connection a test controls, with the listener that hears it. */
internal class Pad<C : AbstractController>(
    val device: UsbDevice,
    build: (UsbDevice, UsbDeviceConnection, UsbDriverListener) -> C,
) {
    val listener = RecordingListener()
    val connection: UsbDeviceConnection = UsbPads.connection(device)
    val usb = UsbPads.scripted(connection)
    val controller: C = build(device, connection, listener)

    /** Whether [start] succeeded: then the input thread runs and the test must stop it. */
    var running = false
        private set

    fun start(): Boolean = controller.start().also { running = it }

    /** The bytes the pad received on its OUT endpoint, as unsigned values. */
    fun writtenBytes(): List<List<Int>> =
        usb.writes.map { write -> write.data.map { it.toInt() and 0xFF } }

    /** Stops what [start] started, if it is not stopped yet. */
    fun stopIfRunning() {
        if (running) controller.stop()
    }
}

/** What `LimeLog` wrote during a test, with the level of each line. */
class LimeLogCapture : ExternalResource() {
    private val logger = Logger.getLogger("com.limelight.LimeLog")
    private val lines = CopyOnWriteArrayList<Pair<Level, String>>()
    private val handler =
        object : Handler() {
            override fun publish(record: LogRecord) {
                lines.add(record.level to record.message)
            }

            override fun flush() = Unit

            override fun close() = Unit
        }

    val entries: List<Pair<Level, String>>
        get() = lines.toList()

    fun warnings(): List<String> = entries.filter { it.first == Level.WARNING }.map { it.second }

    fun severes(): List<String> = entries.filter { it.first == Level.SEVERE }.map { it.second }

    override fun before() = logger.addHandler(handler)

    override fun after() = logger.removeHandler(handler)
}

/**
 * Fails a test when a thread died of an exception that nobody caught. The input thread of a
 * controller has nobody to tell: without this, a read that goes wrong ends the thread and the test
 * only sees that no more reports arrive.
 */
class NoUncaughtThreadExceptions : ExternalResource() {
    private val errors = CopyOnWriteArrayList<Throwable>()
    private var previous: Thread.UncaughtExceptionHandler? = null

    override fun before() {
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> errors.add(error) }
    }

    override fun after() {
        Thread.setDefaultUncaughtExceptionHandler(previous)
        check(errors.isEmpty()) { "a thread died of an exception: ${errors.first()}" }
    }
}
