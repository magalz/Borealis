package io.github.magalz.borealis.library.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.optionsClickable
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.library.R

private val ACTION_SIZE = 48.dp
private val ACTION_GAP = 10.dp
private val ACTION_ICON_SIZE = 24.dp
private val TOP_BAR_PADDING = 8.dp

@Composable
internal fun PhoneTopBar(onAddHost: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = LocalBorealisSpacing.current.gutter, vertical = TOP_BAR_PADDING),
        horizontalArrangement = Arrangement.spacedBy(ACTION_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.hosts_title),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        IconAction(
            ImageVector.vectorResource(R.drawable.ic_hosts_add),
            stringResource(R.string.hosts_add_host),
            onAddHost,
        )
        IconAction(
            ImageVector.vectorResource(R.drawable.ic_hosts_settings),
            stringResource(R.string.hosts_settings),
            onSettings,
        )
    }
}

@Composable
private fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Box(
        Modifier.size(ACTION_SIZE)
            .borealisFocusIndication(shape)
            .clip(shape)
            .optionsClickable(onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(ACTION_ICON_SIZE),
            tint = LocalBorealisColors.current.ink2,
        )
    }
}

@Composable
internal fun TvHeader(state: HostsUiState) {
    val gutter = LocalBorealisSpacing.current.gutter
    Row(
        Modifier.fillMaxWidth().padding(start = gutter, top = gutter, end = gutter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(stringResource(R.string.hosts_title), style = MaterialTheme.typography.titleLarge)
        if (!state.loading && !state.searching) {
            Text(
                pluralStringResource(R.plurals.hosts_count, state.items.size, state.items.size),
                style = MaterialTheme.typography.bodySmall,
                color = LocalBorealisColors.current.ink3,
            )
        }
    }
}
