package io.github.magalz.borealis.settings

import android.content.Context
import android.content.Intent
import android.preference.Preference
import android.util.AttributeSet

/**
 * Entry in the upstream settings that opens the Borealis settings screen. Referenced by class name
 * from `preferences.xml`.
 */
@Suppress("DEPRECATION")
class BorealisSettingsPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    public override fun onClick() {
        context.startActivity(Intent(context, BorealisSettingsActivity::class.java))
    }
}
