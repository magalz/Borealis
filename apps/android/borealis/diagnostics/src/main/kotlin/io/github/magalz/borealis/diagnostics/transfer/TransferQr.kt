package io.github.magalz.borealis.diagnostics.transfer

import android.graphics.Bitmap
import com.google.zxing.common.BitMatrix

internal object TransferQr {
    fun encode(text: String): BitMatrix = TODO()

    fun toBitmap(matrix: BitMatrix, targetPx: Int): Bitmap = TODO()
}
