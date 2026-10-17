@file:Suppress("UNUSED_PARAMETER")

package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.Composable

/** One choice of a [ChoiceSheet]: the [key] it returns, its [label] and an optional [summary]. */
class ChoiceOption<out T>(val key: T, val label: String, val summary: String? = null)

/** A modal list of choices. */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    // Stub: the behaviour comes with the implementation.
}
