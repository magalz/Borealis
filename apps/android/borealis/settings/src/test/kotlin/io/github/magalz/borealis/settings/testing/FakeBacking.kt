package io.github.magalz.borealis.settings.testing

import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.store.SettingsBacking
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred

/**
 * A [SettingsBacking] in memory. A write waits for [gate] when there is one, then fails when
 * [failWrites] is set, and otherwise lands in [stored] and is recorded in [writes].
 */
internal class FakeBacking(override val store: SettingStore = SettingStore.PREFS) :
    SettingsBacking {
    val stored = ConcurrentHashMap<Setting<*>, Any>()
    val writes = CopyOnWriteArrayList<Pair<Setting<*>, Any?>>()

    @Volatile var failWrites = false
    @Volatile var failReads = false
    @Volatile var gate: CompletableDeferred<Unit>? = null

    /** Runs right after a write has landed in [stored], as a write from outside could. */
    @Volatile var onWritten: (() -> Unit)? = null
    private var listener: ((Map<Setting<*>, Any?>) -> Unit)? = null

    override suspend fun read(): Map<Setting<*>, Any?> {
        if (failReads) throw IOException("read-detail")
        return values()
    }

    override suspend fun write(setting: Setting<*>, value: Any?) {
        gate?.await()
        if (failWrites) throw IOException("disk full")
        writes += setting to value
        if (value == null) stored.remove(setting) else stored[setting] = value
        listener?.invoke(values())
        onWritten?.invoke()
    }

    override fun observe(onChanged: (Map<Setting<*>, Any?>) -> Unit) {
        listener = onChanged
    }

    /** A change that did not come from the repository. */
    fun changeElsewhere(setting: Setting<*>, value: Any?) {
        if (value == null) stored.remove(setting) else stored[setting] = value
        listener?.invoke(values())
    }

    private fun values(): Map<Setting<*>, Any?> =
        Settings.all.filter { it.store == store }.associateWith { stored[it] }
}
