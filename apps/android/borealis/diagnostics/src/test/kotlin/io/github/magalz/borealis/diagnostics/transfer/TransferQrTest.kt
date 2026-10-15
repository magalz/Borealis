package io.github.magalz.borealis.diagnostics.transfer

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferQrTest {
    private val link = "http://192.168.1.23:41873/0123456789abcdef0123456789abcdef"

    private fun decode(matrix: BitMatrix, scale: Int = 1): com.google.zxing.Result {
        val width = matrix.width * scale
        val pixels =
            IntArray(width * width) { i ->
                val x = (i % width) / scale
                val y = (i / width) / scale
                if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        val source = RGBLuminanceSource(width, width, pixels)
        return QRCodeReader()
            .decode(
                BinaryBitmap(HybridBinarizer(source)),
                mapOf(DecodeHintType.PURE_BARCODE to true),
            )
    }

    @Test
    fun `the code decodes to the exact link`() {
        val result = decode(TransferQr.encode(link))

        assertThat(result.text).isEqualTo(link)
    }

    @Test
    fun `the error correction level is M`() {
        val result = decode(TransferQr.encode(link))

        assertThat(result.resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL]).isEqualTo("M")
    }

    @Test
    fun `the margin is small`() {
        // This link fits version 4 (33 modules); the margin adds a few on each side.
        val matrix = TransferQr.encode(link)

        assertThat(matrix.width).isAtMost(41)
        assertThat(matrix.width).isEqualTo(matrix.height)
        assertThat(matrix[0, 0]).isFalse()
    }

    @Test
    fun `the bitmap is black on white, scaled by whole modules, and still decodes`() {
        val matrix = TransferQr.encode(link)

        val bitmap = TransferQr.toBitmap(matrix, targetPx = 400)

        val scale = 400 / matrix.width
        assertThat(bitmap.width).isEqualTo(matrix.width * scale)
        assertThat(bitmap.height).isEqualTo(bitmap.width)
        assertThat(bitmap.config).isEqualTo(Bitmap.Config.ARGB_8888)
        // The margin is white even when the dialog behind it is dark, and a dark module is black.
        assertThat(bitmap.getPixel(0, 0)).isEqualTo(Color.WHITE)
        val (x, y) =
            (0 until matrix.height)
                .flatMap { row -> (0 until matrix.width).map { it to row } }
                .first { (col, row) -> matrix[col, row] }
        assertThat(bitmap.getPixel(x * scale, y * scale)).isEqualTo(Color.BLACK)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val result =
            QRCodeReader()
                .decode(
                    BinaryBitmap(HybridBinarizer(source)),
                    mapOf(DecodeHintType.PURE_BARCODE to true),
                )
        assertThat(result.text).isEqualTo(link)
    }

    @Test
    fun `a target smaller than the code still gives one pixel per module`() {
        val matrix = TransferQr.encode(link)

        val bitmap = TransferQr.toBitmap(matrix, targetPx = 1)

        assertThat(bitmap.width).isEqualTo(matrix.width)
    }
}
