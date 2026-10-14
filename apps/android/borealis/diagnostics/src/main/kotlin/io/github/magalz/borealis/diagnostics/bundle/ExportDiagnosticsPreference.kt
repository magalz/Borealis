package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.preference.Preference
import android.util.AttributeSet
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Settings entry that builds the diagnostic bundle and shares it, or saves it where a TV user can
 * fetch it with `adb`. Referenced by class name from the Borealis preferences XML.
 */
@Suppress("DEPRECATION")
class ExportDiagnosticsPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    internal var sectionsProvider: () -> Set<DiagnosticSection> = { emptySet() }
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    public override fun onClick(): Unit = TODO()
}
