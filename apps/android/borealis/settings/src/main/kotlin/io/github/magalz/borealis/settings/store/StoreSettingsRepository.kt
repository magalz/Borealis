package io.github.magalz.borealis.settings.store

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.repository.SettingsRepository
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The [SettingsRepository] over a SharedPreferences backing and a DataStore backing.
 *
 * Every write goes through one queue, so the backings see the writes in the order of the calls. A
 * key with a write in flight is owned by the repository: changes that arrive from the backings for
 * that key are ignored until its last write is done, and then the key takes the value that was
 * stored (or, when the write failed, the value that is stored now). That is what keeps a read after
 * a write on the new value, and what rolls an optimistic [setNow] back.
 */
internal class StoreSettingsRepository(
    preferences: SettingsBacking,
    dataStore: SettingsBacking,
    private val scope: CoroutineScope,
    initial: SettingsSnapshot,
) : SettingsRepository {
    private class Write(val setting: Setting<*>, val value: Any?) {
        val done = CompletableDeferred<Unit>()
    }

    private val backings =
        mapOf(SettingStore.PREFS to preferences, SettingStore.DATASTORE to dataStore)
    private val state = MutableStateFlow(initial)

    // Guards [pending] and the updates of [state], and keeps the queue in the order of the calls.
    private val lock = Any()

    // The number of writes of each key that are queued or running.
    private val pending = HashMap<String, Int>()
    private val queue = Channel<Write>(Channel.UNLIMITED)

    override val snapshot: StateFlow<SettingsSnapshot> = state

    /**
     * Called with each new snapshot before [snapshot] and [observe] emit it, on the writing thread
     * and under the lock of the repository. [LogSettings] uses it to switch the redactor, so that
     * whoever reacts to the emission already sees the new masking. It must be quick and must not
     * call back into the repository.
     */
    @Volatile var beforePublish: ((SettingsSnapshot) -> Unit)? = null

    // Publishes the snapshot that [transform] makes of the current one, when it differs.
    private fun change(transform: (SettingsSnapshot) -> SettingsSnapshot) {
        synchronized(lock) {
            val current = state.value
            val next = transform(current)
            if (next != current) {
                beforePublish?.invoke(next)
                state.value = next
            }
        }
    }

    init {
        for (backing in backings.values) {
            backing.observe { stored -> applyOutsideChange(stored) }
        }
        scope.launch { for (write in queue) perform(write) }
    }

    override fun <T> observe(setting: Setting<T>): Flow<T> =
        state.map { it[setting] }.distinctUntilChanged()

    override suspend fun <T> set(setting: Setting<T>, value: T) {
        enqueue(setting, value, optimistic = false).await()
    }

    override fun <T> setNow(setting: Setting<T>, value: T) {
        enqueue(setting, value, optimistic = true)
    }

    override suspend fun clear(vararg settings: Setting<*>) {
        settings.map { enqueue(it, null, optimistic = false) }.forEach { it.await() }
    }

    private fun enqueue(
        setting: Setting<*>,
        value: Any?,
        optimistic: Boolean,
    ): CompletableDeferred<Unit> {
        val write = Write(setting, value)
        synchronized(lock) {
            pending.merge(setting.key, 1, Int::plus)
            if (optimistic) change { it.withStored(setting, value) }
            queue.trySend(write)
        }
        return write.done
    }

    // Whatever a store throws, a write never throws to its caller (UC-009): it is logged instead.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun perform(write: Write) {
        val setting = write.setting
        try {
            var failed = false
            try {
                backings.getValue(setting.store).write(setting, write.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed = true
                // The key only, never the value: a setting can hold an address or a name.
                BorealisLog.w(
                    LogTag.APP,
                    "settings write failed",
                    "key" to setting.key,
                    "error" to error.javaClass.simpleName,
                )
            }
            // A failed write leaves the key as it is stored, so read that back, away from the lock.
            val stored = if (failed) storedValueOf(setting) else StoredValue.Known(write.value)
            synchronized(lock) {
                val left = pending.getValue(setting.key) - 1
                if (left > 0) {
                    pending[setting.key] = left
                } else {
                    pending.remove(setting.key)
                    if (stored is StoredValue.Known) change { it.withStored(setting, stored.value) }
                }
            }
        } finally {
            write.done.complete(Unit)
        }
    }

    private sealed interface StoredValue {
        class Known(val value: Any?) : StoredValue

        data object Unknown : StoredValue
    }

    private suspend fun storedValueOf(setting: Setting<*>): StoredValue =
        try {
            StoredValue.Known(backings.getValue(setting.store).read()[setting])
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            StoredValue.Unknown
        }

    private fun applyOutsideChange(stored: Map<Setting<*>, Any?>) {
        synchronized(lock) {
            change { snapshot ->
                stored.entries.fold(snapshot) { acc, (setting, value) ->
                    if (pending.containsKey(setting.key)) acc else acc.withStored(setting, value)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun SettingsSnapshot.withStored(setting: Setting<*>, value: Any?): SettingsSnapshot =
        with(setting as Setting<Any?>, value)

    companion object {
        /**
         * Primes the snapshot from both backings, blocking until they are read. A backing that
         * cannot be read counts as empty and is logged: the app must still start.
         */
        fun create(
            preferences: SettingsBacking,
            dataStore: SettingsBacking,
            scope: CoroutineScope,
        ): StoreSettingsRepository {
            // fromStored refuses a null for a setting that has a default, so absent keys are left
            // out.
            val stored = runBlocking {
                readOrEmpty(preferences) + readOrEmpty(dataStore)
            }
                .filterValues { it != null }
            return StoreSettingsRepository(
                preferences,
                dataStore,
                scope,
                SettingsSnapshot.fromStored(stored),
            )
        }

        // An unreadable store must not stop the app from starting, whatever it throws.
        @Suppress("TooGenericExceptionCaught")
        private suspend fun readOrEmpty(backing: SettingsBacking): Map<Setting<*>, Any?> =
            try {
                backing.read()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                BorealisLog.w(
                    LogTag.APP,
                    "settings unreadable at start",
                    "store" to backing.store.name,
                    "error" to error.javaClass.simpleName,
                )
                emptyMap()
            }
    }
}
