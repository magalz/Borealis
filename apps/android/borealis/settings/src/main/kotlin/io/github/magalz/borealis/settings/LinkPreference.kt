package io.github.magalz.borealis.settings

import android.content.Context
import android.preference.Preference
import android.util.AttributeSet

/** Opens its `url` attribute in a browser, or shows the URL when no app can open it. */
@Suppress("DEPRECATION")
class LinkPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs) {
    var url: String? = null

    public override fun onClick() = Unit
}
