package io.github.magalz.borealis.library

import android.app.Activity
import io.github.magalz.borealis.library.domain.StreamLauncher
import io.github.magalz.borealis.library.domain.StreamRequest
import javax.inject.Inject
import javax.inject.Singleton

/** [StreamLauncher] over `ServerHelper.doStart`, which builds the intent of the stream activity. */
@Singleton
internal class GameStreamLauncher
@Inject
constructor(private val connection: ComputerManagerConnection) : StreamLauncher {
    override fun start(activity: Activity, request: StreamRequest): Boolean = TODO()
}
