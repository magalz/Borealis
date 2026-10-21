package io.github.magalz.borealis.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.magalz.borealis.diagnostics.transfer.QrMatrix
import kotlin.math.floor

/**
 * The code [qr] as black modules on white, the margin included in [qr]. Each module is a whole
 * number of pixels so the code stays sharp, centred in the [side] by [side] square. A screen reader
 * says [description] for it. The screen shows the text that the code holds, so the description says
 * what the image is and does not repeat that text.
 */
@Composable
internal fun QrCode(qr: QrMatrix, side: Dp, description: String, modifier: Modifier = Modifier) {
    Canvas(modifier.size(side).semantics { contentDescription = description }) {
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
