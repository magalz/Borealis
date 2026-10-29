package io.github.magalz.borealis.designsystem.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.LocalGlassScene
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType

/**
 * The ink of a [BackdropText] on the plain page, as the screen had it before the halo: [Ink] for a
 * title, [Ink2] for the text that was a step below it, and [Ink3] and [Ink4] for the secondary text
 * and the section labels. Over the animated background [Ink3] and [Ink4] are ink-2, which the halo
 * keeps at 4.5:1 and which they do not reach on their own (docs/ux/accessibility.md, Halo).
 */
enum class BackdropInk {
    Ink,
    Ink2,
    Ink3,
    Ink4,
}

/**
 * Set on the node of a [BackdropText], and of any [Text] under [LocalTextHalo]: true where the halo
 * is drawn behind its text.
 */
val BackdropTextHalo = SemanticsPropertyKey<Boolean>("BackdropTextHalo")

internal var SemanticsPropertyReceiver.backdropTextHalo by BackdropTextHalo

/**
 * The halo behind a text on the animated background: the glyphs widened by [STROKE_DP], blurred by
 * [BLUR_DP], in the `page` colour at [OPACITY_PERCENT]. It is the smallest candidate of the grid
 * that keeps ink and ink-2 at 4.5:1 (3:1 for the title of a TV, which is large text), with 2% to
 * spare, over every background on a TV and on a phone. The figure that it keeps is the minimum over
 * every place that the text can take on every frame, a pixel apart, and not over a few places.
 * `contrast.py halo` measures it, and the section Halo of docs/ux/accessibility.md has the method
 * and the numbers; a test keeps these constants equal to the choice that the notes record, and
 * another reads the alpha of the drawn halo beside a stem against the model.
 */
internal object BackdropHalo {
    const val STROKE_DP = 3
    const val BLUR_DP = 2
    const val OPACITY_PERCENT = 80

    private const val PERCENT = 100f
    private const val BOTH_SIDES = 2

    /** The halo of a [BackdropText]: [STROKE_DP] wide at [OPACITY_PERCENT]. */
    val OVER_BACKGROUND = TextHaloStyle(OPACITY_PERCENT / PERCENT, STROKE_DP, filled = false)

    /**
     * The colour of the halo over the page colour [page]: [page] at the opacity of [style], which
     * is [OPACITY_PERCENT] where the text is on the background, and what a screen asks of
     * [LocalTextHalo] where it is on something else.
     */
    fun color(page: Color, style: TextHaloStyle = OVER_BACKGROUND): Color =
        page.copy(alpha = style.opacity)

    /** Draws the halo of the text that [layout] laid out, in [page] with [style], behind it. */
    fun DrawScope.drawHalo(
        layout: TextLayoutResult,
        page: Color,
        style: TextHaloStyle = OVER_BACKGROUND,
    ) {
        // The shadow of a stroked copy of the text, which has no colour of its own, is the halo: a
        // stroke of twice the width widens each glyph by the width, and the shadow blurs it. The
        // shadow layer of a paint is drawn on every API level, where a blur effect is not (API 31).
        val shadow = Shadow(color(page, style), Offset.Zero, BLUR_DP.dp.toPx())
        val stroke =
            Stroke(
                BOTH_SIDES * style.strokeDp.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        // The paragraph is painted and not drawn with drawText: drawText clips to the size of the
        // layout unless the overflow is visible, and the halo is outside it.
        drawIntoCanvas { canvas ->
            val paint = { drawStyle: DrawStyle ->
                layout.multiParagraph.paint(
                    canvas,
                    color = Color.Transparent,
                    shadow = shadow,
                    drawStyle = drawStyle,
                )
            }
            paint(stroke)
            if (style.filled) {
                // A stroke wider than a shape is wide has a hole where the shape is: the stroker
                // winds the inner side of the outline the other way, and it cancels the outer
                // side. The dot of an i, a period or a bullet then has almost no halo at its
                // edge. A stroke of half the width has a hole of a third the size, and the glyph
                // filled covers that.
                paint(
                    Stroke(
                        stroke.width / BOTH_SIDES,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                )
                paint(Fill)
            }
        }
    }
}

/**
 * This ink where a [BackdropText] has a [halo], and where it is on the plain page: ink-3 and ink-4
 * are ink-2 with a halo, and ink and ink-2 are as they are.
 */
internal fun BorealisColors.backdropInk(ink: BackdropInk, halo: Boolean): Color =
    when (ink) {
        BackdropInk.Ink -> this.ink
        BackdropInk.Ink2 -> ink2
        BackdropInk.Ink3 -> if (halo) ink2 else ink3
        BackdropInk.Ink4 -> if (halo) ink2 else ink4
    }

/**
 * A text that sits straight on the animated background, outside any glass: a screen title, a
 * section label, a count, a hint, a loading or an empty state. Over a background of any style it
 * has a soft dark halo ([BackdropHalo]) and takes [ink] as [BackdropInk] says; on the plain page
 * ([BackgroundStyle.Off]), or outside a host, it is a [Text] with the token as the screen had it.
 * The halo is drawn also where the glass is Off and with High contrast, for the background is still
 * behind the text. A text on a glass surface does not use it: `GlassTextColors` covers that.
 *
 * [style] is the text style, whose colour [ink] replaces. The node says through [BackdropTextHalo]
 * whether the halo is drawn.
 */
@Composable
fun BackdropText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    ink: BackdropInk = BackdropInk.Ink,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val colors = LocalBorealisColors.current
    val halo = LocalGlassScene.current.style != BackgroundStyle.Off
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val behind =
        if (halo) {
            Modifier.drawBehind { layout?.let { with(BackdropHalo) { drawHalo(it, colors.page) } } }
        } else {
            Modifier
        }
    // The semantics come first in the chain, so that a modifier that clears them, as the one of a
    // label under a cover does, leaves the halo in them.
    Text(
        text,
        Modifier.semantics { backdropTextHalo = halo }.then(modifier).then(behind),
        color = colors.backdropInk(ink, halo),
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = { layout = it },
        style = style,
    )
}

/**
 * A section label as a [BackdropText]: capitals in the kicker style, in ink-4, which is ink-2 where
 * the halo is drawn. It is a heading, which a screen reader jumps between.
 */
@Composable
fun BackdropKicker(text: String, modifier: Modifier = Modifier) {
    BackdropText(
        text.uppercase(),
        modifier.semantics { heading() },
        LocalBorealisType.current.kicker,
        BackdropInk.Ink4,
    )
}
