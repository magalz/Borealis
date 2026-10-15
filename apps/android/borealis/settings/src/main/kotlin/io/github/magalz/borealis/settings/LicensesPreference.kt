package io.github.magalz.borealis.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.preference.Preference
import android.util.AttributeSet
import android.widget.ScrollView
import android.widget.TextView

/**
 * Shows the bundled `NOTICE` and `LICENSE` in a scrollable dialog, so they are readable without a
 * browser. The text can take focus, so a D-pad scrolls it.
 */
@Suppress("DEPRECATION")
class LicensesPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs) {
    public override fun onClick() {
        val text =
            LegalTexts.load { context.assets.open(it) }
                ?: context.getString(R.string.borealis_licenses_unavailable)
        val padding = (PADDING_DP * context.resources.displayMetrics.density).toInt()
        val body =
            TextView(context).apply {
                this.text = text
                typeface = Typeface.MONOSPACE
                textSize = TEXT_SIZE_SP
                isFocusable = true
                setPadding(padding, padding, padding, padding)
            }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton(R.string.borealis_close, null)
            .show()
    }

    private companion object {
        const val PADDING_DP = 16
        const val TEXT_SIZE_SP = 12f
    }
}
