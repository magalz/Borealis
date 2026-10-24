package com.limelight.utils

import android.app.Activity
import android.app.AlertDialog

class Dialog
private constructor(
    private val activity: Activity,
    private val title: String?,
    private val message: String?,
    private val runOnDismiss: Runnable,
) : Runnable {
    private lateinit var alert: AlertDialog

    override fun run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing) {
            return
        }

        alert = AlertDialog.Builder(activity).create()

        alert.setTitle(title)
        alert.setMessage(message)
        alert.setCancelable(false)
        alert.setCanceledOnTouchOutside(false)

        alert.setButton(
            AlertDialog.BUTTON_POSITIVE,
            activity.resources.getText(android.R.string.ok),
        ) { _, _ ->
            alert.dismiss()

            runOnDismiss.run()
        }
        alert.setOnShowListener {
            // Set focus to the OK button by default
            val button = alert.getButton(AlertDialog.BUTTON_POSITIVE)
            button.isFocusable = true
            button.isFocusableInTouchMode = true
            button.requestFocus()
        }

        alert.show()
    }

    companion object {
        @JvmStatic
        fun displayDialog(
            activity: Activity,
            title: String?,
            message: String?,
            runOnDismiss: Runnable,
        ) {
            activity.runOnUiThread(Dialog(activity, title, message, runOnDismiss))
        }
    }
}
