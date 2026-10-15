@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing

/** The optional action of an [EmptyState]. */
class EmptyStateAction(val label: String, val onClick: () -> Unit)

private val CONTENT_GAP = 8.dp
private val ACTION_GAP = 16.dp

/**
 * What a screen shows when it has nothing to list yet, such as Hosts while it is still searching
 * (decision D8): a [title], a [body] that says what to do next and, optionally, one [action].
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: EmptyStateAction? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(LocalBorealisSpacing.current.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalBorealisColors.current.ink3,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            BorealisButton(
                action.label,
                action.onClick,
                modifier = Modifier.padding(top = ACTION_GAP - CONTENT_GAP),
                style = BorealisButtonStyle.Secondary,
            )
        }
    }
}
