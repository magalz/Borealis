package io.github.magalz.borealis.settings.catalog

/** Where the value of a [Setting] is stored (ADR 0055). */
enum class SettingStore {
    /** The default SharedPreferences file, read synchronously by the inherited code. */
    PREFS,

    /** The `borealis_settings` DataStore file; read once, blocking, at app start. */
    DATASTORE,
}

/**
 * One setting of the catalog: its stored [key], the [default] that applies while the key is absent,
 * and the [store] that holds it.
 *
 * The subclass says how upstream stores the key: a switch is a [BooleanSetting], a list is a
 * [StringSetting] (even when its values look like numbers) and a slider is an [IntSetting]. The
 * hierarchy is sealed so that a storage backing can switch over it exhaustively.
 *
 * Settings are compared by identity: use the entries of [Settings].
 */
sealed class Setting<T>(val key: String, val default: T, val store: SettingStore) {
    /**
     * Whether [value] is of the type that this setting stores. A `null` is accepted only by an
     * [OptionalIntSetting].
     */
    abstract fun accepts(value: Any?): Boolean

    override fun toString(): String = "Setting($key)"
}

/** A setting stored as a Boolean: a switch. */
class BooleanSetting(key: String, default: Boolean, store: SettingStore = SettingStore.PREFS) :
    Setting<Boolean>(key, default, store) {
    override fun accepts(value: Any?): Boolean = value is Boolean
}

/** A setting stored as an Int: a slider. */
class IntSetting(key: String, default: Int, store: SettingStore = SettingStore.PREFS) :
    Setting<Int>(key, default, store) {
    override fun accepts(value: Any?): Boolean = value is Int
}

/** A setting stored as a String: a list. */
class StringSetting(key: String, default: String, store: SettingStore = SettingStore.PREFS) :
    Setting<String>(key, default, store) {
    override fun accepts(value: Any?): Boolean = value is String
}

/**
 * An Int setting with no default: while the key is absent its value is `null`, which means
 * "automatic". Only the bitrate is one. Writing `null` removes the key.
 */
class OptionalIntSetting(key: String, store: SettingStore = SettingStore.PREFS) :
    Setting<Int?>(key, null, store) {
    override fun accepts(value: Any?): Boolean = value == null || value is Int
}
