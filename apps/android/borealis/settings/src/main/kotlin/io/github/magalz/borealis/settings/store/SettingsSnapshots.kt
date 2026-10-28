package io.github.magalz.borealis.settings.store

import android.content.Context
import android.preference.PreferenceManager
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.magalz.borealis.settings.repository.SettingsRepository
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The one settings repository of the process, and the synchronous way into its snapshot for code
 * that Hilt does not reach (`Application.onCreate`, the stream activity).
 *
 * The first call builds the repository and primes the snapshot, blocking while it reads both stores
 * once. It is safe to call before `Application.onCreate`, from any thread. After that, [current] is
 * a read of memory and never blocks.
 */
object SettingsSnapshots {
    private class Holder(
        val application: Context,
        val scope: CoroutineScope,
        val repository: SettingsRepository,
    )

    @Volatile private var holder: Holder? = null

    /** The snapshot now. Primes the repository when it is the first call. */
    @JvmStatic fun current(context: Context): SettingsSnapshot = repository(context).snapshot.value

    /** The repository of the process; the same instance on every call. */
    @JvmStatic
    fun repository(context: Context): SettingsRepository {
        val application: Context? = context.applicationContext
        holder
            ?.takeIf { application == null || it.application === application }
            ?.let {
                return it.repository
            }
        return synchronized(this) {
            val current = holder
            if (current != null && (application == null || current.application === application)) {
                current.repository
            } else {
                // Only a test starts a second application in one process: the old one goes.
                current?.scope?.cancel()
                build(application ?: context).also { holder = it }.repository
            }
        }
    }

    // PreferenceManager is deprecated; its default file is where the inherited settings live.
    @Suppress("DEPRECATION")
    private fun build(application: Context): Holder {
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        LegacyMigrations.run(preferences)
        @Suppress("InjectDispatcher") // The holder is where the production dispatcher is chosen.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore =
            DataStoreBacking.create(
                preferences,
                scope,
                application.preferencesDataStoreFile(DataStoreBacking.FILE_NAME),
            )
        val repository =
            StoreSettingsRepository.create(
                PrefsBacking(preferences),
                DataStoreBacking(dataStore, scope),
                scope,
            )
        return Holder(application, scope, repository)
    }
}
