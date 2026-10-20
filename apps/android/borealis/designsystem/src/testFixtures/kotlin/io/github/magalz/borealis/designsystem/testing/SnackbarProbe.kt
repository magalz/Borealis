package io.github.magalz.borealis.designsystem.testing

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat

/**
 * Shows [message] on [state] for as long as this is in the composition, so a test finds it however
 * long it takes; a snackbar of the default duration goes away on its own.
 */
@Composable
fun StaysAsSnackbar(state: SnackbarHostState, message: String) {
    LaunchedEffect(state, message) {
        state.showSnackbar(message, duration = SnackbarDuration.Indefinite)
    }
}

/**
 * Asserts that the snackbar that shows [message] is as wide as its text and the padding around it,
 * not as wide as the screen it is on.
 */
fun SemanticsNodeInteractionsProvider.assertSnackbarIsAsWideAsItsText(message: String) {
    val text = onNodeWithText(message, useUnmergedTree = true)
    val snackbar = text.onParent().getUnclippedBoundsInRoot()
    val textWidth = text.getUnclippedBoundsInRoot().width
    assertThat(snackbar.width.value).isAtLeast(textWidth.value)
    assertThat(snackbar.width.value).isAtMost((textWidth + MAX_SNACKBAR_PADDING).value)
}

/** The side padding of a snackbar, with room to spare, and no more. */
private val MAX_SNACKBAR_PADDING = 48.dp
