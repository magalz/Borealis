package io.github.magalz.borealis.designsystem.testing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult

/** The layout that the text of this node was drawn with: its lines, its size and its style. */
fun SemanticsNode.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}

/** The layout that the text of this node was drawn with: its lines, its size and its style. */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult = fetchSemanticsNode().textLayout()

/** The colour that the text [text] is drawn in, read from the unmerged tree of the screen. */
fun ComposeTestRule.textColor(text: String): Color =
    onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style.color
