package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.settings.ChoiceSheet
import io.github.magalz.borealis.settings.R

/**
 * What is open over the settings screen: a choice sheet, the native warning or the Reset question.
 */
@Stable
internal class SettingsOverlays {
    /** The list whose choice sheet is open. */
    var choice by mutableStateOf<SettingsItem?>(null)

    /** The list (Resolution or Frame rate) whose native entry was just chosen. */
    var nativeWarning by mutableStateOf<SettingsItem?>(null)

    /** Whether the Reset layout question is open. */
    var confirmReset by mutableStateOf(false)

    /** What the rows ask of the screen. */
    val requests =
        RowRequests(onOpenChoice = { choice = it }, onResetLayout = { confirmReset = true })
}

/**
 * The choice sheet, the native warning and the Reset layout question that [overlays] holds open.
 */
@Composable
internal fun SettingsOverlayHost(
    overlays: SettingsOverlays,
    state: SettingsUiState,
    actions: SettingsActions,
) {
    overlays.choice?.let { item ->
        ChoiceSheet(
            title = stringResource(item.titleRes),
            options = choiceOptions(item, state),
            selected = item.selectedChoiceKey(state.snapshot),
            onSelect = { key ->
                actions.onChoiceSelected(item, key)
                if (isNativeChoice(item, key, state)) overlays.nativeWarning = item
            },
            onDismiss = { overlays.choice = null },
        )
    }
    overlays.nativeWarning?.let { item -> NativeWarning(item, overlays) }
    if (overlays.confirmReset) ResetLayoutQuestion(overlays, actions)
}

/** Whether the entry [key] of the list [item] is a native resolution or frame rate. */
private fun isNativeChoice(item: SettingsItem, key: String, state: SettingsUiState): Boolean =
    when (item) {
        SettingsItem.RESOLUTION -> state.resolutionChoices.any { it.key == key && it.isNative }
        SettingsItem.FRAME_RATE ->
            state.frameRateChoices.any { it.fps.toString() == key && it.isNative }
        else -> false
    }

/**
 * UC-009, A3: the PC must support the native mode. The choice is kept unless the user changes it.
 */
@Composable
private fun NativeWarning(item: SettingsItem, overlays: SettingsOverlays) {
    val close = { overlays.nativeWarning = null }
    SettingsMessageDialog(
        title =
            stringResource(
                if (item == SettingsItem.RESOLUTION) {
                    R.string.settings_native_resolution_title
                } else {
                    R.string.settings_native_frame_rate_title
                }
            ),
        message = stringResource(R.string.settings_native_warning_message),
        confirm = DialogButton(stringResource(R.string.settings_native_keep), close),
        onDismiss = close,
        dismiss =
            DialogButton(
                stringResource(R.string.settings_native_change),
                {
                    close()
                    overlays.choice = item
                },
                BorealisButtonStyle.Text,
            ),
        focusDismiss = false,
    )
}

/** UC-009, A6: ask before the layout of the on-screen controls is reset; Cancel has focus. */
@Composable
private fun ResetLayoutQuestion(overlays: SettingsOverlays, actions: SettingsActions) {
    val close = { overlays.confirmReset = false }
    SettingsMessageDialog(
        title = stringResource(R.string.settings_reset_layout_dialog_title),
        message = stringResource(R.string.settings_reset_layout_dialog_message),
        confirm =
            DialogButton(
                stringResource(R.string.settings_reset_layout_confirm),
                {
                    close()
                    actions.onResetLayout()
                },
                BorealisButtonStyle.Danger,
            ),
        onDismiss = close,
        dismiss =
            DialogButton(
                stringResource(R.string.settings_cancel),
                close,
                BorealisButtonStyle.Text,
            ),
    )
}
