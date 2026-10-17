package io.github.magalz.borealis.diagnostics.transfer

import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The QR code of the link: [size] by [size] modules, margin included. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    override fun equals(other: Any?): Boolean =
        other is QrMatrix && size == other.size && modules.contentEquals(other.modules)

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()
}

/** Where a one-shot transfer stands. */
sealed interface TransferState {
    data object Idle : TransferState

    data object NoNetwork : TransferState

    data class Waiting(
        val link: String,
        val qr: QrMatrix,
        val fileName: String,
        val sizeBytes: Long,
        val remainingMillis: Long,
    ) : TransferState

    data object Downloaded : TransferState

    data object Expired : TransferState

    data object Stopped : TransferState

    data object Failed : TransferState
}

/** Skeleton: the behaviour follows in the next commit. */
class TransferSession
internal constructor(
    private val address: () -> InetAddress?,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<TransferState>(TransferState.Idle)
    val state: StateFlow<TransferState> = mutableState

    fun start(file: File) = Unit

    fun stop() = Unit
}
