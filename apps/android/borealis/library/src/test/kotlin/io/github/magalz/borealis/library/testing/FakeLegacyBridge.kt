package io.github.magalz.borealis.library.testing

import android.app.Activity
import android.view.ViewGroup
import io.github.magalz.borealis.library.domain.LegacyBridge

class FakeLegacyBridge : LegacyBridge {
    /** For each [onLauncherCreated] call: whether the activity already had its content. */
    val contentSetAtCreate = mutableListOf<Boolean>()
    var resumed = 0
        private set

    override fun onLauncherCreated(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        contentSetAtCreate += content.childCount > 0
    }

    override fun onLauncherResumed(activity: Activity) {
        resumed++
    }
}
