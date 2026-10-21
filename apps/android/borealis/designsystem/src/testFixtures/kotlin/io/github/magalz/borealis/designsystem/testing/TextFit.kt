package io.github.magalz.borealis.designsystem.testing

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import com.google.common.truth.Truth.assertWithMessage

/**
 * The texts on the screen that do not fit where they are: one whose line runs past the width it was
 * given (a word that cannot break, or a line that does not wrap), one that is cut off at the
 * bottom, or one that ends in an ellipsis. A line that the text wraps is not cut, so it is not
 * listed. Each entry names the text and what is wrong with it.
 *
 * It reads the text layouts that the tree gives, so it finds every
 * [androidx.compose.material3.Text] and [androidx.compose.foundation.text.BasicText]. The
 * `didOverflowWidth` of a layout is not used: it is true for any text that is narrower than the
 * width it was measured in, and a line of a text aligned to the end is measured past the node.
 */
fun ComposeTestRule.clippedTexts(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), true)
        .fetchSemanticsNodes()
        .mapNotNull { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: return@mapNotNull null
            val lines = 0 until layout.lineCount
            val reasons = buildList {
                if (lines.any { layout.getLineRight(it) > maxWidth(layout) + HALF_PIXEL }) {
                    add("runs past its width")
                }
                if (layout.didOverflowHeight) add("is cut off at the bottom")
                if (lines.any(layout::isLineEllipsized)) add("ends in an ellipsis")
                if (layout.multiParagraph.didExceedMaxLines) add("has more lines than it may show")
            }
            reasons
                .takeIf { it.isNotEmpty() }
                ?.let { "\"${layout.layoutInput.text.text}\" ${it.joinToString()}" }
        }

private const val HALF_PIXEL = 0.5f

/** PNG is lossless: the quality is ignored. */
private const val PNG_QUALITY = 100

/**
 * The width the text was given to lay its lines out in; a text with no limit has an infinite one.
 */
private fun maxWidth(layout: TextLayoutResult): Float =
    layout.layoutInput.constraints.maxWidth.takeIf { it != Constraints.Infinity }?.toFloat()
        ?: Float.MAX_VALUE

/** Fails, naming each text, when [clippedTexts] is not empty. */
fun ComposeTestRule.assertNoClippedText() {
    assertWithMessage("texts that do not fit").that(clippedTexts()).isEmpty()
}

/**
 * A tool, not a behaviour: writes the frame of the root as `<folder>/<name>.png` when the
 * environment variable `BOREALIS_FRAMES_OUT` names a folder, so that a person can look at what a
 * fit test saw. Without the variable it does nothing.
 */
fun ComposeTestRule.dumpFrame(name: String) {
    val folder = System.getenv("BOREALIS_FRAMES_OUT")?.takeIf { it.isNotBlank() } ?: return
    val image = onRoot().captureToImage().asAndroidBitmap()
    val file = java.io.File(folder, "$name.png").also { it.parentFile?.mkdirs() }
    file.outputStream().use {
        image.compress(android.graphics.Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
    }
}
