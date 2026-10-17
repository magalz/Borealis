package io.github.magalz.borealis.settings.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import com.google.common.truth.Truth.assertThat

/** The surface of the Borealis dialog titled [title]: the frame names itself by its pane title. */
fun SemanticsNodeInteractionsProvider.dialogFrame(title: String): SemanticsNodeInteraction =
    onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title))

/** Asserts the dialog [title] is [expected] dp wide, give or take a dp for rounding to pixels. */
fun SemanticsNodeInteractionsProvider.assertDialogWidth(title: String, expected: Float) {
    val bounds = dialogFrame(title).getUnclippedBoundsInRoot()
    assertThat((bounds.right - bounds.left).value).isWithin(1f).of(expected)
}

/** The style the text [text] is laid out with. */
fun SemanticsNodeInteractionsProvider.textStyleOf(text: String): TextStyle {
    val results = mutableListOf<TextLayoutResult>()
    onNodeWithText(text)
        .fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult]
        .action
        ?.invoke(results)
    return results.single().layoutInput.style
}
