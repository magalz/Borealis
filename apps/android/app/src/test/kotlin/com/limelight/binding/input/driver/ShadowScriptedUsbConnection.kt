package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.rules.ExternalResource
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSystemClock

/**
 * A USB connection a test controls: the pad's IN endpoint plays a script, and everything written to
 * an OUT endpoint is recorded. Robolectric's own [UsbDeviceConnection] shadow writes every transfer
 * into a pipe, whatever the direction, so it cannot play a pad that sends reports.
 *
 * Use it with the set of shadows named in [ShadowRequestingUsbManager]. It replaces the stock
 * shadow for [UsbDeviceConnection], so `UsbManager.openDevice` hands it out too: [created] lists
 * the connections the code under test opened.
 */
@Implements(UsbDeviceConnection::class)
class ShadowScriptedUsbConnection {
    /** What the pad does on its next read. */
    sealed interface Read {
        /** A report of the pad. */
        class Report(val bytes: ByteArray) : Read

        /** A transfer that returns zero bytes. */
        data object Empty : Read

        /**
         * A transfer that fails after [elapsedMs] of the clock the code reads. An idle pad fails
         * after the 3000 ms timeout of the read, so a failure that takes a second or more is a
         * timeout, and a quicker one is a lost device.
         */
        class Failure(val elapsedMs: Long = 0) : Read
    }

    /** One transfer to an OUT endpoint. */
    class Write(val endpoint: UsbEndpoint, val data: ByteArray, val timeoutMs: Int)

    /** One claim of an interface. */
    class Claim(val usbInterface: UsbInterface, val force: Boolean)

    /** The device this connection was opened for. */
    lateinit var device: UsbDevice
        private set

    /** The reads the next transfers on an IN endpoint return, in order; an idle pad blocks. */
    val reads = LinkedBlockingQueue<Read>()

    /** The results of the next OUT transfers, in order; an empty queue means "all bytes sent". */
    val writeResults = ConcurrentLinkedQueue<Int>()

    /** The results of the next claims, in order; an empty queue means "claimed". */
    val claimResults = ConcurrentLinkedQueue<Boolean>()

    val writes = CopyOnWriteArrayList<Write>()

    /** The threads that read from an IN endpoint, in the order of their first read. */
    private val readers = CopyOnWriteArrayList<Thread>()
    private val firstRead = CountDownLatch(1)

    /** The timeout of each read of an IN endpoint, in milliseconds. */
    val readTimeouts = CopyOnWriteArrayList<Int>()
    val claims = CopyOnWriteArrayList<Claim>()
    val releases = CopyOnWriteArrayList<UsbInterface>()
    private val closeCount = AtomicInteger()

    /** How many times the code closed this connection. */
    val closes: Int
        get() = closeCount.get()

    /**
     * The thread that reads from an IN endpoint, once it has made its first read. A controller
     * reports its arrival before that read, so a test that has seen the arrival has to wait for it.
     */
    fun awaitReader(): Thread {
        check(firstRead.await(READER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "no read of the IN endpoint in $READER_TIMEOUT_SECONDS seconds"
        }
        return readers.single()
    }

    /** Queues a [Read.Report] of [bytes] (each an unsigned byte value). */
    fun report(vararg bytes: Int) {
        reads.add(Read.Report(ByteArray(bytes.size) { bytes[it].toByte() }))
    }

    @Suppress("unused")
    @Implementation
    fun __constructor__(device: UsbDevice) {
        this.device = device
        created.add(this)
        onOpen(this)
    }

    @Suppress("unused")
    @Implementation
    fun claimInterface(usbInterface: UsbInterface, force: Boolean): Boolean {
        claims.add(Claim(usbInterface, force))
        return claimResults.poll() ?: true
    }

    @Suppress("unused")
    @Implementation
    fun releaseInterface(usbInterface: UsbInterface): Boolean {
        releases.add(usbInterface)
        return true
    }

    @Suppress("unused")
    @Implementation
    fun close() {
        closeCount.incrementAndGet()
    }

    @Suppress("unused")
    @Implementation
    fun bulkTransfer(
        endpoint: UsbEndpoint,
        buffer: ByteArray,
        length: Int,
        timeout: Int,
    ): Int = transfer(endpoint, buffer, length, timeout)

    @Suppress("unused")
    @Implementation
    fun bulkTransfer(
        endpoint: UsbEndpoint,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        timeout: Int,
    ): Int {
        check(offset == 0) { "the code under test reads and writes from offset 0" }
        return transfer(endpoint, buffer, length, timeout)
    }

    // Once the connection is closed a transfer fails, as in the native code, and nothing is sent.
    private fun transfer(endpoint: UsbEndpoint, buffer: ByteArray, length: Int, timeout: Int): Int =
        if (closes > 0) {
            -1
        } else if (endpoint.direction == UsbConstants.USB_DIR_IN) {
            read(buffer, length, timeout)
        } else {
            writes.add(Write(endpoint, buffer.copyOf(length), timeout))
            writeResults.poll() ?: length
        }

    private fun read(buffer: ByteArray, length: Int, timeout: Int): Int {
        if (Thread.currentThread() !in readers) readers.add(Thread.currentThread())
        readTimeouts.add(timeout)
        firstRead.countDown()
        val next =
            try {
                reads.take()
            } catch (_: InterruptedException) {
                // The thread was interrupted while it waited for a report. A real read does not
                // end early: it fails when its timeout is over, with the thread still interrupted.
                Thread.currentThread().interrupt()
                ShadowSystemClock.advanceBy(Duration.ofMillis(timeout.toLong()))
                return -1
            }
        return when (next) {
            is Read.Report -> {
                check(next.bytes.size <= length) { "a report does not fit the read buffer" }
                next.bytes.copyInto(buffer)
                next.bytes.size
            }
            Read.Empty -> 0
            is Read.Failure -> {
                if (next.elapsedMs > 0)
                    ShadowSystemClock.advanceBy(Duration.ofMillis(next.elapsedMs))
                -1
            }
        }
    }

    companion object {
        private const val READER_TIMEOUT_SECONDS = 10L

        /** The connections opened since the last test, oldest first. */
        val created = CopyOnWriteArrayList<ShadowScriptedUsbConnection>()

        /**
         * Runs on each connection as it opens, so a test can script a connection that the code
         * under test opens itself (`UsbManager.openDevice`).
         */
        @Volatile var onOpen: (ShadowScriptedUsbConnection) -> Unit = {}

        /** Forgets what an earlier test opened or scripted. */
        fun reset() {
            created.clear()
            onOpen = {}
        }
    }
}

/**
 * Clears the connections and the script hook around each test: the shadow's statics outlive a test
 * in the Robolectric sandbox, and a hook left by one test would script the next one's connections.
 */
class ScriptedUsbReset : ExternalResource() {
    override fun before() = ShadowScriptedUsbConnection.reset()

    override fun after() = ShadowScriptedUsbConnection.reset()
}
