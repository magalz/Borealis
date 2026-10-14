package io.github.magalz.borealis

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import com.limelight.R
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice

/**
 * The question "an app is already running": Join, Quit and relaunch or Cancel. Thin UI glue: the
 * decision logic is `LaunchConflicts` in `:borealis:host`. Everything here runs on the main thread.
 * The three buttons are focusable, so a D-pad works; Back, [dismiss] and anything else that closes
 * the dialog count as Cancel (the first answer wins).
 */
object LaunchConflictDialog {
    private var current: AlertDialog? = null

    /** Shows the dialog on [activity], or answers Cancel when the activity is going away. */
    @JvmStatic
    @Suppress("LongParameterList")
    fun show(
        activity: Activity,
        runningAppId: Int,
        runningAppName: String?,
        requestedAppId: Int,
        requestedAppName: String?,
        answer: ConflictAnswer,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            answer.choose(ConflictChoice.CANCEL)
            return
        }
        dismiss()
        val running = runningAppName ?: activity.getString(R.string.conflict_unknown_app)
        val requested = requestedAppName ?: activity.getString(R.string.conflict_requested_unknown)
        val question =
            if (runningAppId == requestedAppId) {
                activity.getString(R.string.conflict_message_same, running)
            } else {
                activity.getString(R.string.conflict_message_other, running, requested)
            }
        current =
            AlertDialog.Builder(activity)
                .setTitle(R.string.conflict_title)
                .setMessage(question + "\n\n" + activity.getString(R.string.conflict_quit_warning))
                .setPositiveButton(R.string.conflict_join, choose(answer, ConflictChoice.JOIN))
                .setNeutralButton(
                    R.string.conflict_quit_and_relaunch,
                    choose(answer, ConflictChoice.QUIT_AND_RELAUNCH),
                )
                .setNegativeButton(android.R.string.cancel, choose(answer, ConflictChoice.CANCEL))
                .setOnDismissListener {
                    answer.choose(ConflictChoice.CANCEL)
                    current = null
                }
                .show()
    }

    /** Closes the dialog, if it is open; the pending question is answered with Cancel. */
    @JvmStatic
    fun dismiss() {
        current?.dismiss()
        current = null
    }

    private fun choose(answer: ConflictAnswer, choice: ConflictChoice) =
        DialogInterface.OnClickListener { _, _ ->
            answer.choose(choice)
        }
}
