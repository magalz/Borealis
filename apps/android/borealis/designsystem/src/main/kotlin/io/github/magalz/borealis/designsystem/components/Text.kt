// This file is the one place that draws Material's Text: every other text of the app goes through
// the Text below (ADR 0058, decision 4), and a detekt ForbiddenImport keeps it so.
@file:Suppress("ForbiddenImport")

package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.designsystem.theme.emOf

/**
 * The text of the app: Material's text with the word spacing of the Text spacing of the look (ADR
 * 0058, decision 4). The theme gives every style its letter spacing and line height, but Compose
 * has no word spacing, so at Wide and Wider each space gets a span: the letter spacing of the text
 * plus the word spacing of the level. The text that a screen reader reads stays [text], since a
 * span changes no character.
 *
 * At Normal, or when [text] has no space, this is Material's text with what it was given: no
 * [AnnotatedString] is built. The parameters are those of Material's, except the ones that nothing
 * in the app uses (the inline content of an [AnnotatedString]): add one when a caller needs it.
 * Text fields have no word spacing: addresses and PINs have no spaces.
 *
 * Under [LocalTextHalo] the text has the halo of a [BackdropText], at the opacity that it says.
 *
 * Mind the end of a wrapped line. Android lets a space at the end of a line hang past the width,
 * and it wraps the line as if the extra spacing of that space were not there, so a line that ends
 * in a space at Wide or Wider can draw up to one word spacing (0.16 or 0.5 em) past the box of the
 * text, ink included. A text that is clipped at its end, by a parent or by a fixed width, needs
 * that much room before the clip. The text is not given padding for it: in the screens of the app
 * it lands in the padding and the gaps that surround every text.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    if (LocalBorealisLook.current.textSpacing == TextSpacing.Normal || ' ' !in text) {
        val halo = rememberTextHalo(onTextLayout)
        MaterialText(
            text = text,
            modifier = if (halo == null) modifier else modifier.then(halo.modifier),
            color = color,
            autoSize = autoSize,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = halo?.onTextLayout ?: onTextLayout,
            style = style,
        )
    } else {
        Text(
            text = remember(text) { AnnotatedString(text) },
            modifier = modifier,
            color = color,
            autoSize = autoSize,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout ?: NoTextLayout,
            style = style,
        )
    }
}

/** [Text] of an [AnnotatedString]: its spans stay, and each space gets the word spacing. */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = NoTextLayout,
    style: TextStyle = LocalTextStyle.current,
) {
    val spacing = LocalBorealisLook.current.textSpacing
    val spaced =
        if (spacing == TextSpacing.Normal || ' ' !in text.text) {
            text
        } else {
            val size = if (fontSize.isSpecified) fontSize else style.fontSize
            val own = if (letterSpacing.isSpecified) letterSpacing else style.letterSpacing
            val letterEm = emOf(own, size) ?: 0f
            remember(text, spacing, letterEm) { text.withWordSpacing(letterEm + spacing.wordEm) }
        }
    val halo = rememberTextHalo(onTextLayout)
    MaterialText(
        text = spaced,
        modifier = if (halo == null) modifier else modifier.then(halo.modifier),
        color = color,
        autoSize = autoSize,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = halo?.onTextLayout ?: onTextLayout,
        style = style,
    )
}

private val NoTextLayout: (TextLayoutResult) -> Unit = {}

/** This text with a span of [spaceEm] of letter spacing on each of its spaces; its spans stay. */
private fun AnnotatedString.withWordSpacing(spaceEm: Float): AnnotatedString =
    AnnotatedString.Builder(this)
        .apply {
            for (index in text.indices) {
                if (text[index] == ' ') {
                    addStyle(SpanStyle(letterSpacing = spaceEm.em), index, index + 1)
                }
            }
        }
        .toAnnotatedString()
