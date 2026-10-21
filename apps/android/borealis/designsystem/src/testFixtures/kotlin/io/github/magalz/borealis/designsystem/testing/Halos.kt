package io.github.magalz.borealis.designsystem.testing

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.github.magalz.borealis.designsystem.components.BackdropTextHalo

/**
 * The text [text] is a backdrop text, with its halo drawn when [drawn] is true and not drawn when
 * it is false. [substring] matches [text] as part of the text.
 */
fun ComposeTestRule.assertHalo(text: String, drawn: Boolean = true, substring: Boolean = false) {
    onNode(
            hasText(text, substring) and SemanticsMatcher.expectValue(BackdropTextHalo, drawn),
            useUnmergedTree = true,
        )
        .assertExists()
}

/**
 * The text [text] is no backdrop text; or, when [drawn] is true, it is one with no halo, as on the
 * plain page.
 */
fun ComposeTestRule.assertNoHalo(text: String, drawn: Boolean = false) {
    val halo =
        if (drawn) SemanticsMatcher.expectValue(BackdropTextHalo, false)
        else SemanticsMatcher.keyNotDefined(BackdropTextHalo)
    onNode(hasText(text) and halo, useUnmergedTree = true).assertExists()
}
