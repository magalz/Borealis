package io.github.magalz.borealis.settings

import android.app.Activity
import android.content.pm.PackageInfo
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class BorealisSettingsScreenTest {
    private fun launch(versionName: String? = "9.8.7"): Activity {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val info =
            PackageInfo().apply {
                packageName = app.packageName
                this.versionName = versionName
            }
        shadowOf(app.packageManager).installPackage(info)
        val activity = Robolectric.buildActivity(BorealisSettingsActivity::class.java).setup().get()
        activity.fragmentManager.executePendingTransactions()
        return activity
    }

    private fun Activity.pref(key: String): Preference {
        val fragment = fragmentManager.findFragmentById(android.R.id.content) as PreferenceFragment
        return checkNotNull(fragment.findPreference(key)) { "no preference with key $key" }
    }

    @Test
    fun `the activity hosts the preference fragment`() {
        val activity = launch()

        assertThat(activity.fragmentManager.findFragmentById(android.R.id.content))
            .isInstanceOf(BorealisSettingsFragment::class.java)
    }

    @Test
    fun `verbose logging and address masking are off by default`() {
        val activity = launch()

        val verbose = activity.pref(SettingsKeys.VERBOSE_LOGGING) as CheckBoxPreference
        val mask = activity.pref(SettingsKeys.MASK_ADDRESSES) as CheckBoxPreference

        assertThat(verbose.isChecked).isFalse()
        assertThat(mask.isChecked).isFalse()
        assertThat(verbose.title.toString()).isEqualTo("Verbose logging")
        assertThat(mask.title.toString()).isEqualTo("Mask addresses in logs and diagnostics")
    }

    @Test
    fun `switching a setting stores it in the default preferences`() {
        val activity = launch()
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)

        (activity.pref(SettingsKeys.VERBOSE_LOGGING) as CheckBoxPreference).isChecked = true
        (activity.pref(SettingsKeys.MASK_ADDRESSES) as CheckBoxPreference).isChecked = true

        assertThat(preferences.getBoolean("borealis_verbose_logging", false)).isTrue()
        assertThat(preferences.getBoolean("borealis_mask_addresses", false)).isTrue()
    }

    @Test
    fun `the version row shows the version name and the commit`() {
        val activity = launch(versionName = "9.8.7")

        assertThat(activity.pref(SettingsKeys.ABOUT_VERSION).summary.toString())
            .isEqualTo("9.8.7 (commit unknown)")
    }

    @Test
    fun `a missing version name is shown as unknown`() {
        val activity = launch(versionName = null)

        assertThat(activity.pref(SettingsKeys.ABOUT_VERSION).summary.toString())
            .isEqualTo("unknown (commit unknown)")
    }

    @Test
    fun `source code and privacy policy are links to the repository`() {
        val activity = launch()

        val source = activity.pref(SettingsKeys.ABOUT_SOURCE_CODE) as LinkPreference
        val policy = activity.pref(SettingsKeys.ABOUT_PRIVACY_POLICY) as LinkPreference

        assertThat(source.url).isEqualTo("https://github.com/magalz/Borealis")
        assertThat(source.title.toString()).isEqualTo("Source code")
        assertThat(source.summary.toString()).isEqualTo("https://github.com/magalz/Borealis")
        assertThat(policy.url).isEqualTo("https://github.com/magalz/Borealis/blob/main/PRIVACY.md")
        assertThat(policy.title.toString()).isEqualTo("Privacy policy")
    }

    @Test
    fun `the licence row is plain text and the licences row opens the licence texts`() {
        val activity = launch()

        val license = activity.pref(SettingsKeys.ABOUT_LICENSE)
        val licenses = activity.pref(SettingsKeys.ABOUT_LICENSES)

        assertThat(license.title.toString()).isEqualTo("Licence: GPL-3.0")
        assertThat(license.isSelectable).isFalse()
        assertThat(licenses).isInstanceOf(LicensesPreference::class.java)
        assertThat(licenses.title.toString()).isEqualTo("Open-source licences")
    }
}
