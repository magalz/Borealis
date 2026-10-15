@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/** The reachability of a host as the dot shows it. */
enum class DotStatus {
    Online,
    Attention,
    Offline,
    Unknown,
}

private val DOT_SIZE = 8.dp

/**
 * A small status dot: success for [DotStatus.Online], offline grey for [DotStatus.Offline] and
 * [DotStatus.Unknown]. Pass a [contentDescription] when the dot is not next to a text that says the
 * same; with null it is decorative.
 */
@Composable
fun StatusDot(status: DotStatus, contentDescription: String?, modifier: Modifier = Modifier) {
    val colors = LocalBorealisColors.current
    val color = if (status == DotStatus.Online) colors.success else colors.offline
    Box(
        modifier
            .size(DOT_SIZE)
            .background(color, CircleShape)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    )
}
