package io.github.magalz.borealis.settings.privacy

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.settings.R
import java.io.File

private val BAR_PADDING = 4.dp
private val BAR_GAP = 4.dp

/**
 * Privacy and diagnostics as a screen of its own, for a phone: a top bar with Back, then the rows.
 * [onBack] leaves the screen. [onSendToPhone], [onOpenAudit] and [startShare] are those of
 * [PrivacyPane]. Provides its own theme for [formFactor].
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onSendToPhone: (File, String) -> Unit,
    onOpenAudit: () -> Unit,
    modifier: Modifier = Modifier,
    formFactor: FormFactor = rememberFormFactor(),
    viewModel: PrivacyViewModel = hiltViewModel(),
    startShare: (Intent) -> Unit = rememberShareStarter(),
) {
    val binding = rememberPrivacyBinding(viewModel, onSendToPhone, onOpenAudit, startShare)
    BorealisTheme(formFactor) {
        PrivacyScreenContent(
            binding.state,
            binding.snackbarHostState,
            binding.actions,
            onBack,
            modifier,
        )
    }
}

/** The stateless phone screen. */
@Composable
internal fun PrivacyScreenContent(
    state: PrivacyUiState,
    snackbarHostState: SnackbarHostState,
    actions: PrivacyActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(LocalBorealisColors.current.page)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = BAR_PADDING),
            horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_privacy_back),
                    stringResource(R.string.borealis_privacy_back),
                )
            }
            Text(
                stringResource(R.string.borealis_privacy_title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        PrivacyContent(state, snackbarHostState, actions, Modifier.weight(1f))
    }
}
