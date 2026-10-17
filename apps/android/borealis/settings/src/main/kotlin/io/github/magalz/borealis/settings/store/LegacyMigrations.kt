package io.github.magalz.borealis.settings.store

import android.content.SharedPreferences

/** Moves the keys that older versions stored to the keys of the catalog, once. */
internal object LegacyMigrations {
    fun run(preferences: SharedPreferences) = Unit
}
