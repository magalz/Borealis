package io.github.magalz.borealis.diagnostics.bundle

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class ExportDiagnosticsPreferenceTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Before
    fun setUp() {
        File(activity.cacheDir, "diagnostics").deleteRecursively()
        activity.getExternalFilesDir("diagnostics")?.deleteRecursively()
    }

    @After
    fun tearDown() {
        ShadowToast.reset()
    }

    private fun preference(sections: Set<DiagnosticSection> = emptySet()) =
        ExportDiagnosticsPreference(activity).apply {
            sectionsProvider = { sections }
            // androidx FileProvider cannot map Windows paths under Robolectric; its configuration
            // is
            // covered by FileProviderConfigTest and ManifestXmlTest.
            uriFor = { file ->
                Uri.Builder()
                    .scheme("content")
                    .authority(fileProviderAuthority(activity.packageName))
                    .appendPath("diagnostics")
                    .appendPath(file.name)
                    .build()
            }
            // Run the "background" work on the test thread so the result is there when onClick
            // returns.
            ioDispatcher = Dispatchers.Unconfined
        }

    @Suppress("DEPRECATION")
    private fun allowSharing() {
        val send = Intent(Intent.ACTION_SEND).setType("application/zip")
        val resolved =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = "com.example.mail"
                        name = "com.example.mail.Send"
                    }
            }
        shadowOf(activity.packageManager).addResolveInfoForIntent(send, resolved)
    }

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

    @Test
    fun `a click flushes the pending log lines into the bundle`() {
        allowSharing()
        val logDir = File(activity.noBackupFilesDir, "logs").apply { mkdirs() }

        preference()
            .apply { flushLogs = { File(logDir, "borealis.log").writeText("queued\n") } }
            .onClick()

        val cache = File(activity.cacheDir, "diagnostics").listFiles()!!.single()
        assertThat(unzip(cache.readBytes())["logs/borealis.log"]).isEqualTo("queued\n")
    }

    @Test
    fun `the app wires the flush through the hooks`() {
        allowSharing()
        val logDir = File(activity.noBackupFilesDir, "logs").apply { mkdirs() }
        DiagnosticBundleHooks.flushLogs = { File(logDir, "borealis.log").writeText("hooked\n") }
        try {
            preference().onClick()
        } finally {
            DiagnosticBundleHooks.flushLogs = {}
        }

        val cache = File(activity.cacheDir, "diagnostics").listFiles()!!.single()
        assertThat(unzip(cache.readBytes())["logs/borealis.log"]).isEqualTo("hooked\n")
    }

    @Test
    fun `a click shares the bundle through a chooser`() {
        allowSharing()

        preference(setOf(TextSection("audit.jsonl", "line\n"))).onClick()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_CHOOSER)
        @Suppress("DEPRECATION")
        val send = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertThat(send.type).isEqualTo("application/zip")
        @Suppress("DEPRECATION") val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo("${activity.packageName}.diagnostics.fileprovider")
        assertThat(uri.lastPathSegment).startsWith("borealis-diagnostics-")
        assertThat(ShadowAlertDialog.getLatestAlertDialog()).isNull()
    }

    @Test
    fun `with no share target the path and the adb command are shown in a dialog`() {
        preference().onClick()

        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        val dialog: AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertThat(dialog.isShowing).isTrue()
        val saved = checkNotNull(activity.getExternalFilesDir("diagnostics")).listFiles()!!.single()
        val text = texts(dialog.window!!.decorView).joinToString("\n")
        assertThat(text).contains(saved.absolutePath)
        assertThat(text)
            .contains(
                "adb pull /sdcard/Android/data/${activity.packageName}/files/diagnostics/${saved.name}"
            )
        // A D-pad reaches the text (it scrolls) and the Close button.
        assertThat(containsScrollView(dialog.window!!.decorView)).isTrue()
        assertThat(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isFocusable).isTrue()
    }

    @Test
    fun `a failure becomes a short message and never throws`() {
        // The bundle directory cannot be created: a file is in its place.
        File(activity.cacheDir, "diagnostics").writeText("a file, not a directory")

        preference().onClick()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Could not create the diagnostic bundle. See the log for details.")
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }
}
