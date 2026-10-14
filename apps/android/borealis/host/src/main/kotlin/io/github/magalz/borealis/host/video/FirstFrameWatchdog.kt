package io.github.magalz.borealis.host.video

import java.util.concurrent.atomic.AtomicReference

/**
 * A task handed to a [WatchdogScheduler]; [cancel] is safe to call at any time and any number of
 * times.
 */
fun interface ScheduledTask {
    fun cancel()
}

/** Runs a delayed action. Production uses a main-looper `Handler`; tests use a fake clock. */
interface WatchdogScheduler {
    fun schedule(delayMillis: Long, action: Runnable): ScheduledTask
}

/**
 * Last safety net for a stream whose first video frame never shows up.
 *
 * A watchdog is single-use. It starts [State.IDLE] and ends in exactly one terminal state:
 * [State.SATISFIED] (a frame arrived), [State.FIRED] ([onTimeout] ran) or [State.CANCELLED]. Every
 * transition is a compare-and-set, so the events may come in any order and on any thread: a frame
 * reported before [arm] satisfies the watchdog for good (the later [arm] does nothing), and
 * [onTimeout] runs at most once. [onTimeout] runs on whichever thread the [scheduler] uses; the
 * production scheduler is the main looper.
 */
class FirstFrameWatchdog(
    private val scheduler: WatchdogScheduler,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val onTimeout: Runnable,
) {
    enum class State {
        IDLE,
        ARMED,
        SATISFIED,
        FIRED,
        CANCELLED,
    }

    private val current = AtomicReference(State.IDLE)
    @Volatile private var task: ScheduledTask? = null

    val state: State
        get() = current.get()

    /** Starts the countdown. A no-op unless the watchdog is still [State.IDLE]. */
    fun arm() {
        if (!current.compareAndSet(State.IDLE, State.ARMED)) return
        val scheduled = scheduler.schedule(timeoutMillis) { fire() }
        task = scheduled
        // A frame or cancel may have won the race before the task handle was stored.
        if (current.get() != State.ARMED) scheduled.cancel()
    }

    /** Reports the first rendered frame. Satisfies an idle or armed watchdog, otherwise a no-op. */
    fun onFirstFrame() = finish(State.SATISFIED)

    /** Abandons the watchdog. Final when idle or armed, otherwise a no-op. */
    fun cancel() = finish(State.CANCELLED)

    private fun finish(terminal: State) {
        while (true) {
            val before = current.get()
            if (before != State.IDLE && before != State.ARMED) return
            if (current.compareAndSet(before, terminal)) {
                task?.cancel()
                return
            }
        }
    }

    private fun fire() {
        if (current.compareAndSet(State.ARMED, State.FIRED)) onTimeout.run()
    }

    companion object {
        /**
         * Native code ends the stream after about 35 s at worst; this is the last line of defence.
         */
        const val DEFAULT_TIMEOUT_MILLIS = 45_000L
    }
}
