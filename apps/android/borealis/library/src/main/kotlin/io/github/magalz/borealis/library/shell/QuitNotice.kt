package io.github.magalz.borealis.library.shell

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The notice that a Quit in the stream leaves for the Library: the same "App quit." that a Quit
 * from the Apps sheet shows. The stream [post]s it, and the Library, which is behind the stream and
 * comes to the front when it ends, shows it once with the host of its shell.
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
    private val waiting = Channel<Unit>(Channel.CONFLATED)

    /** The notices to show, each delivered once, to one collector at a time. */
    val notices: Flow<Unit> = waiting.receiveAsFlow()

    /** Leaves the notice, if a Library is there to show it. */
    fun post() {
        if (libraries > 0) waiting.trySend(Unit)
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
