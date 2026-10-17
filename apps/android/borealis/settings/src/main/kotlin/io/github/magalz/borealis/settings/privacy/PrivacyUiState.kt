package io.github.magalz.borealis.settings.privacy

import android.content.Intent
import java.io.File

/**
 * What the Privacy and diagnostics pane shows (UC-006, UC-007): the two switches, whether the
 * bundle is being built and the dialog that is open, if any.
 */
data class PrivacyUiState(
    val verboseLogging: Boolean = false,
    val maskAddresses: Boolean = false,
    val exporting: Boolean = false,
    val dialog: PrivacyDialog? = null,
)

/** A dialog over the pane. At most one is open. */
sealed interface PrivacyDialog {
    /** "Erase the audit trail?", waiting for Cancel or Erase. */
    data object EraseConfirmation : PrivacyDialog

    /**
     * The bundle was saved to [location] because a TV, or a phone with no receiver, cannot share
     * it. [adbPullCommand] copies it to a computer; [bundle] is what Send to phone sends.
     */
    data class Saved(
        val location: String,
        val adbPullCommand: String,
        val bundle: File,
        val fileName: String,
        val sizeBytes: Long,
    ) : PrivacyDialog
}

/** What the pane does once, outside its state. */
sealed interface PrivacyEffect {
    /** Start [chooser], the system share sheet for the exported bundle. */
    class StartShare(val chooser: Intent) : PrivacyEffect

    /** Say [message] briefly. */
    data class ShowMessage(val message: PrivacyMessage) : PrivacyEffect
}

/** A short message of the pane. */
enum class PrivacyMessage {
    ExportFailed,
    EraseDone,
    EraseFailed,
}
