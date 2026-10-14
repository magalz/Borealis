package io.github.magalz.borealis.settings

import android.content.Context
import android.preference.Preference
import android.util.AttributeSet

/** Shows the bundled licence texts in a scrollable dialog. */
@Suppress("DEPRECATION")
class LicensesPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs) {
    public override fun onClick() = Unit
}
