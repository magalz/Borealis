package io.github.magalz.borealis.library.testing

import android.app.Activity
import io.github.magalz.borealis.library.domain.StreamLauncher
import io.github.magalz.borealis.library.domain.StreamRequest

class FakeStreamLauncher : StreamLauncher {
    val requests = mutableListOf<StreamRequest>()

    /** What [start] answers: false means the host is no longer known. */
    var known = true

    override fun start(activity: Activity, request: StreamRequest): Boolean {
        requests += request
        return known
    }
}
