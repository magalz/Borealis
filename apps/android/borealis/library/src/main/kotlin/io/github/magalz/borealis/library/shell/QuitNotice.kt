package io.github.magalz.borealis.library.shell

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The "App quit." notice that a Quit in the stream leaves for the Library. */
@Singleton
class QuitNotice @Inject constructor() {
    /** Leaves the notice. */
    fun post() = Unit

    /** The notices to show. */
    val notices: Flow<Unit> = flow { awaitCancellation() }

    /** A Library is there to show the notices. */
    fun attach() = Unit

    /** That Library is gone. */
    fun detach() = Unit
}
