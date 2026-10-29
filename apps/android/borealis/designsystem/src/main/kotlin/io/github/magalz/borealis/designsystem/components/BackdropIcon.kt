package io.github.magalz.borealis.designsystem.components

import android.graphics.BlurMaskFilter
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.LocalGlassScene
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/** Set on the node of a [BackdropIcon]: true where the halo is drawn behind its icon. */
val BackdropIconHalo = SemanticsPropertyKey<Boolean>("BackdropIconHalo")

private var SemanticsPropertyReceiver.backdropIconHalo by BackdropIconHalo

/**
 * An icon that stands straight on the animated background, outside any glass: the arrow back of a
 * bar, the actions of the Hosts bar, the toggle of the Apps bar. Over a background of any style it
 * has the halo of a [BackdropText] ([BackdropHalo]) behind it, which keeps it at 3:1 (WCAG 1.4.11)
 * as the text keeps 4.5:1; on the plain page ([BackgroundStyle.Off]), or outside a host, it is an
 * [Icon]. The halo is drawn also where the glass is Off and with High contrast, for the background
 * is still behind the icon. An icon on a glass surface does not use it: the glass covers that.
 *
 * Under [LocalTextHalo] the halo is drawn over any background and at the stroke and the opacity
 * that it says: the icon is then on a glass over a picture that nothing measures.
 *
 * [imageVector] has to be drawn with strokes, as the icons of [BorealisIcons] are, and the icon is
 * square. The node says through [BackdropIconHalo] whether the halo is drawn.
 */
@Composable
fun BackdropIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val style = LocalTextHalo.current
    val halo = LocalGlassScene.current.style != BackgroundStyle.Off || style != null
    val page = LocalBorealisColors.current.page
    val strokes = remember(imageVector) { imageVector.strokes() }
    val behind =
        if (halo)
            Modifier.iconHalo(imageVector, strokes, page, style ?: BackdropHalo.OVER_BACKGROUND)
        else Modifier
    Icon(
        imageVector,
        contentDescription,
        Modifier.semantics { backdropIconHalo = halo }.then(modifier).then(behind),
        tint,
    )
}

/** All the strokes of an icon as one path, in the units of its grid, and how wide they are. */
private class IconStrokes(val path: Path, val width: Float)

/**
 * The strokes of the vector as one path: the halo is the union of them, drawn once, as it is in the
 * model (`contrast.py halo --icons`), and not one halo for each path, which would be darker where
 * two of them meet.
 */
private fun ImageVector.strokes(): IconStrokes {
    val path = Path()
    var width = 0f
    fun add(node: VectorNode) {
        when (node) {
            is VectorGroup -> node.forEach(::add)
            is VectorPath ->
                if (node.stroke != null) {
                    path.addPath(node.pathData.toPath())
                    width = maxOf(width, node.strokeLineWidth)
                }
        }
    }
    root.forEach(::add)
    return IconStrokes(path, width)
}

/**
 * The halo behind an icon: its strokes widened by [BackdropHalo.STROKE_DP], blurred by
 * [BackdropHalo.BLUR_DP], in the `page` colour at [BackdropHalo.OPACITY_PERCENT]. It is the halo of
 * the text on a path: a stroke of the width of the icon's own and twice the halo's, in a paint with
 * a blur of the mask, which Skia gives the sigma that it gives the shadow of a text. The icon is
 * drawn over it.
 */
private fun Modifier.iconHalo(
    icon: ImageVector,
    strokes: IconStrokes,
    page: Color,
    haloStyle: TextHaloStyle,
): Modifier = drawWithCache {
    val scale = size.minDimension / icon.viewportWidth
    val outline =
        Path().apply {
            addPath(strokes.path)
            transform(Matrix().apply { scale(scale, scale) })
        }
    val halo =
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = strokes.width * scale + 2 * haloStyle.strokeDp.dp.toPx()
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            color = BackdropHalo.color(page, haloStyle)
            asFrameworkPaint().maskFilter =
                BlurMaskFilter(BackdropHalo.BLUR_DP.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
    onDrawBehind { drawIntoCanvas { it.drawPath(outline, halo) } }
}
