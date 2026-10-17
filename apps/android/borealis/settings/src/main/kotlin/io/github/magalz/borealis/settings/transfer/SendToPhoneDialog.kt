package io.github.magalz.borealis.settings.transfer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

/** Not built yet: shows nothing. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SendToPhoneDialog(
    bundle: File,
    adbPullCommand: String,
    onDismiss: () -> Unit,
    transfer: TransferController,
    modifier: Modifier = Modifier,
) = Unit

/** Not built yet. */
@Suppress("UNUSED_PARAMETER") internal fun formatTransferSize(bytes: Long): String = ""

/** Not built yet. */
@Suppress("UNUSED_PARAMETER") internal fun formatCountdown(remainingMillis: Long): String = ""

/** The test tag of the QR code. */
internal const val QR_TAG = "send-to-phone-qr"
