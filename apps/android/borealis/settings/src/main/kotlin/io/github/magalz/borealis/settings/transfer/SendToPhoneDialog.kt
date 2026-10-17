package io.github.magalz.borealis.settings.transfer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.diagnostics.transfer.QrMatrix
import io.github.magalz.borealis.diagnostics.transfer.TransferState
import io.github.magalz.borealis.settings.R
import java.io.File
import java.util.Locale
import kotlin.math.floor

/** The test tag of the QR code. */
internal const val QR_TAG = "send-to-phone-qr"

private val DIALOG_WIDTH = 720.dp
private val DIALOG_PADDING = 24.dp
private val SECTION_GAP = 16.dp
private val COLUMN_GAP = 24.dp
private val TEXT_GAP = 12.dp
private const val QR_SHARE_OF_SHORT_SIDE = 0.6f
private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = BYTES_PER_KB * 1024
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * The TV's "Send to phone" dialog (UC-006, ADR 0051). Opening it starts a one-download link for
 * [bundle] on [transfer], and it shows the state of that link: the QR code, the link text and a
 * countdown while it waits, then Downloaded or Expired (with Try again), or why there is no link
 * (no local network, with [adbPullCommand]; or a failure). Close has the focus in every state;
 * closing, Back and leaving the composition stop the link, and Close and Back call [onDismiss].
 * Phones never show it: the Privacy pane offers it on a TV only. The link never reaches a log.
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
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val colors = LocalBorealisColors.current
        val shape = MaterialTheme.shapes.extraLarge
        Column(
            Modifier.widthIn(max = DIALOG_WIDTH)
                .background(colors.surface, shape)
                .border(1.dp, colors.line, shape)
                .clip(shape)
                .verticalScroll(rememberScrollState())
                .padding(DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        ) {
            Text(
                stringResource(R.string.borealis_send_to_phone_title),
                style = MaterialTheme.typography.titleLarge,
            )
            TransferContent(
                state = if (started) current else TransferState.Idle,
                adbPullCommand = adbPullCommand,
                qrSide = qrSide,
                onClose = close,
                onTryAgain = { transfer.start(bundle) },
            )
        }
    }
}

/** What the dialog says and offers in [state]. */
@Composable
private fun TransferContent(
    state: TransferState,
    adbPullCommand: String,
    qrSide: Dp,
    onClose: () -> Unit,
    onTryAgain: () -> Unit,
) {
    val colors = LocalBorealisColors.current
    when (state) {
        is TransferState.Waiting -> Waiting(state, qrSide, onClose)
        TransferState.Downloaded ->
            Outcome(stringResource(R.string.borealis_send_to_phone_downloaded), onClose)
        TransferState.Expired ->
            Outcome(
                stringResource(R.string.borealis_send_to_phone_expired),
                onClose,
                onTryAgain = onTryAgain,
                textColor = colors.warningInk,
            )
        TransferState.NoNetwork ->
            Outcome(
                stringResource(R.string.borealis_send_to_phone_no_network),
                onClose,
                command = adbPullCommand,
                textColor = colors.warningInk,
            )
        TransferState.Failed ->
            Outcome(
                stringResource(R.string.borealis_send_to_phone_failed),
                onClose,
                textColor = colors.dangerInk,
            )
        TransferState.Idle,
        TransferState.Stopped ->
            Outcome(stringResource(R.string.borealis_send_to_phone_starting), onClose)
    }
}

/** The file, then the QR code beside the instructions, the link, the countdown and Close. */
@Composable
private fun Waiting(state: TransferState.Waiting, qrSide: Dp, onClose: () -> Unit) {
    val colors = LocalBorealisColors.current
    Text(
        stringResource(
            R.string.borealis_send_to_phone_file,
            state.fileName,
            formatTransferSize(state.sizeBytes),
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
            Actions(onClose, onTryAgain = null)
        }
    }
}

/** A line of text, the `adb pull` [command] when there is one, and the actions. */
@Composable
private fun Outcome(
    text: String,
    onClose: () -> Unit,
    onTryAgain: (() -> Unit)? = null,
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
    Actions(onClose, onTryAgain)
}

/** Try again, when offered, and Close, which has the initial focus. */
@Composable
private fun Actions(onClose: () -> Unit, onTryAgain: (() -> Unit)?) {
    val closeFocus = remember { FocusRequester() }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TEXT_GAP, Alignment.End),
    ) {
        if (onTryAgain != null) {
            BorealisButton(
                stringResource(R.string.borealis_send_to_phone_try_again),
                onTryAgain,
                style = BorealisButtonStyle.Secondary,
            )
        }
        BorealisButton(
            stringResource(R.string.borealis_send_to_phone_close),
            onClose,
            modifier = Modifier.initialFocus(closeFocus),
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
        val module = floor(size.width / qr.size)
        val origin = floor((size.width - module * qr.size) / 2)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr[x, y]) {
                    drawRect(
                        Color.Black,
                        Offset(origin + x * module, origin + y * module),
                        Size(module, module),
                    )
                }
            }
        }
    }
}

/** A file size as "512 B", "58 KB" or "1.5 MB". */
internal fun formatTransferSize(bytes: Long): String =
    when {
        bytes < BYTES_PER_KB -> "$bytes B"
        bytes < BYTES_PER_MB -> "${Math.round(bytes.toDouble() / BYTES_PER_KB)} KB"
        else -> String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
    }

/** The time left as minutes and seconds, rounded up, so the display reaches 0:00 at expiry. */
internal fun formatCountdown(remainingMillis: Long): String {
    val seconds = maxOf(0L, (remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
    val rest = (seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')
    return "${seconds / SECONDS_PER_MINUTE}:$rest"
}
