package io.github.magalz.borealis.stream.ui.hint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.stream.R
import kotlinx.coroutines.delay

private const val HINT_DURATION_MS = 6_000L
private val PADDING_HORIZONTAL = 14.dp
private val PADDING_VERTICAL = 6.dp

/**
 * The line that tells a new user how to open the menu on this device, while [show] is true. It goes
 * after six seconds, or when a panel opens, and then calls [onDismiss] once, so the session
 * remembers that it was seen. It takes no input.
 */
@Composable
internal fun MenuHint(
    show: Boolean,
    panelOpen: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    var done by remember { mutableStateOf(false) }
    val active = show && !done
    LaunchedEffect(active, panelOpen) {
        if (!active) return@LaunchedEffect
        if (!panelOpen) delay(HINT_DURATION_MS)
        done = true
        dismiss()
    }
    if (!active || panelOpen) return
    val colors = LocalBorealisColors.current
    val shape = LocalBorealisShapes.current.pill
    Text(
        stringResource(hintText(LocalFormFactor.current)),
        modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.line, shape)
            .padding(PADDING_HORIZONTAL, PADDING_VERTICAL),
        color = colors.ink2,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun hintText(formFactor: FormFactor) =
    when (formFactor) {
        FormFactor.Phone -> R.string.stream_hint_phone
        FormFactor.Handheld -> R.string.stream_hint_handheld
        FormFactor.Tv -> R.string.stream_hint_tv
    }
