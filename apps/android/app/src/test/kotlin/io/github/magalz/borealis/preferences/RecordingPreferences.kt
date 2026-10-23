package io.github.magalz.borealis.preferences

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * A context whose SharedPreferences files write what is done to them into [log]. The log pins what
 * the inherited preferences code writes while it reads: the keys, the values, their order, and
 * whether it applies the edit or commits it.
 *
 * Each editor is logged as `edit`, its operations (`remove(key)`, `putString(key=value)`, and so
 * on), then `apply()` or `commit()`. [commitResult] is what every `commit()` answers; the edit
 * itself still reaches the real file.
 *
 * `PreferenceManager.getDefaultSharedPreferences` asks its context for the file, so this context
 * sees both the default file and `GlPreferences`. Each request is added to [files] as the name and
 * the mode that were asked for.
 */
internal class RecordingContext(
    base: Context,
    val log: MutableList<String> = mutableListOf(),
    private val commitResult: Boolean = true,
) : ContextWrapper(base) {
    val files = mutableListOf<Pair<String, Int>>()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        files += name to mode
        return RecordingPreferences(super.getSharedPreferences(name, mode), log, commitResult)
    }
}

internal class RecordingPreferences(
    private val delegate: SharedPreferences,
    private val log: MutableList<String>,
    private val commitResult: Boolean,
) : SharedPreferences by delegate {
    override fun edit(): SharedPreferences.Editor {
        log += "edit"
        return RecordingEditor(delegate.edit(), log, commitResult)
    }
}

@Suppress("TooManyFunctions")
internal class RecordingEditor(
    private val delegate: SharedPreferences.Editor,
    private val log: MutableList<String>,
    private val commitResult: Boolean,
) : SharedPreferences.Editor {
    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        log += "putString($key=$value)"
        delegate.putString(key, value)
        return this
    }

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
        log += "putStringSet($key=$values)"
        delegate.putStringSet(key, values)
        return this
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        log += "putInt($key=$value)"
        delegate.putInt(key, value)
        return this
    }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        log += "putLong($key=$value)"
        delegate.putLong(key, value)
        return this
    }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        log += "putFloat($key=$value)"
        delegate.putFloat(key, value)
        return this
    }

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        log += "putBoolean($key=$value)"
        delegate.putBoolean(key, value)
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
        log += "remove($key)"
        delegate.remove(key)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        log += "clear()"
        delegate.clear()
        return this
    }

    override fun commit(): Boolean {
        log += "commit()"
        delegate.commit()
        return commitResult
    }

    override fun apply() {
        log += "apply()"
        delegate.apply()
    }
}
