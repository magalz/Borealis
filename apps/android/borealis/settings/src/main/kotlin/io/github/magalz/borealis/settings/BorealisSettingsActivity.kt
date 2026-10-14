package io.github.magalz.borealis.settings

import android.app.Activity
import android.os.Bundle

/** Hosts the Borealis settings screen. */
class BorealisSettingsActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            fragmentManager
                .beginTransaction()
                .replace(android.R.id.content, BorealisSettingsFragment())
                .commit()
        }
    }
}
