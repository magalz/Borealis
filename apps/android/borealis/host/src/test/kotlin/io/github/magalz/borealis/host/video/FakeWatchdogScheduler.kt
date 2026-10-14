package io.github.magalz.borealis.host.video

/** Deterministic [WatchdogScheduler]: time only moves through [advanceBy]. */
class FakeWatchdogScheduler : WatchdogScheduler {
    private class Entry(val dueAt: Long, val action: Runnable) {
        var cancelled = false
        var ran = false
    }

    private val entries = mutableListOf<Entry>()
    private var now = 0L

    /** Number of tasks that were scheduled and neither ran nor were cancelled. */
    val pendingCount: Int
        get() = entries.count { !it.cancelled && !it.ran }

    val scheduledCount: Int
        get() = entries.size

    override fun schedule(delayMillis: Long, action: Runnable): ScheduledTask {
        val entry = Entry(now + delayMillis, action)
        entries += entry
        return ScheduledTask { entry.cancelled = true }
    }

    fun advanceBy(millis: Long) {
        now += millis
        for (entry in entries.toList()) {
            if (!entry.cancelled && !entry.ran && entry.dueAt <= now) {
                entry.ran = true
                entry.action.run()
            }
        }
    }
}
