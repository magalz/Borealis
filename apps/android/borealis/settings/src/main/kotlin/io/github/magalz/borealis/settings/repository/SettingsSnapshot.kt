package io.github.magalz.borealis.settings.repository

import io.github.magalz.borealis.settings.DefaultBitrate
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings

/**
 * An immutable, typed view of every setting at one moment. A read is synchronous and never fails: a
 * key that is absent from the store gives the [Setting.default] of its setting.
 *
 * Two snapshots are equal when they give the same value for every setting, so for equality a value
 * equal to its default is the same as an absent key. That rule belongs to the snapshot only: the
 * store keeps whatever [SettingsRepository.set] wrote. The bitrate is the one setting with no
 * default: [Settings.BITRATE_KBPS] reads as `null` while it is automatic.
 */
class SettingsSnapshot private constructor(private val values: Map<String, Any>) {
    /** The value of [setting], or its default when the key is absent. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(setting: Setting<T>): T = (values[setting.key] as T?) ?: setting.default

    /**
     * A copy with [setting] set to [value]. A [value] that equals the default, and a `null` for
     * [Settings.BITRATE_KBPS], leave the key absent.
     */
    fun <T> with(setting: Setting<T>, value: T): SettingsSnapshot =
        if (value == null || value == setting.default) {
            without(setting)
        } else {
            SettingsSnapshot(values + (setting.key to value as Any))
        }

    /** A copy with the keys of [settings] absent, so that they read as their defaults. */
    fun without(vararg settings: Setting<*>): SettingsSnapshot =
        SettingsSnapshot(values - settings.map { it.key }.toSet())

    /**
     * Whether the bitrate is automatic: no bitrate is stored, or the stored one is 0 (the inherited
     * code treats 0 as unset).
     */
    val isBitrateAutomatic: Boolean
        get() = (this[Settings.BITRATE_KBPS] ?: 0) == 0

    /**
     * The bitrate that a stream would use, in kbps: the stored one, or the automatic bitrate
     * computed from the resolution and the frame rate when it is [isBitrateAutomatic]. Throws
     * [NumberFormatException] when the stored resolution or frame rate is not a number, as the
     * inherited code does.
     *
     * The legacy `seekbar_bitrate` key (in Mbps, so a value times 1000) that the inherited code
     * still migrates is not read here. Moving it to [Settings.BITRATE_KBPS] is a migration of the
     * storage backing.
     */
    fun effectiveBitrateKbps(): Int =
        if (isBitrateAutomatic) {
            DefaultBitrate.kbps(this[Settings.RESOLUTION], this[Settings.FRAME_RATE])
        } else {
            checkNotNull(this[Settings.BITRATE_KBPS])
        }

    override fun equals(other: Any?): Boolean = other is SettingsSnapshot && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "SettingsSnapshot($values)"

    companion object {
        /** The snapshot of an empty store: every setting reads as its default. */
        val DEFAULTS: SettingsSnapshot = SettingsSnapshot(emptyMap())

        /**
         * Builds a snapshot from raw stored [values], for a storage backing that reads each key by
         * its setting type. A `null` value is skipped. Throws [IllegalArgumentException] when a
         * value is not of the type of its setting.
         */
        fun fromStored(values: Map<Setting<*>, Any?>): SettingsSnapshot {
            var snapshot = DEFAULTS
            for ((setting, value) in values) {
                require(setting.accepts(value)) { "$setting does not store $value" }
                @Suppress("UNCHECKED_CAST")
                snapshot = snapshot.with(setting as Setting<Any?>, value)
            }
            return snapshot
        }
    }
}
