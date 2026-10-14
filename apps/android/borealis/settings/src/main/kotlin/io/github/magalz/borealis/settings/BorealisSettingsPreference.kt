package io.github.magalz.borealis.settings

import android.content.Context
import android.preference.Preference
import android.util.AttributeSet

/** Entry in the upstream settings that opens the Borealis settings screen. */
@Suppress("DEPRECATION")
class BorealisSettingsPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    public override fun onClick() = Unit
}
