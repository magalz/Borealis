package io.github.magalz.borealis.settings.transfer

import io.github.magalz.borealis.diagnostics.transfer.QrMatrix
import io.github.magalz.borealis.diagnostics.transfer.TransferState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A transfer the test scripts: it records the calls and shows whatever [state] is set to. */
class FakeTransferController(initial: TransferState = waiting()) : TransferController {
    private val mutableState = MutableStateFlow(initial)
    val started = mutableListOf<File>()
    var stops = 0
        private set

    override val state: StateFlow<TransferState>
        get() = mutableState

    fun show(next: TransferState) {
        mutableState.value = next
    }

    override fun start(file: File) {
        started += file
    }

    override fun stop() {
        stops++
    }

    companion object {
        const val LINK = "http://192.168.1.41:40125/Qk3JfAXWr0000000000000000000000"
        const val FILE_NAME = "borealis-diagnostics-20261015-204210.zip"

        /** A 4 by 4 code with its dark modules on the diagonal, so a pixel test knows each one. */
        fun diagonalQr(): QrMatrix = qr(4) { x, y -> x == y }

        /**
         * Builds a code through its internal constructor: only the diagnostics module makes them.
         */
        fun qr(size: Int, dark: (x: Int, y: Int) -> Boolean): QrMatrix =
            QrMatrix::class
                .java
                .getDeclaredConstructor(Int::class.javaPrimitiveType, BooleanArray::class.java)
                .newInstance(size, BooleanArray(size * size) { dark(it % size, it / size) })

        fun waiting(
            remainingMillis: Long = 272_000,
            sizeBytes: Long = 59_392,
            qr: QrMatrix = diagonalQr(),
        ) =
            TransferState.Waiting(
                link = LINK,
                qr = qr,
                fileName = FILE_NAME,
                sizeBytes = sizeBytes,
                remainingMillis = remainingMillis,
            )
    }
}
