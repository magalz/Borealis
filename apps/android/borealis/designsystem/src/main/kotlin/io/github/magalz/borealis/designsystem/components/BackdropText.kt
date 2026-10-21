package io.github.magalz.borealis.designsystem.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.magalz.borealis.designsystem.theme.BorealisColors

/** The ink that a text on the background has on the plain page. */
enum class BackdropInk {
    Ink,
    Ink2,
    Ink3,
    Ink4,
}

/** Set on the node of a [BackdropText]: true where the halo is drawn behind its text. */
val BackdropTextHalo = SemanticsPropertyKey<Boolean>("BackdropTextHalo")

var SemanticsPropertyReceiver.backdropTextHalo by BackdropTextHalo

internal object BackdropHalo {
    const val STROKE_DP = 0
    const val BLUR_DP = 0
    const val OPACITY_PERCENT = 0
}

internal fun BorealisColors.backdropInk(ink: BackdropInk, halo: Boolean): Color =
    when (ink) {
        BackdropInk.Ink -> this.ink
        BackdropInk.Ink2 -> ink2
        BackdropInk.Ink3 -> ink3
        BackdropInk.Ink4 -> ink4
    }

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
    Text(
        text,
        modifier.semantics { backdropTextHalo = false },
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun BackdropKicker(text: String, modifier: Modifier = Modifier) {
    BackdropText(text, modifier)
}
