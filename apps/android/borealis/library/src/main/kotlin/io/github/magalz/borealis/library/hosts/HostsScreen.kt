package io.github.magalz.borealis.library.hosts

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.adaptive.hostGridColumns
import io.github.magalz.borealis.designsystem.components.ContinueCard
import io.github.magalz.borealis.designsystem.components.DotStatus
import io.github.magalz.borealis.designsystem.components.EmptyState
import io.github.magalz.borealis.designsystem.components.EmptyStateAction
import io.github.magalz.borealis.designsystem.components.HostCard
import io.github.magalz.borealis.designsystem.components.HostCardAction
import io.github.magalz.borealis.designsystem.components.Kicker
import io.github.magalz.borealis.designsystem.components.NavigationRail
import io.github.magalz.borealis.designsystem.focus.InitialFocusState
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.focus.rememberInitialFocusState
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.library.R
import io.github.magalz.borealis.library.domain.Reachability

/** An outline sits 5dp outside its card, so neighbouring cards need 10dp between them. */
private val GRID_GAP = 12.dp
private val SECTION_PADDING = 6.dp

/**
 * The Hosts home screen: the session to continue, then every host with its state and the one action
 * that state allows (ADR 0052, D2, D8).
 *
 * The screen is stateless and draws itself in [BorealisTheme] for [formFactor]:
 * - **Phone (touch):** a top bar with Add host and Settings, then one column of cards whose Open,
 *   Pair and Wake buttons are inline, so no long press is needed. A long press opens the options.
 * - **TV and handheld:** a [NavigationRail] with Hosts selected, Add host and Settings, then the
 *   cards in a grid. The first Continue card has focus when the screen opens, else the first host
 *   card. OK opens, hold OK, X or Menu opens the options.
 *
 * The options (Details, Wake, Forget) and the Forget confirmation belong to the screen: it keeps
 * which host they are open for and calls [onDetails], [onWake] or [onForget] when the user chooses.
 * The details are shown while [HostsUiState.details] is set. [snackbarHostState] shows the messages
 * the route turns the ViewModel's effects into.
 *
 * **Back.** The screen intercepts no Back, so Back still leaves the activity from the home screen:
 * a TV remote's Back must return to the launcher, and on a phone it is the system gesture. What it
 * does swallow on a TV or handheld is the gamepad's B button itself (`Key.ButtonB`), so a stray B
 * at the top level does nothing, as the proposal says. A host that maps B to Back before the key
 * reaches the app still leaves; T7 can then add a Back handler with a confirmation to the route.
 */
@Composable
fun HostsScreen(
    state: HostsUiState,
    formFactor: FormFactor,
    snackbarHostState: SnackbarHostState,
    onHostSelected: (String) -> Unit,
    onContinue: (String) -> Unit,
    onWake: (String) -> Unit,
    onForget: (String) -> Unit,
    onDetails: (String) -> Unit,
    onDetailsDismissed: () -> Unit,
    onAddHost: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BorealisTheme(formFactor) {
        var optionsHostId by rememberSaveable { mutableStateOf<String?>(null) }
        var forgetHostId by rememberSaveable { mutableStateOf<String?>(null) }
        val handlers =
            HostsHandlers(
                onHostSelected = onHostSelected,
                onContinue = onContinue,
                onWake = onWake,
                onOptions = { optionsHostId = it },
                onAddHost = onAddHost,
            )
        HostsLayout(
            state,
            formFactor,
            handlers,
            snackbarHostState,
            onSettings,
            modifier,
        )
        HostsDialogs(
            state = state,
            optionsHostId = optionsHostId,
            forgetHostId = forgetHostId,
            onOptionsHostChange = { optionsHostId = it },
            onForgetHostChange = { forgetHostId = it },
            onDetails = onDetails,
            onWake = onWake,
            onForget = onForget,
            onDetailsDismissed = onDetailsDismissed,
        )
    }
}

@Composable
private fun HostsLayout(
    state: HostsUiState,
    formFactor: FormFactor,
    handlers: HostsHandlers,
    snackbarHostState: SnackbarHostState,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(LocalBorealisColors.current.page)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // A gamepad's B is Back at the top level: it must not leave the home screen.
            .onKeyEvent { formFactor != FormFactor.Phone && it.key == Key.ButtonB }
    ) {
        if (formFactor == FormFactor.Phone) {
            Column(Modifier.fillMaxSize()) {
                PhoneTopBar(handlers.onAddHost, onSettings)
                HostsBody(state, formFactor, handlers, Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                HostsRail(handlers.onAddHost, onSettings)
                Column(Modifier.weight(1f)) {
                    TvHeader(state)
                    HostsBody(state, formFactor, handlers, Modifier.weight(1f))
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** What the cards and the empty state do, as one argument for the layouts. */
private class HostsHandlers(
    val onHostSelected: (String) -> Unit,
    val onContinue: (String) -> Unit,
    val onWake: (String) -> Unit,
    val onOptions: (String) -> Unit,
    val onAddHost: () -> Unit,
)

/**
 * The element that starts with focus on a TV or handheld, and the guard that lets the screen take
 * it once: a host that sorts first, a Continue card that appears or a card that scrolls back into
 * view later must not pull focus away from where the user moved it.
 */
private class StartFocus(val requester: FocusRequester, val state: InitialFocusState)

private fun Modifier.withStartFocus(start: StartFocus?): Modifier =
    if (start == null) this else initialFocus(start.requester, start.state)

@Composable
private fun HostsBody(
    state: HostsUiState,
    formFactor: FormFactor,
    handlers: HostsHandlers,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        // A phone leaves focus alone; a TV or handheld starts on the first card, or inside the
        // empty state (a focus group hands focus to its first child). The guard starts again when
        // the search ends, so the first hosts to arrive take the focus.
        val requester = remember { FocusRequester() }
        val initialFocus = rememberInitialFocusState(state.searching)
        val startFocus =
            if (formFactor != FormFactor.Phone) StartFocus(requester, initialFocus) else null
        if (state.searching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.hosts_empty_title),
                    body = stringResource(R.string.hosts_empty_body),
                    modifier = Modifier.withStartFocus(startFocus).focusGroup(),
                    action =
                        EmptyStateAction(
                            stringResource(R.string.hosts_add_host),
                            handlers.onAddHost,
                        ),
                )
            }
        } else {
            val columns = hostGridColumns(formFactor, maxWidth.value.toInt())
            HostGrid(state, columns, startFocus, handlers)
        }
    }
}

private fun LazyGridItemSpanScope.fullWidth() = GridItemSpan(maxLineSpan)

@Composable
private fun HostGrid(
    state: HostsUiState,
    columns: Int,
    startFocus: StartFocus?,
    handlers: HostsHandlers,
) {
    val optionsLabel = stringResource(R.string.hosts_options)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        // The padding and the gaps keep the focus outline, 5dp outside a card, from being clipped.
        contentPadding = PaddingValues(LocalBorealisSpacing.current.gutter),
        verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
    ) {
        if (state.continueItems.isNotEmpty()) {
            item(key = "continue-section", span = { fullWidth() }) {
                Kicker(stringResource(R.string.hosts_section_continue))
            }
            itemsIndexed(
                state.continueItems,
                key = { _, item -> "continue:${item.hostId}" },
                span = { _, _ -> fullWidth() },
            ) { index, item ->
                ContinueCard(
                    appName = item.appName,
                    detail = stringResource(R.string.hosts_continue_detail, item.hostName),
                    resumeLabel = stringResource(R.string.hosts_continue_resume),
                    onClick = { handlers.onContinue(item.hostId) },
                    modifier =
                        Modifier.fillMaxWidth()
                            .withStartFocus(startFocus.takeIf { index == 0 })
                            .testTag("continue:${item.hostId}"),
                )
            }
        }
        item(key = "hosts-section", span = { fullWidth() }) {
            Kicker(
                stringResource(R.string.hosts_section_hosts),
                Modifier.padding(top = SECTION_PADDING),
            )
        }
        itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
            val first = index == 0 && state.continueItems.isEmpty()
            HostItem(
                item,
                handlers,
                optionsLabel,
                Modifier.withStartFocus(startFocus.takeIf { first }),
            )
        }
    }
}

@Composable
private fun HostItem(
    item: HostItemUiState,
    handlers: HostsHandlers,
    optionsLabel: String,
    modifier: Modifier,
) {
    // Open and Pair both select the host: the ViewModel decides between apps and pairing.
    val action =
        item.action.labelRes()?.let { label ->
            HostCardAction(stringResource(label)) {
                if (item.action == HostAction.WAKE) {
                    handlers.onWake(item.id)
                } else {
                    handlers.onHostSelected(item.id)
                }
            }
        }
    HostCard(
        name = item.name,
        status = item.dotStatus(),
        statusText = stringResource(item.statusTextRes()),
        onClick = { handlers.onHostSelected(item.id) },
        modifier = modifier.testTag("host:${item.id}"),
        runningApp = item.runningAppName,
        action = action,
        onOptions = { handlers.onOptions(item.id) },
        optionsLabel = optionsLabel,
    )
}

/** An online host that is not paired needs the user, so its dot is the warning one. */
private fun HostItemUiState.dotStatus(): DotStatus =
    when (reachability) {
        Reachability.ONLINE -> if (paired) DotStatus.Online else DotStatus.Attention
        Reachability.OFFLINE -> DotStatus.Offline
        Reachability.UNKNOWN -> DotStatus.Unknown
    }

@StringRes
private fun HostItemUiState.statusTextRes(): Int =
    when (reachability) {
        Reachability.ONLINE ->
            if (paired) R.string.hosts_status_online else R.string.hosts_status_not_paired
        Reachability.OFFLINE -> R.string.hosts_status_offline
        Reachability.UNKNOWN -> R.string.hosts_status_checking
    }

@StringRes
private fun HostAction.labelRes(): Int? =
    when (this) {
        HostAction.OPEN -> R.string.hosts_action_open
        HostAction.PAIR -> R.string.hosts_action_pair
        HostAction.WAKE -> R.string.hosts_action_wake
        HostAction.NONE -> null
    }
