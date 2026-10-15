package io.github.magalz.borealis.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The apps the user hid, in the file and format of the 12.2 app list (`AppView`): one string set of
 * app ids per host id, so both lists agree.
 */
@Singleton
internal class HiddenApps @Inject constructor(@ApplicationContext private val context: Context) {
    private val changes = MutableStateFlow(0)

    /** Changes whenever this class writes, so a reader knows to read again. */
    val revision: StateFlow<Int> = changes

    private val prefs
        get() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(hostId: String): Set<Int> =
        prefs.getStringSet(hostId, null).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    @Synchronized
    fun set(hostId: String, appId: Int, hidden: Boolean) {
        val ids = if (hidden) read(hostId) + appId else read(hostId) - appId
        prefs.edit().putStringSet(hostId, ids.mapTo(HashSet()) { it.toString() }).apply()
        changes.update { it + 1 }
    }

    /** Forgets the hidden apps of a host that was removed. */
    @Synchronized
    fun clear(hostId: String) {
        prefs.edit().remove(hostId).apply()
        changes.update { it + 1 }
    }

    private companion object {
        /** `AppView.HIDDEN_APPS_PREF_FILENAME`. */
        const val FILE = "HiddenApps"
    }
}
