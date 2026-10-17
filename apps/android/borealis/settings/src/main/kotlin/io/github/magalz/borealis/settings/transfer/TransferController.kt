package io.github.magalz.borealis.settings.transfer

import io.github.magalz.borealis.diagnostics.transfer.TransferState
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Send to phone dialog needs from a transfer: the [state] to show, [start] for a link that
 * serves a file once and [stop] to close it. The app's `TransferSession` is the implementation; the
 * seam lets the dialog be tested with a scripted state.
 */
interface TransferController {
    val state: StateFlow<TransferState>

    fun start(file: File)

    fun stop()
}
