package io.github.magalz.borealis.library.addhost

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.BorealisTextField
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.library.R

private val CONTENT_MAX_WIDTH = 560.dp
private val CONTENT_GAP = 16.dp
private val HINT_GAP = 4.dp
private val BUTTON_GAP = 12.dp
private val PROGRESS_SIZE = 24.dp
private val PROGRESS_STROKE = 3.dp

/**
 * The Add host screen (UC-008): one Address field, a hint and a note on the port, and Add host and
 * Cancel. It is stateless and draws itself in [BorealisTheme] for [formFactor].
 *
 * The field has focus when the screen opens. On a phone that opens the keyboard; on a TV or
 * handheld the window is in keyboard input mode, where Compose does not open the keyboard on focus,
 * so the system keyboard opens when the user presses OK on the field. Done submits. While
 * [AddHostUiState.busy] a progress indicator shows and both buttons are disabled. Back is Cancel.
 */
@Composable
fun AddHostScreen(
    state: AddHostUiState,
    formFactor: FormFactor,
    onAddressChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BorealisTheme(formFactor) {
        BackHandler(onBack = onCancel)
        val colors = LocalBorealisColors.current
        val addressFocus = remember { FocusRequester() }
        Box(modifier.fillMaxSize().background(colors.page)) {
            Column(
                Modifier.widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(LocalBorealisSpacing.current.gutter),
                verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
            ) {
                Text(
                    stringResource(R.string.addhost_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(HINT_GAP)) {
                    BorealisTextField(
                        value = state.address,
                        onValueChange = onAddressChanged,
                        label = stringResource(R.string.addhost_address_label),
                        modifier = Modifier.initialFocus(addressFocus),
                        error = state.error?.let { stringResource(it.textRes()) },
                        onDone = onSubmit,
                        keyboardType = KeyboardType.Uri,
                    )
                    Hint(stringResource(R.string.addhost_address_hint))
                    Hint(stringResource(R.string.addhost_port_hint))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BorealisButton(
                        stringResource(R.string.addhost_submit),
                        onSubmit,
                        enabled = !state.busy,
                    )
                    BorealisButton(
                        stringResource(R.string.addhost_cancel),
                        onCancel,
                        style = BorealisButtonStyle.Secondary,
                        enabled = !state.busy,
                    )
                    if (state.busy) {
                        val busy = stringResource(R.string.addhost_busy)
                        CircularProgressIndicator(
                            Modifier.size(PROGRESS_SIZE).semantics { contentDescription = busy },
                            color = colors.accent,
                            strokeWidth = PROGRESS_STROKE,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalBorealisColors.current.ink3,
    )
}

@StringRes
private fun AddHostError.textRes(): Int =
    when (this) {
        AddHostError.EMPTY -> R.string.addhost_error_empty
        AddHostError.INVALID -> R.string.addhost_error_invalid
        AddHostError.WRONG_SUBNET -> R.string.addhost_error_wrong_subnet
        AddHostError.UNREACHABLE -> R.string.addhost_error_unreachable
    }
