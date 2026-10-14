package io.github.magalz.borealis.host.video

/** A task handed to a [WatchdogScheduler]; [cancel] is safe to call at any time and any number of times. */
fun interface ScheduledTask {
    fun cancel()
}

/** Runs a delayed action. Production uses a main-looper `Handler`; tests use a fake clock. */
interface WatchdogScheduler {
    fun schedule(delayMillis: Long, action: Runnable): ScheduledTask
}

/** Stub, replaced by the implementation commit. */
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

    val state: State
        get() = State.IDLE

    fun arm() = Unit

    fun onFirstFrame() = Unit

    fun cancel() = Unit

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 45_000L
    }
}
