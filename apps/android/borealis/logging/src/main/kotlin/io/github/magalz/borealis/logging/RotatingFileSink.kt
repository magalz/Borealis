package io.github.magalz.borealis.logging

import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Appends one UTF-8 line per record to `<directory>/borealis.log`. When a record would push the
 * file past [maxFileBytes] it is rotated to `borealis.log.1` (older files shift up) and at most
 * [maxFiles] files are kept in total, the oldest being deleted.
 *
 * [write] only enqueues the record; a single daemon thread formats it, writes it and flushes the
 * file, so a caller (for example the video thread through the native log forwarder) never waits for
 * the disk. The queue holds at most [queueCapacity] records. When it is full, new records are
 * dropped and counted, and a `N records dropped` line is queued as soon as there is room (or at the
 * next [flush] or [close]). Records are written in the order they were accepted. Call [flush] (or
 * [flushAll]) before the process dies to get the queued records onto the disk. Close the sink when
 * done: its writer thread otherwise lives as long as the process.
 *
 * The `N records dropped` line is queued only when the queue has room. If it is still full when
 * [flush] or [close] runs, it completes without the line: [flush] keeps the count for a later call,
 * [close] loses it.
 *
 * [writeGate] runs on the writer thread before every record and exists for tests only.
 */
class RotatingFileSink(
    private val directory: File,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val writeGate: () -> Unit = {},
) : LogSink {
    private val queue = ArrayBlockingQueue<LogRecord>(queueCapacity)

    // Guards the counters, the closed flag and the writer start. Producers offer to the queue under
    // it, so the counters match the queue order.
    private val stateLock = ReentrantLock()
    private val progress = stateLock.newCondition()
    private var enqueued = 0L
    private var processed = 0L
    private var dropped = 0
    private var closed = false
    private var writer: Thread? = null

    // Touched only by the writer thread.
    private val file = RotatingLogFile(directory, maxFiles, maxFileBytes)

    init {
        require(maxFiles >= 1) { "maxFiles must be at least 1" }
        require(maxFileBytes > 0) { "maxFileBytes must be positive" }
        require(queueCapacity >= 1) { "queueCapacity must be at least 1" }
        synchronized(openSinks) { openSinks.add(this) }
    }

    override fun write(record: LogRecord) {
        stateLock.withLock {
            if (closed) return
            startWriter()
            queueDropSummary(record.timestampMillis)
            if (queue.offer(record)) enqueued++ else dropped++
        }
    }

    /**
     * Waits up to [timeoutMillis] until every record accepted so far is on the disk. Returns false
     * on timeout (for example while the disk is stalled).
     */
    fun flush(timeoutMillis: Long = DEFAULT_FLUSH_TIMEOUT_MILLIS): Boolean {
        var remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        stateLock.withLock {
            if (!closed) queueDropSummary(System.currentTimeMillis())
            val target = enqueued
            while (processed < target) {
                if (remaining <= 0L) return false
                remaining = progress.awaitNanos(remaining)
            }
        }
        return true
    }

    /** Drains the queue and stops the writer; later records are ignored. Idempotent. */
    override fun close() {
        val thread: Thread?
        stateLock.withLock {
            if (closed) return
            queueDropSummary(System.currentTimeMillis())
            closed = true
            thread = writer
        }
        synchronized(openSinks) { openSinks.remove(this) }
        if (thread == null) return
        try {
            if (queue.offer(STOP, CLOSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                thread.join(CLOSE_TIMEOUT_MILLIS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // Callers hold stateLock.
    private fun startWriter() {
        if (writer != null) return
        writer =
            Thread(::runWriter, WRITER_THREAD_NAME).apply {
                isDaemon = true
                start()
            }
    }

    // Callers hold stateLock.
    private fun queueDropSummary(timestampMillis: Long) {
        if (dropped == 0) return
        val summary =
            LogRecord(timestampMillis, LogLevel.WARN, LogTag.APP, "$dropped records dropped")
        if (queue.offer(summary)) {
            enqueued++
            dropped = 0
        }
    }

    private fun runWriter() {
        try {
            while (true) {
                val record = queue.take()
                if (record === STOP) break
                runCatching {
                    writeGate()
                    file.append((LogFormatter.format(record) + "\n").toByteArray(Charsets.UTF_8))
                }
                stateLock.withLock {
                    processed++
                    progress.signalAll()
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            file.close()
        }
    }

    companion object {
        const val FILE_NAME = "borealis.log"
        const val DEFAULT_MAX_FILES = 5
        const val DEFAULT_MAX_FILE_BYTES = 1024L * 1024L

        /** Roughly 150 KB of ordinary records; larger bursts than this are dropped and counted. */
        const val DEFAULT_QUEUE_CAPACITY = 1024
        const val DEFAULT_FLUSH_TIMEOUT_MILLIS = 2_000L
        private const val CLOSE_TIMEOUT_MILLIS = 2_000L
        private const val WRITER_THREAD_NAME = "borealis-log-writer"

        private val STOP = LogRecord(0L, LogLevel.VERBOSE, LogTag.APP, "")

        // Open sinks, for flushAll. Weakly held so the registry itself pins nothing, but the writer
        // thread of a sink that was never closed keeps that sink alive: callers must close().
        private val openSinks: MutableSet<RotatingFileSink> =
            Collections.newSetFromMap(WeakHashMap())

        /**
         * Flushes every open sink within one shared deadline of [timeoutMillis]. For the crash
         * handler, which cannot reach the sink instances that the application created.
         */
        fun flushAll(timeoutMillis: Long = DEFAULT_FLUSH_TIMEOUT_MILLIS) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            val sinks = synchronized(openSinks) { openSinks.toList() }
            for (sink in sinks) {
                val left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                sink.flush(left.coerceAtLeast(0L))
            }
        }
    }
}
