package io.github.magalz.borealis.settings

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.Preference
import android.util.AttributeSet

/**
 * Opens the `url` attribute in a browser. Google TV often has none, so when no app can open the
 * link the URL is shown as text instead. The URL is also the summary unless one is set.
 */
@Suppress("DEPRECATION")
class LinkPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs) {
    var url: String? = attrs?.getAttributeValue(null, "url")

    init {
        if (summary == null) summary = url
    }

    public override fun onClick() {
        val target = url ?: return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        } catch (_: ActivityNotFoundException) {
            AlertDialog.Builder(context)
                .setTitle(R.string.borealis_link_dialog_title)
                .setMessage(target)
                .setPositiveButton(R.string.borealis_close, null)
                .show()
        }
    }
}
