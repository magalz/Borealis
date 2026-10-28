package io.github.magalz.borealis.library.shell

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** What the stream leaves for the Library when the user leaves it: the notice the Library shows. */
sealed interface LeaveNotice {
    /** The app was quit on the PC: the "App quit." that a Quit from the Apps sheet shows. */
    data object Quit : LeaveNotice

    /** The user disconnected and the app keeps running on the PC; [appName] is blank if unknown. */
    data class Disconnected(val appName: String) : LeaveNotice
}

/**
 * The notice that leaving the stream leaves for the Library: "App quit." after a Quit, which a Quit
 * from the Apps sheet shows too, or that the app is still running after a Disconnect. The stream
 * [post]s it, and the Library, which is behind the stream and comes to the front when it ends,
 * shows it once with the host of its shell.
 *
 * A notice is for a Library that is there: it is dropped when none is, and when the Library that it
 * waits for goes before it was shown. Without that, a stream that nothing in the Library started (a
 * launcher shortcut) would leave a notice for a later visit to the Library. A notice that comes
 * while the Library is stopped waits until it is in front again.
 *
 * Everything here runs on the main thread: the stream's actions, and the Library's composition.
 */
@Singleton
class QuitNotice @Inject constructor() {
    private var libraries = 0
    private val waiting = Channel<LeaveNotice>(Channel.CONFLATED)

    /** The notices to show, each delivered once, to one collector at a time. */
    val notices: Flow<LeaveNotice> = waiting.receiveAsFlow()

    /** Leaves [notice], if a Library is there to show it. */
    fun post(notice: LeaveNotice) {
        if (libraries > 0) waiting.trySend(notice)
    }

    /** A Library is there to show the notices; pair it with [detach]. */
    fun attach() {
        libraries++
    }

    /** A Library is gone; the last one takes the notice that waits for it. */
    fun detach() {
        libraries--
        if (libraries == 0) waiting.tryReceive()
    }
}
