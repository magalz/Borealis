package io.github.magalz.borealis.settings.transfer

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.BorealisDialog
import io.github.magalz.borealis.designsystem.components.DialogAction
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.diagnostics.transfer.QrMatrix
import io.github.magalz.borealis.diagnostics.transfer.TransferState
import io.github.magalz.borealis.settings.R
import java.io.File
import kotlin.math.floor

/** The test tag of the QR code. */
internal const val QR_TAG = "send-to-phone-qr"

private val COLUMN_GAP = 24.dp
private val TEXT_GAP = 12.dp
private const val QR_SHARE_OF_SHORT_SIDE = 0.6f
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * The TV's "Send to phone" dialog (UC-006, ADR 0051). Opening it starts a one-download link for
 * [bundle] on [transfer], and it shows the state of that link: the QR code, the link text and a
 * countdown while it waits, then Downloaded or Expired (with Try again), or why there is no link
 * (no local network, with [adbPullCommand]; or a failure). It is a wide [BorealisDialog], so it
 * looks like the Saved dialog it follows. Close has the focus in every state; closing, Back and
 * leaving the composition stop the link, and Close and Back call [onDismiss]. Phones never show it:
 * the Privacy pane offers it on a TV only. The link never reaches a log.
 */
@Composable
fun SendToPhoneDialog(
    bundle: File,
    adbPullCommand: String,
    onDismiss: () -> Unit,
    transfer: TransferController = hiltViewModel<SendToPhoneViewModel>().transfer,
) {
    // The session outlives the dialog, so its state is the last link's until start replaces it.
    var started by remember(transfer, bundle) { mutableStateOf(false) }
    DisposableEffect(transfer, bundle) {
        transfer.start(bundle)
        started = true
        onDispose { transfer.stop() }
    }
    val current by transfer.state.collectAsState()
    // The window, not the dialog: a dialog window wraps its content, so it has no short side yet.
    val window = LocalWindowInfo.current.containerSize
    val qrSide =
        with(LocalDensity.current) {
            (minOf(window.width, window.height) * QR_SHARE_OF_SHORT_SIDE).toDp()
        }
    val close = {
        transfer.stop()
        onDismiss()
    }
    val state = if (started) current else TransferState.Idle
    BorealisDialog(
        title = stringResource(R.string.borealis_send_to_phone_title),
        onDismissRequest = close,
        confirm =
            DialogAction(stringResource(R.string.borealis_send_to_phone_close), onClick = close),
        width = DialogWidth.Wide,
        dismiss =
            if (state == TransferState.Expired) {
                DialogAction(
                    stringResource(R.string.borealis_send_to_phone_try_again),
                    BorealisButtonStyle.Secondary,
                ) {
                    transfer.start(bundle)
                }
            } else {
                null
            },
    ) {
        TransferContent(state = state, adbPullCommand = adbPullCommand, qrSide = qrSide)
    }
}

/** What the dialog says in [state]. */
@Composable
private fun TransferContent(state: TransferState, adbPullCommand: String, qrSide: Dp) {
    val colors = LocalBorealisColors.current
    when (state) {
        is TransferState.Waiting -> Waiting(state, qrSide)
        TransferState.Downloaded ->
            Outcome(stringResource(R.string.borealis_send_to_phone_downloaded))
        TransferState.Expired ->
            Outcome(
                stringResource(R.string.borealis_send_to_phone_expired),
                textColor = colors.warningInk,
            )
        TransferState.NoNetwork ->
            Outcome(
                stringResource(R.string.borealis_send_to_phone_no_network),
                command = adbPullCommand,
                textColor = colors.warningInk,
            )
        TransferState.Failed ->
            Outcome(
                stringResource(R.string.borealis_send_to_phone_failed),
                textColor = colors.dangerInk,
            )
        TransferState.Idle -> Outcome(stringResource(R.string.borealis_send_to_phone_starting))
        TransferState.Stopped -> Outcome(stringResource(R.string.borealis_send_to_phone_stopped))
    }
}

/** The file, then the QR code beside the instructions, the link and the countdown. */
@Composable
private fun Waiting(state: TransferState.Waiting, qrSide: Dp) {
    val colors = LocalBorealisColors.current
    Text(
        stringResource(
            R.string.borealis_send_to_phone_file,
            state.fileName,
            // As the Saved dialog of the same bundle shows it.
            Formatter.formatShortFileSize(LocalContext.current, state.sizeBytes),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = colors.ink3,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
        QrCode(state.qr, qrSide, stringResource(R.string.borealis_send_to_phone_qr_description))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
            Text(
                stringResource(R.string.borealis_send_to_phone_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
            Text(
                state.link,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = BorealisFonts.Mono,
                color = colors.ink3,
            )
            Text(
                stringResource(
                    R.string.borealis_send_to_phone_waiting,
                    formatCountdown(state.remainingMillis),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
        }
    }
}

/** A line of text and the `adb pull` [command] when there is one. */
@Composable
private fun Outcome(
    text: String,
    command: String? = null,
    textColor: Color = LocalBorealisColors.current.ink,
) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor)
    if (command != null) {
        Text(
            command,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = BorealisFonts.Mono,
            color = LocalBorealisColors.current.ink3,
        )
    }
}

/**
 * The code as black modules on white, the margin included in [qr]. Each module is a whole number of
 * pixels so the code stays sharp, centred in the [side] by [side] square.
 */
@Composable
private fun QrCode(qr: QrMatrix, side: Dp, description: String) {
    Canvas(Modifier.size(side).testTag(QR_TAG).semantics { contentDescription = description }) {
        drawRect(Color.White)
        // The shorter side, so the code stays square and centred if the box is ever narrower.
        val extent = minOf(size.width, size.height)
        val module = floor(extent / qr.size)
        val originX = floor((size.width - module * qr.size) / 2)
        val originY = floor((size.height - module * qr.size) / 2)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr[x, y]) {
                    drawRect(
                        Color.Black,
                        Offset(originX + x * module, originY + y * module),
                        Size(module, module),
                    )
                }
            }
        }
    }
}

/** The time left as minutes and seconds, rounded up, so the display reaches 0:00 at expiry. */
internal fun formatCountdown(remainingMillis: Long): String {
    val seconds = maxOf(0L, (remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
    val rest = (seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')
    return "${seconds / SECONDS_PER_MINUTE}:$rest"
}
