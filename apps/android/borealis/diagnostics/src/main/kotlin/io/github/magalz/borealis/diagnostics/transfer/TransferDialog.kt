package io.github.magalz.borealis.diagnostics.transfer

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.magalz.borealis.diagnostics.R
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.util.Locale

/**
 * The "Send to phone" dialog: serves [bundle] once on the local network and shows the link as a QR
 * code, with a countdown and the status. Dismissing the dialog stops the server, and so does the
 * settings screen going away. [address] is the private address to bind (null when there is no local
 * network) and [clock] drives the countdown and the server's expiry.
 */
internal class TransferDialog(
    private val context: Context,
    private val bundle: File,
    private val address: () -> InetAddress?,
    private val clock: () -> Long,
    private val isGone: () -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())

    fun show() {
        val local = address()
        if (local == null) {
            showMessage(R.string.borealis_transfer_no_network)
            return
        }
        val server = OneShotFileServer(bundle, local, clock)
        val link =
            try {
                server.start()
            } catch (error: IOException) {
                BorealisLog.e(LogTag.APP, "could not start the diagnostic transfer", error)
                showMessage(R.string.borealis_transfer_failed)
                return
            }
        // The link only exists on screen once showLink returns; if building it fails, nothing could
        // ever stop the server, so stop it here.
        var shown = false
        try {
            showLink(server, link)
            shown = true
        } finally {
            if (!shown) server.stop()
        }
    }

    private fun showLink(server: OneShotFileServer, link: String) {
        val metrics = context.resources.displayMetrics
        // A TV is wider than tall: the code goes left of the text, so the countdown and the status
        // stay in view. Stacked, they would be squeezed out of the dialog. The height is short on a
        // 720p screen, so the padding is smaller there too.
        val landscape = metrics.widthPixels > metrics.heightPixels
        val padding =
            ((if (landscape) LANDSCAPE_PADDING_DP else PADDING_DP) * metrics.density).toInt()
        val countdown = TextView(context)
        val status = TextView(context)
        val details =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(context).apply { setText(R.string.borealis_transfer_instructions) }
                )
                addView(
                    TextView(context).apply {
                        text = link
                        typeface = Typeface.MONOSPACE
                        setTextIsSelectable(true)
                        isFocusable = true
                    }
                )
                addView(countdown)
                addView(status)
            }
        val content =
            LinearLayout(context).apply {
                setPadding(padding, padding, padding, padding)
                addView(
                    qrView(link, if (landscape) LANDSCAPE_QR_FRACTION else PORTRAIT_QR_FRACTION)
                )
                if (landscape) {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val beside =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    beside.marginStart = padding
                    addView(details, beside)
                } else {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(details)
                }
            }
        render(server, countdown, status)
        val dialog =
            AlertDialog.Builder(context)
                .setTitle(R.string.borealis_transfer_title)
                .setView(ScrollView(context).apply { addView(content) })
                .setPositiveButton(R.string.borealis_export_close, null)
                .setOnDismissListener {
                    handler.removeCallbacksAndMessages(null)
                    server.stop()
                }
                .show()
        focusClose(dialog)
        tick(dialog, server, countdown, status)
    }

    private fun qrView(link: String, screenFraction: Double): ImageView {
        val metrics = context.resources.displayMetrics
        val target = (minOf(metrics.widthPixels, metrics.heightPixels) * screenFraction).toInt()
        return ImageView(context).apply {
            setImageBitmap(TransferQr.toBitmap(TransferQr.encode(link), target))
            contentDescription = context.getString(R.string.borealis_transfer_qr_description)
        }
    }

    /** Refreshes the texts once a second until the transfer is over or the screen is gone. */
    private fun tick(
        dialog: AlertDialog,
        server: OneShotFileServer,
        countdown: TextView,
        status: TextView,
    ) {
        handler.postDelayed(
            {
                if (isGone()) {
                    dialog.dismiss()
                } else {
                    render(server, countdown, status)
                    if (server.status == TransferStatus.WAITING) {
                        tick(dialog, server, countdown, status)
                    }
                }
            },
            TICK_MILLIS,
        )
    }

    private fun render(server: OneShotFileServer, countdown: TextView, status: TextView) {
        val state = server.status
        // The line is gone once the transfer is over, not blank: a blank line would take space.
        if (state == TransferStatus.WAITING) {
            countdown.text =
                context.getString(
                    R.string.borealis_transfer_expires,
                    formatRemaining(server.remainingMillis()),
                )
            countdown.visibility = View.VISIBLE
        } else {
            countdown.visibility = View.GONE
        }
        status.setText(
            when (state) {
                TransferStatus.WAITING -> R.string.borealis_transfer_status_waiting
                TransferStatus.DOWNLOADED -> R.string.borealis_transfer_status_downloaded
                TransferStatus.EXPIRED -> R.string.borealis_transfer_status_expired
                TransferStatus.STOPPED -> R.string.borealis_transfer_status_stopped
            }
        )
    }

    private fun showMessage(message: Int) {
        val padding = (PADDING_DP * context.resources.displayMetrics.density).toInt()
        val body =
            TextView(context).apply {
                setText(message)
                isFocusable = true
                setPadding(padding, padding, padding, padding)
            }
        focusClose(
            AlertDialog.Builder(context)
                .setTitle(R.string.borealis_transfer_title)
                .setView(ScrollView(context).apply { addView(body) })
                .setPositiveButton(R.string.borealis_export_close, null)
                .show()
        )
    }

    /** After the first layout, which would otherwise pick the first focusable view. */
    private fun focusClose(dialog: AlertDialog) {
        val close = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        close.post { close.requestFocus() }
    }

    private companion object {
        const val PADDING_DP = 16
        const val LANDSCAPE_PADDING_DP = 8
        const val PORTRAIT_QR_FRACTION = 0.6
        const val LANDSCAPE_QR_FRACTION = 0.5
        const val TICK_MILLIS = 1_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60

        /** `m:ss`, rounded up so it never shows 0:00 while the link still works. */
        fun formatRemaining(millis: Long): String {
            val seconds = (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
            return String.format(
                Locale.ROOT,
                "%d:%02d",
                seconds / SECONDS_PER_MINUTE,
                seconds % SECONDS_PER_MINUTE,
            )
        }
    }
}
