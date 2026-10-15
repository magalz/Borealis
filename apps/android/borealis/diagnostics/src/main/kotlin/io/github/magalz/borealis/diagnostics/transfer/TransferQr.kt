package io.github.magalz.borealis.diagnostics.transfer

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** The QR code of the transfer link: the matrix is pure JVM, the bitmap is a separate step. */
internal object TransferQr {
    private const val MARGIN_MODULES = 2

    /** Error correction M, and the smallest matrix that holds [text] plus a small margin. */
    fun encode(text: String): BitMatrix =
        QRCodeWriter()
            .encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to MARGIN_MODULES,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )

    /**
     * Black modules on a white background, so the code reads inside a dark dialog. Each module is a
     * whole number of pixels, as large as fits in [targetPx], so a scaled-up image stays sharp.
     */
    fun toBitmap(matrix: BitMatrix, targetPx: Int): Bitmap {
        val scale = maxOf(1, targetPx / matrix.width)
        val size = matrix.width * scale
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] =
                    if (matrix[x / scale, y / scale]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
