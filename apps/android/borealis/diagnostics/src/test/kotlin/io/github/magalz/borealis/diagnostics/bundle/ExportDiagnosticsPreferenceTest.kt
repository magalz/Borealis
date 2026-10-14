package io.github.magalz.borealis.diagnostics.bundle

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

/** An activity whose `startActivity` fails the way a revoked grant or a bad intent would. */
class RefusingActivity : Activity() {
    override fun startActivity(intent: Intent?) {
        throw SecurityException("not allowed")
    }
}

@RunWith(RobolectricTestRunner::class)
class ExportDiagnosticsPreferenceTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private lateinit var provider: FakeDownloadsProvider

    @Before
    fun setUp() {
        File(activity.cacheDir, "diagnostics").deleteRecursively()
        provider = FakeDownloadsProvider.install()
    }

    @After
    fun tearDown() {
        ShadowToast.reset()
    }

    private fun preference(
        sections: Set<DiagnosticSection> = emptySet(),
        context: Activity = activity,
    ) =
        ExportDiagnosticsPreference(context).apply {
            sectionsProvider = { sections }
            // androidx FileProvider cannot map Windows paths under Robolectric; the real one is
            // covered by FileProviderConfigTest (not on Windows) and ManifestXmlTest.
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
    fun `a share that the system refuses becomes a message and does not crash`() {
        allowSharing()
        val refusing = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()

        preference(context = refusing).onClick()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Could not create the diagnostic bundle. See the log for details.")
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

    private fun savedName() =
        provider.rows
            .single()
            .current
            .getAsString(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)

    private fun savedDialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog()

    @Test
    fun `with no share target the Downloads path and the adb command are shown in a dialog`() {
        preference().onClick()

        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        val dialog = savedDialog()
        assertThat(dialog.isShowing).isTrue()
        val text = texts(dialog.window!!.decorView).joinToString("\n")
        assertThat(text).contains("Download/${savedName()}")
        assertThat(text).contains("adb pull /sdcard/Download/${savedName()}")
        // A D-pad reaches the text (it scrolls) and both buttons.
        assertThat(containsScrollView(dialog.window!!.decorView)).isTrue()
        assertThat(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isFocusable).isTrue()
        assertThat(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isFocusable).isTrue()
    }

    @Test
    fun `a TV saves the bundle and shows the dialog even when a receiver exists`() {
        RuntimeEnvironment.setQualifiers("+television")
        val tv = Robolectric.buildActivity(Activity::class.java).setup().get()
        allowSharing()

        preference(context = tv).onClick()

        assertThat(shadowOf(tv).nextStartedActivity).isNull()
        val dialog = savedDialog()
        assertThat(dialog.isShowing).isTrue()
        assertThat(texts(dialog.window!!.decorView).joinToString("\n"))
            .contains("adb pull /sdcard/Download/${savedName()}")
    }

    @Test
    fun `the Close button has the focus when the saved dialog opens`() {
        preference().onClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(savedDialog().getButton(AlertDialog.BUTTON_POSITIVE).hasFocus()).isTrue()
    }

    @Test
    fun `the Share button opens the chooser with a read grant for the bundle`() {
        preference().onClick()

        savedDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        @Suppress("DEPRECATION")
        val send = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertThat(send.type).isEqualTo("application/zip")
        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        @Suppress("DEPRECATION") val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertThat(uri.authority).isEqualTo("${activity.packageName}.diagnostics.fileprovider")
    }

    @Test
    fun `a Share button that the system refuses becomes a message and does not crash`() {
        val refusing = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()
        preference(context = refusing).onClick()

        savedDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Could not create the diagnostic bundle. See the log for details.")
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
