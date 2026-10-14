package io.github.magalz.borealis.host.video

import android.os.Handler
import android.os.Looper

/** Production [WatchdogScheduler]: runs the action on the looper of [handler] (the main looper). */
class HandlerWatchdogScheduler(private val handler: Handler = Handler(Looper.getMainLooper())) :
    WatchdogScheduler {
    override fun schedule(delayMillis: Long, action: Runnable): ScheduledTask {
        handler.postDelayed(action, delayMillis)
        return ScheduledTask { handler.removeCallbacks(action) }
    }
}
