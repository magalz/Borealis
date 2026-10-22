package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.ui.common.appLabel
import io.github.magalz.borealis.stream.ui.common.onGamepadB
import io.github.magalz.borealis.stream.ui.error.ErrorActions
import io.github.magalz.borealis.stream.ui.error.errorCopy

private val CONTENT_GAP = 14.dp
private val SUPPORT_LINE_GAP = 2.dp

/**
 * What happened to the connection, what the host was doing, and the next step (ADR 0052, UC-002).
 * The raw code and the port hint stay visible for support. Gamepad B leaves; Back is handled by the
 * host.
 */
@Composable
fun ConnectionErrorScreen(
    appName: String,
    hostName: String,
    failure: StreamFailure,
    intents: StreamIntents,
) {
    val colors = LocalBorealisColors.current
    val gutter = LocalBorealisSpacing.current.gutter
    val copy = errorCopy(failure.kind)
    // The title of a generic failure and of a stopped stream names the app; every other kind names
    // the host.
    val subject =
        when (failure.kind) {
            FailureKind.GENERIC,
            FailureKind.STOPPED -> appLabel(appName)
            else -> hostName
        }
    Box(
        Modifier.fillMaxSize()
            .background(colors.page)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onGamepadB(intents::leave),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            // Scrolls so the buttons stay reachable on a short landscape phone with large text. The
            // gutter is inside the scrolled area, so the focus outline is not clipped.
            Modifier.verticalScroll(rememberScrollState())
                .widthIn(max = BorealisDimens.contentMaxWidth + gutter * 2)
                .padding(gutter),
            verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        ) {
            Column(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
            ) {
                Text(
                    stringResource(copy.title, subject),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(copy.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                )
            }
            SupportLines(failure)
            ErrorActions(copy.action, intents)
        }
    }
}

/** The raw code and, when the classifier has one, the port hint, in monospace for support. */
@Composable
private fun SupportLines(failure: StreamFailure) {
    val style =
        LocalBorealisType.current
            .codes(MaterialTheme.typography.bodySmall)
            .copy(color = LocalBorealisColors.current.ink3)
    Column(verticalArrangement = Arrangement.spacedBy(SUPPORT_LINE_GAP)) {
        Text(failure.rawCode, style = style)
        failure.portHint?.let { Text(it, style = style) }
    }
}
