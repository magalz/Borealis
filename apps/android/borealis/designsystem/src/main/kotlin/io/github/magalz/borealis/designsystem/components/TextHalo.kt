package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/**
 * How wide ([strokeDp], each glyph is widened by that much on every side) and how opaque
 * ([opacity], 0 to 1) a halo is. It is blurred by [BackdropHalo.BLUR_DP], as the halo of a
 * [BackdropText] is, in the `page` colour. A [filled] halo is drawn in three passes (the stroke, a
 * stroke of half the width, and the glyphs filled), so that a small shape, the dot of an i, a
 * period or a bullet, has no hole in it; the halo of a [BackdropText] is the one pass.
 */
@Immutable class TextHaloStyle(val opacity: Float, val strokeDp: Int, val filled: Boolean = false)

/**
 * The halo that every [Text] and [BackdropIcon] under it draws behind its glyphs, or null for none,
 * which is what every screen has. It is the halo of a [BackdropText] ([BackdropHalo]), at the
 * stroke and the opacity of the style, and a node that has it says so through [BackdropTextHalo] or
 * [BackdropIconHalo].
 *
 * A screen sets it over a surface that shows a picture it does not know and cannot measure, as the
 * stream does over the glass on its video: the text there keeps its contrast on the halo and not on
 * what is behind it. A control that sits on a solid surface of its own sets it back to null. The
 * `contrast.py halo` tool measures the halo that a screen asks for.
 */
val LocalTextHalo = staticCompositionLocalOf<TextHaloStyle?> { null }

/** What a [Text] under [LocalTextHalo] adds to its own: the halo to draw behind it. */
internal class TextHalo(val modifier: Modifier, val onTextLayout: (TextLayoutResult) -> Unit)

/**
 * The halo of a [Text] that has one, or null where [LocalTextHalo] has none. The halo is drawn from
 * the layout of the text, which [onTextLayout] of the caller gets as well.
 */
@Composable
internal fun rememberTextHalo(onTextLayout: ((TextLayoutResult) -> Unit)?): TextHalo? {
    val style = LocalTextHalo.current ?: return null
    val page = LocalBorealisColors.current.page
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    return TextHalo(
        Modifier.semantics { backdropTextHalo = true }
            .drawBehind {
                layout?.let { with(BackdropHalo) { drawHalo(it, page, style) } }
            },
        {
            layout = it
            onTextLayout?.invoke(it)
        },
    )
}
