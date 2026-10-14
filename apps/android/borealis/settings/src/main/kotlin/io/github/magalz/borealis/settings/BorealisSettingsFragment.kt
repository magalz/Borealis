package io.github.magalz.borealis.settings

import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceFragment

/** The Borealis preference list; the entries live in `res/xml/borealis_preferences.xml`. */
@Suppress("DEPRECATION")
class BorealisSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.borealis_preferences)
        findPreference(SettingsKeys.ABOUT_VERSION).summary = versionSummary()
    }

    private fun versionSummary(): String {
        val versionName =
            try {
                activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } ?: getString(R.string.borealis_version_unknown)
        return getString(
            R.string.borealis_summary_version,
            versionName,
            getString(R.string.borealis_git_sha),
        )
    }
}
