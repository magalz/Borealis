package io.github.magalz.borealis.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The apps the user hid, in the file and format of the 12.2 app list (`AppView`): one string set of
 * app ids per host id, so both lists agree.
 */
@Singleton
internal class HiddenApps @Inject constructor(@ApplicationContext private val context: Context) {
    private val changes = MutableStateFlow(0)

    /** Changes whenever this class writes, so a reader knows to read again. */
    val revision: StateFlow<Int> = changes

    fun read(hostId: String): Set<Int> = TODO()

    fun set(hostId: String, appId: Int, hidden: Boolean): Unit = TODO()

    fun clear(hostId: String): Unit = TODO()
}
