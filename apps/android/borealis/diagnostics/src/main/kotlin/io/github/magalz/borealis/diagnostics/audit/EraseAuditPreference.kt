package io.github.magalz.borealis.diagnostics.audit

import android.content.Context
import android.preference.Preference
import android.util.AttributeSet

@Suppress("DEPRECATION")
class EraseAuditPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    var eraser: (suspend () -> Unit)? = null

    public override fun onClick() {}
}
