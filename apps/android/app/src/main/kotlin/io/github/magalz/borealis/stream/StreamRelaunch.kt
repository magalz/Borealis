package io.github.magalz.borealis.stream

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

/**
 * Starts a stream again after its activity is gone. `Game` is `singleTask` and `noHistory` and
 * finishes in `onStop`, so `recreate()` does not bring it back, and starting it while the old one
 * is still alive would only deliver a new intent to the old one. The Application outlives the
 * activity, so it starts the saved intent once the activity is destroyed.
 */
object StreamRelaunch {
    /**
     * Starts a copy of [intent] from the Application after [activity] is destroyed. Nothing starts
     * when the destroy is only a recreation, that is, when [activity] is not finishing. The caller
     * finishes the activity.
     */
    fun afterDestroy(activity: Activity, intent: Intent) {
        val application = activity.application
        val saved = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityDestroyed(destroyed: Activity) {
                    if (destroyed !== activity) return
                    application.unregisterActivityLifecycleCallbacks(this)
                    if (destroyed.isFinishing) application.startActivity(saved)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
                    Unit
            }
        )
    }
}
