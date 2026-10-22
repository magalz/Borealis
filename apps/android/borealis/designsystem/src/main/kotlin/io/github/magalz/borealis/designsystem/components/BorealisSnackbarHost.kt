package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val MAX_WIDTH = 600.dp

/** The space that Material's snackbar keeps around itself, so it sits where it did. */
private val MARGIN = 12.dp

/** The room at the start of the text, and at its end when there is no action. */
private val TEXT_HORIZONTAL = 16.dp
private val TEXT_VERTICAL = 14.dp
private val ACTION_GAP = 8.dp
private val ELEVATION = 6.dp

/**
 * Shows the snackbars of [hostState], each as wide as its message and centred in the width the host
 * is given, never wider than 600dp: a longer message wraps. The colours and the shape are those of
 * Material's snackbar in the Borealis theme. A snackbar with an action label has the action as a
 * button, and its focus; one without takes no focus. Place the host at the bottom of the screen
 * with [modifier], insets included.
 *
 * Material's dismiss button (`withDismissAction`) is not shown: no snackbar of the app asks for it.
 */
@Composable
fun BorealisSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier.fillMaxWidth()) { BorealisSnackbar(it) }
}

@Composable
private fun BorealisSnackbar(data: SnackbarData) {
    val actionLabel = data.visuals.actionLabel
    Box(Modifier.fillMaxWidth().padding(MARGIN), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.widthIn(max = MAX_WIDTH),
            shape = SnackbarDefaults.shape,
            color = SnackbarDefaults.color,
            contentColor = SnackbarDefaults.contentColor,
            shadowElevation = ELEVATION,
        ) {
            Row(
                Modifier.padding(
                    start = TEXT_HORIZONTAL,
                    end = if (actionLabel == null) TEXT_HORIZONTAL else 0.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(ACTION_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    data.visuals.message,
                    Modifier.weight(1f, fill = false).padding(vertical = TEXT_VERTICAL),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (actionLabel != null) {
                    TextButton(
                        onClick = data::performAction,
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = SnackbarDefaults.actionColor
                            ),
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}
