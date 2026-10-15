package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable

// Stub: the behaviour tests fail against it until the implementation lands.

enum class ActionStyle {
    Normal,
    Danger,
}

class ActionSheetAction<out T>(
    val key: T,
    val label: String,
    val consequence: String,
    val style: ActionStyle = ActionStyle.Normal,
)

@Composable
fun <T> ActionSheet(
    title: String,
    actions: List<ActionSheetAction<T>>,
    onAction: (ActionSheetAction<T>) -> Unit,
    onDismiss: () -> Unit,
) {}
