@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A small monospace label. With an [onClick] it is a focusable button, without a static tag. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    // Stub: the implementation follows its failing tests.
}

/** A pill-shaped label for a capability of a host. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    // Stub: the implementation follows its failing tests.
}
