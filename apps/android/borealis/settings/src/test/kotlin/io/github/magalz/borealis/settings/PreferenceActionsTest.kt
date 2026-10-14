package io.github.magalz.borealis.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class PreferenceActionsTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun texts(view: View): List<String> =
        when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun containsScrollView(view: View): Boolean =
        view is ScrollView ||
            (view is ViewGroup &&
                (0 until view.childCount).any { containsScrollView(view.getChildAt(it)) })

    private fun latestDialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog()

    @Test
    fun `the entry point opens the settings activity with an explicit intent`() {
        BorealisSettingsPreference(activity).onClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.component)
            .isEqualTo(ComponentName(activity, BorealisSettingsActivity::class.java))
    }

    @Test
    fun `a link opens the browser`() {
        LinkPreference(activity).apply { url = "https://example.org/page" }.onClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data.toString()).isEqualTo("https://example.org/page")
    }

    @Test
    fun `a link that no app can open shows its url as text`() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .checkActivities(true)

        LinkPreference(activity).apply { url = "https://example.org/page" }.onClick()

        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        val dialog = latestDialog()
        assertThat(dialog.isShowing).isTrue()
        assertThat(texts(dialog.window!!.decorView)).contains("https://example.org/page")
    }

    @Test
    fun `a link without a url does nothing`() {
        LinkPreference(activity).onClick()

        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        assertThat(ShadowAlertDialog.getLatestAlertDialog()).isNull()
    }

    @Test
    fun `the licences row shows a scrollable view that says when the texts are missing`() {
        LicensesPreference(activity).onClick()

        val dialog = latestDialog()
        assertThat(dialog.isShowing).isTrue()
        assertThat(texts(dialog.window!!.decorView))
            .contains("The licence texts are not included in this build.")
        assertThat(containsScrollView(dialog.window!!.decorView)).isTrue()
    }
}
