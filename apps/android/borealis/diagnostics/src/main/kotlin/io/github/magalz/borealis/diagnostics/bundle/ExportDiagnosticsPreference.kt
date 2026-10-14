package io.github.magalz.borealis.diagnostics.bundle

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.preference.Preference
import android.util.AttributeSet
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import dagger.hilt.android.EntryPointAccessors
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.diagnostics.R
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings entry that builds the diagnostic bundle and shares it, or saves it where a TV user can
 * fetch it with `adb`. Referenced by class name from the Borealis preferences XML.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class ExportDiagnosticsPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    internal var sectionsProvider: () -> Set<DiagnosticSection> = {
        EntryPointAccessors.fromApplication(
                context.applicationContext,
                DiagnosticsEntryPoint::class.java,
            )
            .diagnosticSections()
    }
    internal var uriFor: (File) -> Uri = {
        FileProvider.getUriForFile(context, fileProviderAuthority(context.packageName), it)
    }
    internal var flushLogs: () -> Unit = { DiagnosticBundleWriter.flushOpenSinks() }
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running = false

    public override fun onClick() {
        if (running) return
        running = true
        toast(R.string.borealis_export_working)
        val flow = newFlow()
        scope.launch {
            val result = withContext(ioDispatcher) { flow.run() }
            running = false
            if (!isGone()) {
                // The system can refuse the share or the dialog (revoked grant, dead window); the
                // click handler must not take the app down with it.
                runCatching { present(result) }
                    .onFailure {
                        BorealisLog.e(LogTag.APP, "could not present the diagnostic export", it)
                        toast(R.string.borealis_export_failed)
                    }
            }
        }
    }

    private fun newFlow() =
        ExportFlow(
            packageName = context.packageName,
            chooserTitle = context.getString(R.string.borealis_export_chooser_title),
            buildBundle = {
                BundleBuilder.build(context, sectionsProvider(), flushLogs = flushLogs)
            },
            uriFor = uriFor,
            canShare = { context.packageManager.queryIntentActivities(it, 0).isNotEmpty() },
            externalDir = { context.getExternalFilesDir(ExportFlow.EXTERNAL_DIR_NAME) },
        )

    /** The settings screen may have been closed while the bundle was being built. */
    private fun isGone(): Boolean =
        (context as? Activity)?.let { it.isFinishing || it.isDestroyed } ?: false

    private fun present(result: ExportResult) {
        when (result) {
            is ExportResult.Share -> share(result.chooser)
            is ExportResult.Saved -> showSaved(result)
            ExportResult.Failed -> toast(R.string.borealis_export_failed)
        }
    }

    private fun share(chooser: Intent) {
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** The text can take focus, so a D-pad scrolls it and reaches the Close button. */
    private fun showSaved(saved: ExportResult.Saved) {
        val padding = (PADDING_DP * context.resources.displayMetrics.density).toInt()
        val body =
            TextView(context).apply {
                text =
                    context.getString(
                        R.string.borealis_export_saved_message,
                        saved.file.absolutePath,
                        saved.adbPullCommand,
                    )
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                isFocusable = true
                setPadding(padding, padding, padding, padding)
            }
        AlertDialog.Builder(context)
            .setTitle(R.string.borealis_export_saved_title)
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton(R.string.borealis_export_close, null)
            .show()
    }

    private fun toast(message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val PADDING_DP = 16
    }
}
