package io.github.magalz.borealis.diagnostics.bundle

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Uri
import android.preference.Preference
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import dagger.hilt.android.EntryPointAccessors
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.diagnostics.R
import io.github.magalz.borealis.diagnostics.transfer.TransferDialog
import io.github.magalz.borealis.diagnostics.transfer.activeLocalIpv4
import io.github.magalz.borealis.diagnostics.transfer.monotonicMillis
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogSwitches
import io.github.magalz.borealis.logging.LogTag
import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    internal var logSwitches: () -> LogSwitches = {
        EntryPointAccessors.fromApplication(
                context.applicationContext,
                DiagnosticsEntryPoint::class.java,
            )
            .logSwitches()
    }
    internal var flushLogs: () -> Unit = { DiagnosticBundleWriter.flushOpenSinks() }
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var transferAddress: () -> InetAddress? = {
        context.getSystemService(ConnectivityManager::class.java)?.let(::activeLocalIpv4)
    }
    internal var transferClock: () -> Long = ::monotonicMillis

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running = false
    private var progress: AlertDialog? = null

    public override fun onClick() {
        if (running) {
            // Back closed the progress dialog but the export goes on: show it again, start nothing.
            if (progress?.isShowing != true) showProgress()
            return
        }
        // Built first: if it throws, nothing is left running.
        val exporter = newExporter()
        running = true
        showProgress()
        scope.launch {
            try {
                val result = exporter.export()
                closeProgress()
                if (!isGone()) {
                    // The system can refuse the share or the dialog (revoked grant, dead window);
                    // the click handler must not take the app down with it.
                    runCatching { present(result) }
                        .onFailure {
                            BorealisLog.e(LogTag.APP, "could not present the diagnostic export", it)
                            toast(R.string.borealis_export_failed)
                        }
                }
            } finally {
                running = false
                closeProgress()
            }
        }
    }

    private fun closeProgress() {
        progress?.let { runCatching { it.dismiss() } }
        progress = null
    }

    /**
     * A toast is easy to miss and a TV does not show it, so the wait has a dialog of its own. Back
     * closes it; the export goes on, and a click while it runs shows the dialog again. A window
     * that cannot be built does not stop the export.
     */
    private fun showProgress() {
        progress =
            runCatching {
                val padding = (PADDING_DP * context.resources.displayMetrics.density).toInt()
                val body =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(padding, padding, padding, padding)
                        addView(ProgressBar(context))
                        addView(
                            TextView(context).apply {
                                setText(R.string.borealis_export_working)
                                setPadding(padding, 0, 0, 0)
                            }
                        )
                    }
                AlertDialog.Builder(context)
                    .setTitle(R.string.borealis_export_title)
                    .setView(body)
                    .create()
                    .apply {
                        setCanceledOnTouchOutside(false)
                        show()
                    }
            }
                .onFailure { BorealisLog.e(LogTag.APP, "could not show the export progress", it) }
                .getOrNull()
    }

    private fun newExporter() =
        DiagnosticsExporter(
            context = context,
            sections = sectionsProvider,
            switches = logSwitches(),
            ioDispatcher = ioDispatcher,
            uriFor = uriFor,
            flushLogs = flushLogs,
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

    /** Share only when the user asks for it; the system can still refuse. */
    private fun shareOnRequest(chooser: Intent) {
        runCatching { share(chooser) }
            .onFailure {
                BorealisLog.e(LogTag.APP, "could not share the saved diagnostic bundle", it)
                toast(R.string.borealis_export_failed)
            }
    }

    /**
     * The text can take focus, so a D-pad scrolls it, and the buttons are focusable. Close has the
     * focus first. The neutral button is "Share…" on a phone or tablet and "Send to phone" on a TV,
     * where the only receiver is usually a video player.
     */
    private fun showSaved(saved: ExportResult.Saved) {
        val padding = (PADDING_DP * context.resources.displayMetrics.density).toInt()
        val body =
            TextView(context).apply {
                text =
                    context.getString(
                        R.string.borealis_export_saved_message,
                        saved.location,
                        saved.adbPullCommand,
                    )
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                isFocusable = true
                setPadding(padding, padding, padding, padding)
            }
        val television = isTelevision(context)
        val builder =
            AlertDialog.Builder(context)
                .setTitle(R.string.borealis_export_saved_title)
                .setView(ScrollView(context).apply { addView(body) })
                .setPositiveButton(R.string.borealis_export_close, null)
        if (television) {
            builder.setNeutralButton(R.string.borealis_export_send_to_phone, null)
        } else {
            builder.setNeutralButton(R.string.borealis_export_share) { _, _ ->
                shareOnRequest(saved.chooser)
            }
        }
        val dialog = builder.show()
        // A dialog button closes the dialog; this one must leave the saved dialog (and its adb
        // command) under the transfer dialog, so its listener is set after show().
        if (television) {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                sendToPhone(saved)
            }
        }
        // After the first layout, which would otherwise pick the first focusable view.
        val close = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        close.post { close.requestFocus() }
    }

    private fun sendToPhone(saved: ExportResult.Saved) {
        runCatching {
            TransferDialog(context, saved.bundle, transferAddress, transferClock, ::isGone).show()
        }
            .onFailure {
                BorealisLog.e(LogTag.APP, "could not show the diagnostic transfer", it)
                toast(R.string.borealis_transfer_failed)
            }
    }

    private fun toast(message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val PADDING_DP = 16
    }
}
