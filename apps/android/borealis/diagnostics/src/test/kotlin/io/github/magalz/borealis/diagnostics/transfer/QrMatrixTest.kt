package io.github.magalz.borealis.diagnostics.transfer

import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The public way to a QR code: the text goes in, a matrix of modules comes out. */
@RunWith(RobolectricTestRunner::class)
class QrMatrixTest {
    private val address = "https://github.com/magalz/Borealis/blob/main/PRIVACY.md"

    private fun decode(matrix: QrMatrix): String {
        val pixels =
            IntArray(matrix.size * matrix.size) {
                if (matrix[it % matrix.size, it / matrix.size]) Color.BLACK else Color.WHITE
            }
        val source = RGBLuminanceSource(matrix.size, matrix.size, pixels)
        return QRCodeReader()
            .decode(
                BinaryBitmap(HybridBinarizer(source)),
                mapOf(DecodeHintType.PURE_BARCODE to true),
            )
            .text
    }

    @Test
    fun `the code of a text decodes to the exact text`() {
        assertThat(decode(QrMatrix.encode(address))).isEqualTo(address)
    }

    @Test
    fun `the code is a square with a light margin on every side`() {
        val matrix = QrMatrix.encode(address)

        val last = matrix.size - 1
        assertThat(matrix[0, 0]).isFalse()
        assertThat(matrix[last, 0]).isFalse()
        assertThat(matrix[0, last]).isFalse()
        assertThat(matrix[last, last]).isFalse()
        assertThat((0..last).all { !matrix[it, 0] && !matrix[0, it] }).isTrue()
        assertThat((0..last).all { !matrix[it, last] && !matrix[last, it] }).isTrue()
    }

    @Test
    fun `a longer text needs a larger code`() {
        val short = QrMatrix.encode("https://github.com/magalz/Borealis")

        assertThat(QrMatrix.encode(address).size).isGreaterThan(short.size)
    }

    @Test
    fun `the same text gives equal codes and another text a different one`() {
        assertThat(QrMatrix.encode(address)).isEqualTo(QrMatrix.encode(address))
        assertThat(QrMatrix.encode(address)).isNotEqualTo(QrMatrix.encode("$address?"))
    }
}
