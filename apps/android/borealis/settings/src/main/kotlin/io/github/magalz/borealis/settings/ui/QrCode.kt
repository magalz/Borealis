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
 *
 * [extraQuietModules] is white that the box adds to each side beyond the margin of [qr], in whole
 * modules, for a code that is read from afar over a dark surface; with 0 the code fills the box.
 */
@Composable
internal fun QrCode(
    qr: QrMatrix,
    side: Dp,
    description: String,
    modifier: Modifier = Modifier,
    extraQuietModules: Int = 0,
) {
    Canvas(modifier.size(side).semantics { contentDescription = description }) {
        drawRect(Color.White)
        // The shorter side, so the code stays square and centred if the box is ever narrower.
        val extent = minOf(size.width, size.height)
        val modulesAcross = qr.size + 2 * extraQuietModules
        val module = floor(extent / modulesAcross)
        val originX = floor((size.width - module * modulesAcross) / 2) + extraQuietModules * module
        val originY = floor((size.height - module * modulesAcross) / 2) + extraQuietModules * module
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
