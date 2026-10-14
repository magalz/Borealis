package io.github.magalz.borealis.diagnostics.audit

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.preference.Preference
import android.util.AttributeSet
import android.widget.Toast
import dagger.hilt.android.EntryPointAccessors
import io.github.magalz.borealis.diagnostics.R
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Erase audit trail" row of the Borealis settings screen (ADR 0050). A click asks for confirmation
 * first; the safe button, Cancel, has the focus, so a D-pad press on the dialog does not erase by
 * accident. Title and summary have defaults, so the preference XML needs no text attributes.
 */
@Suppress("DEPRECATION")
class EraseAuditPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    /** What erases the trail; null means the Hilt-provided [AuditStore]. Tests replace it. */
    var eraser: (suspend () -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        if (title == null) setTitle(R.string.borealis_audit_erase_title)
        if (summary == null) setSummary(R.string.borealis_audit_erase_summary)
        isPersistent = false
    }

    public override fun onClick() {
        val dialog =
            AlertDialog.Builder(context)
                .setTitle(R.string.borealis_audit_erase_dialog_title)
                .setMessage(R.string.borealis_audit_erase_dialog_message)
                .setPositiveButton(R.string.borealis_audit_erase_confirm) { _, _ -> erase() }
                .setNegativeButton(R.string.borealis_audit_erase_cancel, null)
                .show()
        // After the first layout, which would otherwise pick the first focusable view.
        val cancel = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        cancel.post { cancel.requestFocus() }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun erase() {
        scope.launch {
            val message =
                try {
                    (eraser ?: defaultEraser())()
                    R.string.borealis_audit_erased
                } catch (e: Exception) {
                    BorealisLog.w(
                        LogTag.APP,
                        "audit erase failed",
                        "error" to e.javaClass.simpleName,
                    )
                    R.string.borealis_audit_erase_failed
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun defaultEraser(): suspend () -> Unit {
        val entryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AuditEntryPoint::class.java,
            )
        return entryPoint.auditStore()::erase
    }
}
